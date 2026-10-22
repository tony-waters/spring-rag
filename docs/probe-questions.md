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
docker compose build app                # SYSTEM_PROMPT is baked into the image
docker compose rm -sf qdrant            # clears ingested chunks, so ingestion re-runs
docker compose up -d qdrant ollama
docker compose run --rm app
```

`rm -sf qdrant` is only needed when the ingested document changed; `build app` is needed for
any source or property change.

**Do not use `docker compose down -v` for this.** Qdrant has no named volume (see
`compose.yaml`) — its data lives in the container layer, so recreating the container is what
clears it. What `-v` *does* remove is `ollama-data`, the model cache, forcing a fresh ~4.9GB
pull of `llama3.1:8b`. That is the pull the timeout settings in `application.properties` exist
to survive; there is no reason to trigger it to re-ingest a Markdown file.

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
| 5 | `Thanks — what about the one before that?` (immediately after probe 4) | Asks which order it is rather than guessing. Retrieval embeds the raw turn, so the chunks behind this answer are poor (see the query-rewriting issue) — a vague but honest answer is the pass; inventing an order number is the fail. |

## Reading the results

Probes 1, 2, 3 and 5 are correctness: every one of them is a case where the model has no
grounds for a verdict, and giving one anyway is the failure. If any fails, the prompt has
traded safety for tone and needs narrowing.

Probe 4 is the only probe where a verdict is correct, and it checks that the watch-list review
requirement survives the instruction to be brief.

All five passed on `llama3.1:8b` across two runs. They have never all passed on
`llama3.2:3b`: probe 1 failed there under two different prompts, inventing a verdict and
contradicting itself inside one sentence, which is what justified the larger model. If you
switch back to a 3B-class model, expect probe 1 to fail again and do not read it as a prompt
regression.

Budget time: on CPU the 8B takes a few minutes per answer, so a full run is 15-20 minutes.

The two 8B runs differed enough to be worth knowing about, since a single run will mislead
you. In the second, three consecutive answers asked "whether the order was delivered and
when" in near-identical words — the "lead with the question" branch became the only thing the
model said. The first run varied its phrasing. So monotony is a live risk that the pass
criteria above do not catch, and "register is better on the 8B" rests on fewer samples than
the correctness result does.

Probe 5 also passed more weakly the second time: it asked about the order already under
discussion rather than the previous one, and asked for a delivery date it had been given. It
did not invent an order number, which is all the stated criterion checks — the criterion is
looser than it should be.

## Not covered by these probes

The 8B offers to "check on the status" of an order — a capability the app does not have today.
That is correct behaviour arriving early rather than a defect, since MCP access to customers
and orders is the next iteration, so nothing here tests for it. When that lands, several of
these probes change meaning: probe 2 in particular inverts, because a model that can resolve a
customer and an order *should* return a verdict, and refusing becomes the failure. Rebuild the
trap around an identifier MCP returns nothing for.
