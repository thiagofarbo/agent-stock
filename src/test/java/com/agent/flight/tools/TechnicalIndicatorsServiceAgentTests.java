package com.agent.flight.tools;

import com.agent.flight.config.SemanticCache;
import com.agent.flight.controller.StockController;
import com.agent.flight.service.StockService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TechnicalIndicatorsServiceAgentTests {
    @Test
    void agentInvokesTechnicalToolAndReceivesCalculatedValues() throws Exception {
        var builder = RestClient.builder().baseUrl("https://api.stockdata.org");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/v1/data/eod"))
                .andExpect(queryParam("symbols", "TSLA"))
                .andRespond(withSuccess(TechnicalIndicatorsServiceToolTests.HISTORY, MediaType.APPLICATION_JSON));
        var calls = new AtomicInteger();
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                if (calls.getAndIncrement() == 0) {
                    assertThat(request.toolSpecifications()).anyMatch(tool -> tool.name().equals("technicalIndicators"));
                    return ChatResponse.builder().aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                            .id("indicators").name("technicalIndicators").arguments("""
                                    {"symbol":"TSLA","movingAveragePeriod":3,"rsiPeriod":3,
                                    "dateFrom":"2024-01-01","dateTo":"2024-01-10"}
                                    """).build())).build();
                }
                var result = request.messages().stream().filter(ToolExecutionResultMessage.class::isInstance)
                        .map(ToolExecutionResultMessage.class::cast).findFirst().orElseThrow();
                var data = JsonMapper.builder().build().readTree(result.text());
                assertThat(data.get("sma").decimalValue()).isEqualByComparingTo("12.6667");
                assertThat(data.get("ema").decimalValue()).isEqualByComparingTo("12.7500");
                assertThat(data.get("rsi").decimalValue()).isEqualByComparingTo("66.6667");
                return ChatResponse.builder().aiMessage(AiMessage.from("Indicadores recebidos da ferramenta.")).build();
            }
        };
        EmbeddingModel embeddings = new EmbeddingModel() {
            @Override
            public Response<Embedding> embed(String text) {
                return Response.from(Embedding.from(new float[]{1, 0, 0}));
            }
        };
        var cache = new SemanticCache(embeddings, new InMemoryEmbeddingStore<>(), 0.9);
        var service = new StockService(model, new StockTools(builder.build(), "test-token"), cache, Duration.ofSeconds(60));
        var mvc = MockMvcBuilders.standaloneSetup(new StockController(service)).build();
        mvc.perform(get("/api/stocks/search").param("search", "Calcule indicadores da TSLA no início de janeiro de 2024"))
                .andExpect(status().isOk()).andExpect(content().string("Indicadores recebidos da ferramenta."));
        assertThat(calls.get()).isEqualTo(2);
        server.verify();
    }
}
