# spring-rag

A command-line RAG chat demo that answers customer refund questions from an ingested refund
policy. The language below distinguishes the kinds of text the app handles, since they look
alike in a repo full of Markdown but reach the model by different routes.

## Language

**Refund Policy**:
The domain facts about when an order qualifies for a refund. Lives in the ingested document,
reaches the model by retrieval, and is reasoned over rather than obeyed.
_Avoid_: Knowledge base, source document, context

**Answering Guidance**:
Instructions about how to respond to a customer — register, length, what to ask for, what to
always mention. Lives in the system prompt and applies to every turn.
_Avoid_: Tone instructions, prompt guidance

**Register**:
How much an answer reads like a person talking rather than a policy extract. Distinct from
turn-taking (whether the thread is held across turns) and shape (length and structure), which
have separate causes and separate fixes.
_Avoid_: Conversational, tone, voice

**Turn**:
One question from the customer and the answer that follows it. The unit the app works in: a
Turn is what Answering Guidance applies to, what Refund Policy is retrieved for, and what the
customer experiences as a wait. A Turn carries the thread of the ones before it, so "what about
mine?" is a Turn even though it means nothing alone.
_Avoid_: Request, exchange, message, round trip
