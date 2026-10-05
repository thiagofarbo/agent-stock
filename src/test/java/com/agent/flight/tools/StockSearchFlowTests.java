package com.agent.flight.tools;

import com.agent.flight.config.SemanticCache;
import com.agent.flight.controller.StockController;
import com.agent.flight.service.StockService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StockSearchFlowTests {
    @Test
    void stockEndpointExecutesQuoteToolOnMissAndReusesCachedAnswerOnHit() throws Exception {
        String query = "Consulte o preco das acoes da TSLA e MSFT";
        String apiResponse = """
                {"meta":{"requested":2,"returned":2},"data":[
                  {"ticker":"TSLA","price":358.07,"currency":"USD"},
                  {"ticker":"MSFT","price":490.46,"currency":"USD"}]}
                """;
        var builder = RestClient.builder().baseUrl("https://api.stockdata.org");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.stockdata.org/v1/data/quote?symbols=TSLA%2CMSFT&api_token=test-token"))
                .andRespond(withSuccess(apiResponse, MediaType.APPLICATION_JSON));
        var tools = new StockTools(builder.build(), "test-token");
        var calls = new AtomicInteger();
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                if (calls.getAndIncrement() == 0) {
                    assertThat(request.messages()).anyMatch(message ->
                            message instanceof UserMessage user && user.singleText().equals(query));
                    assertThat(request.toolSpecifications()).anyMatch(tool -> tool.name().equals("search"));
                    return ChatResponse.builder().aiMessage(AiMessage.from(
                            ToolExecutionRequest.builder().id("quote-call").name("search")
                                    .arguments("{\"symbols\":\"TSLA,MSFT\"}").build())).build();
                }
                var results = request.messages().stream()
                        .filter(ToolExecutionResultMessage.class::isInstance)
                        .map(ToolExecutionResultMessage.class::cast).toList();
                assertThat(results).hasSize(1);
                assertThat(results.getFirst().text()).isEqualTo(apiResponse);
                // Echo the real tool result, proving that it reached the model.
                return ChatResponse.builder().aiMessage(AiMessage.from(results.getFirst().text())).build();
            }
        };
        EmbeddingModel embeddingModel = new EmbeddingModel() {
            @Override
            public Response<Embedding> embed(String text) {
                assertThat(text).isEqualTo(query);
                return Response.from(Embedding.from(new float[]{1, 0, 0}));
            }
        };
        var cache = new SemanticCache(embeddingModel, new InMemoryEmbeddingStore<>(), 0.9);
        var mvc = MockMvcBuilders.standaloneSetup(new StockController(new StockService(model, tools, cache, java.time.Duration.ofSeconds(60)))).build();

        mvc.perform(get("/api/stocks/search").param("search", query))
                .andExpect(status().isOk())
                .andExpect(content().string(apiResponse));
        mvc.perform(get("/api/stocks/search").param("search", query))
                .andExpect(status().isOk())
                .andExpect(content().string(apiResponse));
        assertThat(calls.get()).isEqualTo(2);
        server.verify();
    }
}
