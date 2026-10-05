package com.agent.flight.tools;

import com.agent.flight.utils.TokenUtils;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.jspecify.annotations.NonNull;
import com.agent.flight.service.TechnicalIndicatorsService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class StockTools {

    private final RestClient client;

    private final String apiToken;

    @Autowired
    public StockTools(@Value("${stockdata.api-token:}") String apiToken) {
        this(defaultClient(), apiToken);
    }

    StockTools(RestClient client, String apiToken) {
        this.client = client;
        this.apiToken = apiToken;
    }

    private static RestClient defaultClient() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        return RestClient.builder()
                .baseUrl("https://api.stockdata.org")
                .requestFactory(factory)
                .build();
    }

    @Tool("Consulta cotações de ações pelo StockData. Retorna JSON com ticker, preço, moeda, "
            + "variação percentual diária, capitalização (quando disponível) e horário da negociação. "
            + "A cobertura de ações é de bolsas dos EUA; não fornece histórico ou notícias.")
    public String search(@P("Símbolos separados por vírgula, por exemplo TSLA,MSFT") String symbols) {

        if (symbols == null || symbols.isBlank()) return "Erro: informe ao menos um símbolo de ação.";

        String[] tickers = getTokens(symbols);

        if (tickers == null) return "Erro: informe símbolos válidos separados por vírgula, como TSLA,MSFT.";
        if (apiToken == null || apiToken.isBlank()) {
            return "Erro: configure STOCKDATA_API_TOKEN para consultar o StockData.";
        }

        String normalized = normallizeTokens(tickers);
        return requestData("/v1/data/quote", normalized, Map.of());
    }

    private String requestData(String path, String normalized, Map<String, String> parameters) {
        try {
            String response = client.get()
                    .uri(builder -> {
                        builder.path(path).queryParam("symbols", "{symbols}")
                                .queryParam("api_token", "{token}");
                        parameters.forEach(builder::queryParam);
                        return builder.build(normalized, apiToken);
                    })
                    .retrieve()
                    .body(String.class);
            return response == null || response.isBlank()
                    ? "Erro: StockData retornou uma resposta vazia." : response;
        } catch (RestClientResponseException exception) {
            return switch (exception.getStatusCode().value()) {
                case 401, 403 -> "Erro: acesso ao StockData negado. Verifique o token e as permissões do plano.";
                case 429 -> "Erro: limite de consultas do StockData atingido. Tente novamente mais tarde.";
                default -> "Erro: StockData retornou HTTP " + exception.getStatusCode().value() + ".";
            };
        } catch (RestClientException exception) {
            return "Erro: não foi possível consultar o StockData. Tente novamente mais tarde.";
        }
    }

    private static @NonNull String normallizeTokens(String[] tickers) {
        return Arrays.stream(tickers).map(String::trim).distinct().collect(Collectors.joining(","));
    }

    private static String @Nullable [] getTokens(String symbols) {
        String[] tickers = symbols.toUpperCase(Locale.ROOT).split(",", -1);
        if (Arrays.stream(tickers).anyMatch(ticker -> !ticker.trim().matches("[A-Z0-9][A-Z0-9.:-]{0,29}"))) {
            return null;
        }
        return tickers;
    }

    @Tool("""
        Busca notícias recentes sobre ações.
        Use para consultar acontecimentos e notícias das empresas solicitadas.
    """)
    public String searchNews(
            @P("Símbolos separados por vírgula, como TSLA,MSFT") String symbols) throws Exception {

        if (symbols == null || symbols.isBlank()) return "Erro: informe ao menos um símbolo de ação.";

        String[] tickers = getTokens(symbols);

        if (tickers == null) return "Erro: informe símbolos válidos separados por vírgula, como TSLA,MSFT.";

        TokenUtils.validateToken(apiToken);

        String normalized = normallizeTokens(tickers);

        return requestData("/v1/news/all", normalized, Map.of("filter_entities", "true", "limit", "5"));
    }
    @Tool("Calcula SMA, EMA e RSI de Wilder a partir dos fechamentos diários do StockData. "
            + "Use para análise técnica; um símbolo por chamada. Períodos em pregões, não dias corridos. "
            + "Padrões: médias de 20 períodos, RSI de 14, janela de 365 dias até ontem (UTC).")
    public String technicalIndicators(
            @P("Um símbolo de ação, como TSLA") String symbol,
            @P(value = "Período para SMA e EMA, entre 2 e 250; padrão 20", required = false) Integer movingAveragePeriod,
            @P(value = "Período do RSI, entre 2 e 250; padrão 14", required = false) Integer rsiPeriod,
            @P(value = "Início do histórico YYYY-MM-DD; opcional", required = false) String dateFrom,
            @P(value = "Fim do histórico YYYY-MM-DD, até ontem UTC; opcional", required = false) String dateTo) throws Exception {
        if (symbol == null || symbol.isBlank() || symbol.contains(",") || getTokens(symbol) == null) {
            return "Erro: informe um único símbolo válido para os indicadores.";
        }
        int ma = movingAveragePeriod == null ? 20 : movingAveragePeriod;
        int rsi = rsiPeriod == null ? 14 : rsiPeriod;
        if (ma < 2 || ma > 250 || rsi < 2 || rsi > 250) {
            return "Erro: os períodos devem estar entre 2 e 250 pregões.";
        }
        LocalDate start;
        LocalDate end;
        try {
            LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
            end = dateTo == null || dateTo.isBlank() ? yesterday : LocalDate.parse(dateTo);
            start = dateFrom == null || dateFrom.isBlank() ? end.minusDays(365) : LocalDate.parse(dateFrom);
            if (start.isAfter(end) || end.isAfter(yesterday)) {
                return "Erro: use um intervalo válido, com fim até ontem (UTC).";
            }
        } catch (java.time.DateTimeException exception) {
            return "Erro: informe datas válidas no formato YYYY-MM-DD.";
        }

        TokenUtils.validateToken(apiToken);

        String normalized = normallizeTokens(getTokens(symbol));
        String history = requestData("/v1/data/eod", normalized,
                Map.of("date_from", start.toString(), "date_to", end.toString(), "interval", "day", "sort", "asc"));
        if (history.startsWith("Erro:")) return history;
        return TechnicalIndicatorsService.fromJson(normalized, history, ma, rsi, start, end);
    }

}
