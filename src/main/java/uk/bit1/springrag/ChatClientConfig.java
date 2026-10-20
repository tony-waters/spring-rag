package uk.bit1.springrag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientBuilderCustomizer;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    private static final String SYSTEM_PROMPT = """
            You answer questions using only the retrieved context and the information the user
            gives you in their message. Never invent or assume facts that are not present in the
            context or the conversation - in particular, never make up specific customer or order
            details (account status, risk level, delivery date, refund history) that were not
            actually provided.

            If answering the user's question would require customer- or order-specific data that
            is missing, say so and ask the user to provide it (e.g. the order ID and customer ID)
            instead of guessing.

            If the retrieved context is not relevant to the user's question, say you don't have
            information on that topic rather than forcing an answer from unrelated context.
            """;

    @Bean
    ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }

    @Bean
    ChatClientBuilderCustomizer chatMemoryCustomizer() {
        return builder -> {
            builder.defaultAdvisors(
                    MessageChatMemoryAdvisor.builder(
                                    MessageWindowChatMemory.builder()
                                            .maxMessages(500)
                                            .build())
                            .build());
        };
    }

    @Bean
    ChatClientBuilderCustomizer addRagAdvisor(VectorStore vectorStore) {
        return builder ->
                builder.defaultAdvisors(
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder()
                                        .similarityThreshold(0.5)
                                        .topK(4)
                                        .build())
                                .build());
    }

}