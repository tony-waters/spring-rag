# Tracing is always on, and the interesting content is in the logs, not the traces

Two decisions recorded together, because they are the two that look like carelessness without
the reasoning.

## Tempo and Grafana are unprofiled, so they always start

`tempo` and `grafana` sit in `compose.yaml` without a profile, so Spring Boot's docker-compose
management starts them alongside Qdrant and Ollama on every run - including during
`contextLoads()`, since `spring.docker.compose.skip.in-tests=false` is set deliberately. The
obvious alternative was a profile, like the `app` service has, making tracing opt-in.

The cost is real: two extra containers on every run and every test, for a demo that otherwise
needs two. It was accepted because this app exists to be watched while it thinks, and tracing
that requires remembering a flag is tracing nobody looks at. A test-only sampling override was
rejected for the same family of reasons - it is exactly the divergence that later hides a
broken exporter, in a test whose whole job is to prove the wiring holds.

No `metrics_generator`, no Prometheus `remote_write`, and so no service graph. That setup
earns its keep when there are several services to draw edges between; here there is one
application service, so it would be three moving parts for a two-node picture.

## The prompt and the retrieved chunks go to the console, not into Tempo

The three content flags in `application.properties`
(`spring.ai.chat.observations.log-prompt`, `.log-completion`,
`spring.ai.vectorstore.observations.log-query-response`) do not put content onto spans. In
Spring AI 2.0.1 the handlers behind them - `ChatModelPromptContentObservationHandler`,
`ChatModelCompletionObservationHandler`, `VectorStoreQueryResponseObservationHandler` - each
hold a commons-logging `Log` and emit at INFO. There is no tracing-aware logging handler in
this version; the only logging handler that also touches tracing is
`ErrorLoggingObservationHandler`.

So Tempo holds the shape of a Turn - where the seconds went, which advisors ran, what the
Qdrant query cost - and the console holds the text: the augmented prompt as sent to Ollama, the
completion, and the retrieved chunks. The two are joined by the `traceId` Spring Boot stamps
into every log line.

The one piece of content that does reach Tempo is the Turn's own question and answer, attached
in `SpringRagApplication` as high-cardinality key-values, because the loop already holds both
strings.

## Consequences

Reading a bad answer means two windows: Grafana for the timings, the terminal for the text.
That is the accepted cost, and the alternatives were both worse:

- **Promoting content onto spans with a custom `ObservationHandler`** depends on running before
  `DefaultTracingObservationHandler` - handler ordering that is not cleanly controllable. That
  is instrumentation which can stop working on a patch upgrade without failing.
- **Loki plus a collector**, with a `tracesToLogs` link from span to log line, is the correct
  answer and is deferred rather than rejected. The trigger for taking it: finding yourself
  scrolling console output to match a trace by eye. It is a smaller job than it looks, because
  `spring-boot-opentelemetry` can already export logs over OTLP
  (`management.otlp.logging.endpoint`) and Loki accepts OTLP natively - so it is a service and
  an endpoint, not a new logging pipeline.

The failure mode this record prevents is a future reader seeing prompts being logged in full
and "fixing" it by turning the flags off, or seeing two containers start during a unit test and
profiling them away - in both cases removing the only way to answer why the model said what it
said.
