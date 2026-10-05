package com.agent.flight.graph.nodes;

import com.agent.flight.graph.StockGraphState;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.Map;
import java.util.regex.Pattern;

public class ValidateResponseNode implements NodeAction<StockGraphState> {

    private static final Pattern PRICE_PLACEHOLDER = Pattern.compile(
            "(?i)(?:US\\$|R\\$|\\$|USD|BRL)\\s*X{2,}(?:[.,]X+)?"
                    + "|\\bX{2,}[.,]X{2,}\\b"
    );

    @Override
    public Map<String, Object> apply(StockGraphState state) {
        String response = state.response().orElse("").strip();

        if (response.isBlank()) {
            return rejected(
                    "EMPTY_RESPONSE",
                    "Erro: a pesquisa terminou sem uma resposta."
            );
        }

        if (PRICE_PLACEHOLDER.matcher(response).find()) {
            return rejected(
                    "PRICE_PLACEHOLDER",
                    "Erro: a resposta contém um preço não preenchido. "
                            + "Não foi possível concluir a pesquisa."
            );
        }

        if (response.regionMatches(true, 0, "Erro:", 0, 5)) {
            return rejected("REPORTED_ERROR", response);
        }

        return Map.of(
                StockGraphState.RESPONSE_FORMAT_VALID, true,
                StockGraphState.VALIDATION_ERROR, "",
                StockGraphState.RESPONSE, response
        );
    }

    private Map<String, Object> rejected(String reason, String message) {
        return Map.of(
                StockGraphState.RESPONSE_FORMAT_VALID, false,
                StockGraphState.VALIDATION_ERROR, reason,
                StockGraphState.RESPONSE, message
        );
    }
}