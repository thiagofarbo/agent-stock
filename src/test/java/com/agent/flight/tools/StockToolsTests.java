package com.agent.flight.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class StockToolsTests {
    private MockRestServiceServer server;
    private StockTools tools;
    private RestClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("https://api.stockdata.org");
        server = MockRestServiceServer.bindTo(builder).build();
        client = builder.build();
        tools = new StockTools(client, "test-token");
    }

    @Test
    void queriesNormalizedSymbolsAndPreservesQuoteData() {
        String response = """
                {"meta":{"requested":2,"returned":2},"data":[
                  {"ticker":"TSLA","price":267.46,"currency":"USD","market_cap":null},
                  {"ticker":"MSFT","price":330.1,"day_change":-1.2}]}
                """;
        server.expect(requestTo("https://api.stockdata.org/v1/data/quote?symbols=TSLA%2CMSFT&api_token=test-token"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertThat(tools.search(" tsla, msft,TSLA ")).isEqualTo(response);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "TSLA,", "TSLA&api_token=other", ",MSFT"})
    void rejectsInvalidSymbolsWithoutCallingApi(String symbols) {
        assertThat(tools.search(symbols)).startsWith("Erro:");
        server.verify();
    }

    @Test
    void handlesMissingTokenAndNullSymbolsWithoutCallingApi() {
        assertThat(new StockTools(client, "").search("TSLA")).contains("STOCKDATA_API_TOKEN");
        assertThat(tools.search(null)).startsWith("Erro:");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 500})
    void handlesHttpFailuresWithoutExposingToken(int status) {
        server.expect(requestTo("https://api.stockdata.org/v1/data/quote?symbols=TSLA&api_token=test-token"))
                .andRespond(withStatus(HttpStatus.valueOf(status)).body("test-token"));
        String result = tools.search("TSLA");
        assertThat(result).startsWith("Erro:").doesNotContain("test-token");
        if (status == 429) {
            assertThat(result).contains("limite");
        }
        server.verify();
    }

    @Test
    void handlesNetworkFailureWithoutExposingToken() {
        server.expect(requestTo("https://api.stockdata.org/v1/data/quote?symbols=TSLA&api_token=test-token"))
                .andRespond(withException(new IOException("URL contains test-token")));
        assertThat(tools.search("TSLA")).startsWith("Erro:").doesNotContain("test-token");
        server.verify();
    }

    @Test
    void preservesEmptyResults() {
        server.expect(requestTo("https://api.stockdata.org/v1/data/quote?symbols=TSLA&api_token=test-token"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));
        assertThat(tools.search("TSLA")).isEqualTo("{\"data\":[]}");
        server.verify();
    }

    @Test
    void handlesEmptyResponse() {
        server.expect(requestTo("https://api.stockdata.org/v1/data/quote?symbols=TSLA&api_token=test-token"))
                .andRespond(withNoContent());
        assertThat(tools.search("TSLA")).contains("resposta vazia");
        server.verify();
    }
}
