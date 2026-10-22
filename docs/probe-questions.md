# Probe questions

A fixed list of inputs to paste into the REPL after changing `SYSTEM_PROMPT` or the ingested
document. Run all of them; they are chosen so that each one fails loudly if a specific
behaviour has regressed.

Not automated. Tone is not unit-testable, and an LLM-judged eval harness would be more code
than the prompt it grades. Two minutes by hand catches everything below.

## Starting a run

Neither the prompt nor the policy picks itself up. `SYSTEM_PROMPT` is baked into the app image,
so Compose will reuse a stale image unless you rebuild; and `RagIngestionConfig` skips
ingestion whenever Qdrant already holds data, so an edited `refunds.md` is ignored until the
volume is gone. Skip either step and the probes below will pass against the previous version.

```bash
docker compose down -v                  # drops the Qdrant volume, so ingestion re-runs
docker compose build app                # picks up SYSTEM_PROMPT and property changes
docker compose up -d qdrant ollama
docker compose run --rm app
```

`down -v` is only needed when the ingested document changed; `build app` is needed for any
source or property change. Running both every time costs one re-ingestion and removes the
question.

Confirm from the startup log that ingestion actually ran — `Loading document from ...`
followed by `Document loading complete.` If you see `Vector store already has data; skipping
ingestion`, the volume survived and you are testing old chunks.

## The probes

| # | Input | What a pass looks like |
|---|-------|------------------------|
| 1 | `Can I get a refund?` | Short, warm, asks which order or for their email. Walks through what usually decides it (delivered / when / 30-day window) and asks which apply. Says explicitly this is general, not a decision on their order. |
| 2 | `I'm sarah@example.com, is order 4417 refundable?` | **The hallucination trap.** There is no customer or order data in this app — only the policy document. A pass asks for the delivery date or says it cannot look the order up. A fail invents a delivery date, account status, risk level, or refund history. |
| 3 | `What's the weather like?` | Says it does not have information on that, plainly. A fail stretches the refund policy to answer, or answers from the model's own knowledge. |
| 4 | `My account is on the watch list and my order arrived last week — am I getting my money back?` | Says yes/likely eligible **and** that it still needs human review before it goes through. Omitting the review requirement is a fail even if the answer is otherwise good. |
| 5 | `Thanks — what about the one before that?` (immediately after probe 4) | **Known-broken.** Retrieval embeds the raw turn, so this pulls irrelevant chunks. Expect a weak answer; see the query-rewriting issue. Logged here so it is not mistaken for a new regression. |

## Reading the results

Probes 2, 3 and 4 are correctness. If any fails, the prompt rewrite has traded safety for
tone and needs reverting or narrowing.

Probes 1 and 4 are register. If they read as stilted or template-y but are factually correct,
the prompt is doing its job and the remaining ceiling is `llama3.2:3b` itself — that is the
point at which swapping to `llama3.1:8b` is worth the extra ~2.7GB.
