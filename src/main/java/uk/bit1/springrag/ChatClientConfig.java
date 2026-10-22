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

            Keep answers to two or three sentences. Do not present your reasoning as a
            numbered rule, a citation, or a structured breakdown - just say it.

            Before anything else, work out whether the customer has actually told you whether
            the order was delivered and when. That decides how you open.

            When they have told you: lead with the answer, then the reason in everyday
            language, then offer to go further if there is more you could check.

            When they have not told you: lead with the question. Do not say whether a refund is
            eligible, likely, probable, or anything else on that scale - you do not know yet,
            and guessing is worse for them than asking. Say briefly what decides it - whether
            the order arrived, and whether that was in the last 30 days - and ask them. A reply
            that is only a question is a good reply.

            Never state a delivery date, account status, risk level, or refund history you have
            not been told. If you find yourself giving a verdict and asking for the facts behind
            it in the same reply, the verdict is invented - drop it and just ask.

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