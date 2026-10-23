# Why a Turn takes two minutes, and what to do about it

Measured on 2026-10-23, from the traces added in #3 plus direct calls to Ollama's
`/api/generate`. Hardware: i7-1255U (2 P-cores + 8 E-cores, 12 threads, 15W, scaling at
60%), 30GB RAM, Intel UHD integrated graphics, Ubuntu 26.04, native Docker.

## The measurement that started it

One Turn, traced: `chatloop.turn` 104,538ms, of which `chat llama3.2:3b` was 104,391ms.
The span's own attributes gave the shape of the problem:

```
gen_ai.usage.input_tokens  = 2180
gen_ai.usage.output_tokens = 74
```

Throughput, measured directly against Ollama with a tiny prompt and a ~2000-token prompt:

```
prefill   ~28 tok/s   (stable across prompt sizes)
decode    ~4-8 tok/s
load       3.07s cold, 0.00s warm
```

So: 2180 / 28 = ~78s reading the prompt, 74 / 5 = ~15s writing the answer. That accounts
for the 104s almost exactly.

**Three quarters of the wait is the model reading, not writing.** Every instinct about
"make the answer shorter" or "cap num_predict" is aimed at the 15s, not the 78s.

## It is not Docker

Worth recording, because it is the obvious first guess and it is wrong:

```
cpu.max    = max 100000     # no CPU limit on the container
memory.max = max            # no memory limit
nproc      = 12             # sees every thread
```

This is native Docker on Linux, not Docker Desktop, so there is no VM and no CPU/RAM
slider to raise. The container already has the whole machine. Nothing to tune here.

## Cause 1: Ollama was using two of twelve threads

Prefill and decode rates against `num_thread`, same ~1000-token prompt each time:

| num_thread | prefill tok/s | decode tok/s |
|---|---|---|
| default | 28.7 | ~5 |
| 2 | 28.7 | 6.2 |
| 4 | 33.8 | 6.4 |
| 6 | 37.7 | 7.2 |
| **8** | **45.8** | **7.3** |
| 12 | 42.5 | 3.4 |

The default is indistinguishable from `num_thread=2`, which is almost certainly what it
picks: on a hybrid Intel chip, llama.cpp's core detection counts the two P-cores and
ignores the eight E-cores. A 3B model was running on two cores.

12 is *worse* than 8 — decode collapses to 3.4 tok/s, the two hyperthreads per core
fighting each other.

## Cause 2: every Turn ships the entire Refund Policy

`refunds.md` is ~1700 tokens. `TokenTextSplitter`'s default `DEFAULT_CHUNK_SIZE` is 800
tokens, which is why ingestion recorded `ingest.chunks=3`. The retrieval is configured
`topK=4` against a store holding 3 chunks, so **every Turn retrieves 100% of the corpus**.

2180 input tokens breaks down as roughly:

```
~1700  the whole of refunds.md, all 3 chunks
 ~403  SYSTEM_PROMPT (299 words of Answering Guidance)
  ~12  the question
   ~0  chat memory, on a first Turn
```

At this corpus size the similarity search is decorative: it cannot filter anything,
because there is nothing to filter out. The retrieval hop that Fig. 2 of the system map
calls "the whole point of the design" is, at present, an expensive no-op.

## The changes

1. `spring.ai.ollama.chat.options.num-thread=8`. Free, no quality cost.
   Predicted: prefill 78s -> 48s, decode 15s -> 10s, so ~104s -> ~58s.

2. `TokenTextSplitter.builder().withChunkSize(200)`. ~9 chunks instead of 3, so `topK=4`
   retrieves ~800 tokens instead of ~1700.
   Predicted: a further ~20s off, and retrieval becomes genuinely selective — which is
   what #1 (memoryless retrieval) is about.

Together, predicted ~104s -> ~40s with no change of model.

Requires `docker compose rm -sf qdrant` to re-ingest, since chunk size only takes effect
at ingestion.

## Considered and rejected for now

- **`keep-alive=-1`**: saves the 3.07s cold load, once per run. Marginal.
- **Capping `num-predict`**: aims at the 15s, not the 78s.
- **`llama3.2:1b`**: roughly triples throughput, but the accuracy cost of going small is
  already documented in `application.properties`. Exhaust the free levers first.
- **Shortening `SYSTEM_PROMPT`**: ~403 tokens, so ~9s at the improved prefill rate. It is
  load-bearing per ADR 0001; not worth trading answer quality for 9s yet.
- **Using the Intel iGPU**: the real ceiling here is a 15W laptop CPU. The official Ollama
  image supports neither CUDA nor ROCm, so the iGPU is unreachable without a different
  image (`ipex-llm`, or a Vulkan build) plus mapping `/dev/dri`. A bigger change than
  anything above, and plausibly worth its own write-up.

## Measured result

Both changes applied, Qdrant cleared, same question asked again:

| | Before | After |
|---|---|---|
| `chatloop.turn` | 104,538ms | **35,012ms** |
| `chat llama3.2:3b` | 104,391ms | 34,900ms |
| `gen_ai.usage.input_tokens` | 2,180 | **1,227** |
| `gen_ai.usage.output_tokens` | 74 | 38 |
| `ingest.chunks` | 3 | **9** |
| `qdrant query` | 78ms | 62ms |

**3.0x faster**, against a predicted ~2.6x. Input tokens fell 44%, and retrieval now draws 4
chunks from 9 rather than being handed all 3 that existed.

One honest caveat on that multiple: the answer was also shorter this time (38 output tokens
against 74), and answer length is not something these changes control. Normalising to the
original 74-token answer gives roughly 40s, so the attributable speedup is about **2.6x** - which
is exactly what was predicted. The headline 3.0x includes luck.

Retrieval remains a rounding error either way: 62ms of a 35s Turn. The prompt the model has to
read is still the whole cost, and `SYSTEM_PROMPT` is now a larger share of it.
