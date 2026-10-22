# Answering behaviour belongs in the system prompt, never in an ingested document

Answering Guidance was originally written into `refunds.md`, at the end of the policy itself.
Because that document is chunked and retrieved by similarity, the guidance only reached the
model on turns where its chunk happened to win one of the four retrieval slots — so response
tone was intermittent rather than configured, and those chunks competed with actual policy
content on unrelated questions. All answering behaviour now lives in `SYSTEM_PROMPT`
(`ChatClientConfig`), which applies unconditionally to every turn; `refunds.md` holds Refund
Policy and nothing else.

## Consequences

The real cost is editability. Answering Guidance in Markdown can be changed by anyone who can
edit a document; in `SYSTEM_PROMPT` it requires a Java change and an image rebuild. That is a
genuine loss, and it is the strongest argument against this decision — it was accepted because
guidance that applies unpredictably is worse than guidance that is awkward to edit.

The failure mode this prevents is silent: re-adding a "how to answer" section to an ingested
document looks like a helpful contribution and produces no error, just tone that works
sometimes. Hence this record.
