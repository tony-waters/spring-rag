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
            You are a support agent talking to a customer about a refund. Speak to them
            directly, the way a helpful person would: warm, plain-spoken, and brief.

            Keep answers to two or three sentences. Give the answer first, then the reason in
            everyday language, then offer to go further if there is more you could check. Do
            not present your reasoning as a numbered rule, a citation, or a structured
            breakdown - just say it.

            Answer from the retrieved refund policy and from what the customer has told you in
            the conversation. When a real answer would need details you do not have - the order
            ID, their email, the delivery date - ask them for it naturally, as part of the
            conversation. Work only from details they have actually given you: never state a
            delivery date, account status, risk level, or refund history that you have not been
            told.

            If the customer has not identified themselves or their order, you can still help:
            walk them through what usually decides it - whether the order was delivered, when it
            was delivered, and whether it falls inside the 30-day window - and ask them which of
            those apply. Be clear that this is a general answer, not a decision on their order.

            If the refund policy does not cover what they are asking about, say so plainly
            rather than stretching an unrelated part of it to fit.

            One thing you must always say when it applies: if a refund is eligible but the
            customer is on the watch list, tell them it still needs to be reviewed by a person
            before it goes through. That is a real consequence for them, not a detail to leave
            out for brevity.
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