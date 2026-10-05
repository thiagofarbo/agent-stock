package com.agent.flight.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class StockServiceLangGraphTests {
    @Test
    void validatesQuestionAndReturnsClassifiedPlanAsJson() {
        var calls = new AtomicInteger();
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                calls.incrementAndGet();
                assertThat(request.messages()).anyMatch(message -> message instanceof UserMessage user
                        && user.singleText().startsWith("Qual o preço e as notícias da Tesla?"));
                return ChatResponse.builder().aiMessage(AiMessage.from("""
                        {"intents":["QUOTE","NEWS"],"symbols":["TSLA"],
                         "needsClarification":false,"clarificationQuestion":""}
                        """)).build();
            }
        };
        // Planejamento não precisa de Redis nem de ferramentas externas.
        var service = new StockService(model, null, null, Duration.ofSeconds(60));
        var result = JsonMapper.builder().build().readTree(
                service.searchWithLangGraph("  Qual o preço e as notícias da Tesla?  "));
        assertThat(result.get("symbols").get(0).asText()).isEqualTo("TSLA");
        assertThat(result.get("intents").size()).isEqualTo(2);
        assertThat(result.get("needsClarification").asBoolean()).isFalse();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void rejectsEmptyQuestionBeforeCallingModel() {
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                throw new AssertionError("Não deve chamar o modelo para uma pergunta vazia");
            }
        };
        var service = new StockService(model, null, null, Duration.ofSeconds(60));
        assertThatThrownBy(() -> service.searchWithLangGraph("   "))
                .hasStackTraceContaining("Pergunta não informada");
        assertThatIllegalArgumentException().isThrownBy(() -> service.searchWithLangGraph(null));
    }
}
