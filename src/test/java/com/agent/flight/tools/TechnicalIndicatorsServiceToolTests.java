package com.agent.flight.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TechnicalIndicatorsServiceToolTests {
    private MockRestServiceServer server;
    private StockTools tools;
    static final String HISTORY = """
            {"data":[
              {"date":"2024-01-05T00:00:00.000Z","close":13},
              {"date":"2024-01-04T00:00:00.000Z","close":14},
              {"date":"2024-01-03T00:00:00.000Z","close":11},
              {"date":"2024-01-02T00:00:00.000Z","close":12},
              {"date":"2024-01-01T00:00:00.000Z","close":10}]}
            """;

    @BeforeEach
    void setup() {
        var builder = RestClient.builder().baseUrl("https://api.stockdata.org");
        server = MockRestServiceServer.bindTo(builder).build();
        tools = new StockTools(builder.build(), "test-token");
    }

    @Test
    void requestsDailyHistoryAndCalculatesIndicators() throws Exception {
        server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/v1/data/eod"))
                .andExpect(queryParam("symbols", "TSLA"))
                .andExpect(queryParam("api_token", "test-token"))
                .andExpect(queryParam("interval", "day"))
                .andExpect(queryParam("sort", "asc"))
                .andExpect(queryParam("date_from", "2024-01-01"))
                .andExpect(queryParam("date_to", "2024-01-10"))
                .andRespond(withSuccess(HISTORY, MediaType.APPLICATION_JSON));
        var json = JsonMapper.builder().build().readTree(call());
        assertThat(json.get("sma").decimalValue()).isEqualByComparingTo("12.6667");
        assertThat(json.get("rsi").decimalValue()).isEqualByComparingTo("66.6667");
        assertThat(json.get("asOf").asText()).isEqualTo("2024-01-05");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"data\":[]}", "invalid", "{\"data\":[{\"date\":\"2024-01-01\",\"close\":null}]}"})
    void handlesEmptyOrInvalidHistory(String body) throws Exception {
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThat(call()).startsWith("Erro:");
        server.verify();
    }

    @Test
    void rejectsInvalidArgumentsBeforeCallingApi() throws Exception {
        assertThat(tools.technicalIndicators("TSLA,MSFT", 3, 3, null, null)).startsWith("Erro:");
        assertThat(tools.technicalIndicators("TSLA", 0, 3, null, null)).startsWith("Erro:");
        assertThat(tools.technicalIndicators("TSLA", 3, 3, "2024-02-30", null)).startsWith("Erro:");
        assertThat(tools.technicalIndicators("TSLA", 3, 3, "2024-02-01", "2024-01-01")).startsWith("Erro:");
        assertThat(tools.technicalIndicators("TSLA", 3, 3, null, "9999-01-01")).startsWith("Erro:");
        server.verify();
    }

    @Test
    void handlesApiErrorsWithoutExposingCredentials() throws Exception {
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("test-token"));
        assertThat(call()).contains("limite").doesNotContain("test-token");
        server.verify();
    }

    @Test
    void completesExistingNewsTool() throws Exception {
        server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/v1/news/all"))
                .andExpect(queryParam("filter_entities", "true"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));
        assertThat(tools.searchNews("TSLA")).isEqualTo("{\"data\":[]}");
        server.verify();
    }

    private String call() throws Exception {
        return tools.technicalIndicators(" tsla ", 3, 3, "2024-01-01", "2024-01-10");
    }
}
