package uk.bit1.springrag;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;

import java.util.Scanner;
import java.util.UUID;

@SpringBootApplication
public class SpringRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringRagApplication.class, args);
    }

    @Bean
    @Order(1)
    ApplicationRunner go(ChatClient chatClient, ObservationRegistry observationRegistry) {
        return args -> {
            var conversationId = newConversationId();

            System.out.println("Session " + conversationId
                    + " - search Grafana for this id to find this session's traces.\n");
            System.out.println("How can I help?\n");

            try (Scanner scanner = new Scanner(System.in)) {
                while (true) {
                    System.out.print("> ");
                    if (!scanner.hasNextLine()) break; // to avoid infinite loops in tests
                    var input = scanner.nextLine();
                    if (input.isBlank()) continue; // allows user to hit return without error
                    System.out.println("\n" + answer(chatClient, observationRegistry, conversationId, input));
                }
            }
        };
    }

    /**
     * One Turn, observed: the span covers exactly the wait between the user pressing enter and
     * the answer appearing.
     * <p>
     * Opened as an Observation rather than as a span through {@code Tracer} because
     * {@code observe} puts it in scope for the duration of the call - so Spring AI's own
     * observations (the ChatClient call, each advisor, the embedding, the Qdrant query) become
     * children of it. Without a current observation they would each start their own root, and
     * one question would arrive in Tempo as several unrelated traces.
     */
    private static String answer(ChatClient chatClient, ObservationRegistry observationRegistry,
                                 String conversationId, String question) {
        var turn = Observation.createNotStarted("chatloop.turn", observationRegistry)
                // Low cardinality: this also becomes a tag on the chatloop.turn timer, which is
                // fine for an id with one value per run.
                .lowCardinalityKeyValue("conversation.id", conversationId)
                // High cardinality: span attributes only. As timer tags, these would add a new
                // time series for every question ever asked.
                .highCardinalityKeyValue("turn.question", question);

        return turn.observe(() -> {
            var answer = chatClient.prompt(question)
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content();
            turn.highCardinalityKeyValue("turn.answer", String.valueOf(answer));
            return answer;
        });
    }

    /**
     * Short enough to retype into Grafana's search box. Chat memory is in-process - there is no
     * ChatMemoryRepository on the classpath - so there is nothing for a stable id to reconnect
     * to across runs, and a fixed one would put the same conversation id on every span this
     * project ever emits.
     */
    private static String newConversationId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

}
