package uk.bit1.springrag;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
public class RagIngestionConfig {

    private static final Logger logger = LoggerFactory.getLogger(RagIngestionConfig.class);

    @Value("${rag.document.url}")
    String documentUrl;

    /**
     * Ingestion gets its own trace rather than hanging off a Turn: this runner is
     * {@code @Order(0)}, so it has finished before the first Turn exists. The guard is inside
     * the observation deliberately - a run that skips ingestion then shows up in Tempo as a
     * near-empty chatloop.ingest trace, which is a more useful answer than no trace at all.
     */
    @Bean
    @Order(0)
    ApplicationRunner load(VectorStore vectorStore, ObservationRegistry observationRegistry) {
        return args -> {
            var ingest = Observation.createNotStarted("chatloop.ingest", observationRegistry)
                    .lowCardinalityKeyValue("ingest.document", documentUrl);
            ingest.observe(() -> ingest(vectorStore, ingest));
        };
    }

    private void ingest(VectorStore vectorStore, Observation ingest) {
        var existing = vectorStore.similaritySearch(
                SearchRequest.builder().query(documentUrl).topK(1).build());
        if (existing != null && !existing.isEmpty()) {
            // Bounded to two values, so it is safe as a tag on the chatloop.ingest timer and
            // answers "did this run actually ingest anything?" without opening the trace.
            ingest.lowCardinalityKeyValue("ingest.skipped", "true");
            logger.info("Vector store already has data; skipping ingestion of {}. " +
                    "Run 'docker compose rm -sf qdrant' to clear it and re-ingest. " +
                    "Avoid 'docker compose down -v': qdrant has no volume to delete, " +
                    "but that would wipe the cached Ollama models.", documentUrl);
            return;
        }

        ingest.lowCardinalityKeyValue("ingest.skipped", "false");

        logger.info("Loading document from {}.", documentUrl);

        var reader = new TikaDocumentReader(documentUrl);
        var splitter = TokenTextSplitter.builder().build();

        var chunks = splitter.apply(reader.get());
        ingest.highCardinalityKeyValue("ingest.chunks", String.valueOf(chunks.size()));

        vectorStore.accept(chunks);

        logger.info("Document loading complete.");
    }

}
