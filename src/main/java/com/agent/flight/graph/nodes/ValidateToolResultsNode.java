package com.agent.flight.graph.nodes;

import com.agent.flight.graph.StockGraphState;
import org.bsc.langgraph4j.action.NodeAction;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

public class ValidateToolResultsNode implements NodeAction<StockGraphState> {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Override
    public Map<String, Object> apply(StockGraphState state) {
        var executions = state.toolExecutions();

        if (executions.isEmpty()) {
            return rejected(
                    "NO_TOOL_EXECUTION",
                    "Nenhuma ferramenta foi executada para consultar os dados."
            );
        }

        for (var execution : executions) {
            String result = execution.result();

            if (result == null || result.isBlank()) {
                return rejected(
                        "EMPTY_TOOL_RESULT",
                        "Uma ferramenta retornou uma resposta vazia."
                );
            }

            result = result.strip();

            if (result.regionMatches(true, 0, "Erro:", 0, 5)) {
                return rejected(
                        "TOOL_REPORTED_ERROR",
                        "Uma ferramenta informou falha na consulta."
                );
            }

            JsonNode payload;

            try {
                payload = JSON.readTree(result);
            } catch (tools.jackson.core.JacksonException exception) {
                return rejected(
                        "INVALID_TOOL_JSON",
                        "Uma ferramenta retornou dados em formato inválido."
                );
            }

            if (payload == null
                    || !payload.isObject()
                    || payload.isEmpty()) {
                return rejected(
                        "EMPTY_OR_INVALID_PAYLOAD",
                        "Uma ferramenta retornou um conteúdo ausente ou inválido."
                );
            }

            if (payload.hasNonNull("error")
                    || payload.hasNonNull("errors")) {
                return rejected(
                        "API_REPORTED_ERROR",
                        "O serviço de dados informou um erro."
                );
            }

            if ("search".equals(execution.toolName())
                    || "searchNews".equals(execution.toolName())) {

                JsonNode data = payload.get("data");

                if (data == null || !data.isArray()) {
                    return rejected(
                            "INVALID_DATA",
                            "O serviço não retornou a lista de dados esperada."
                    );
                }

                if ("search".equals(execution.toolName())
                        && data.isEmpty()) {
                    return rejected(
                            "NO_QUOTES",
                            "Nenhuma cotação foi encontrada para a consulta."
                    );
                }
            }
        }

        return Map.of(
                StockGraphState.TOOL_RESULTS_VALID, true,
                StockGraphState.TOOL_VALIDATION_ERROR, ""
        );
    }

    private Map<String, Object> rejected(
            String reason,
            String message
    ) {
        return Map.of(
                StockGraphState.TOOL_RESULTS_VALID, false,
                StockGraphState.TOOL_VALIDATION_ERROR, reason,
                StockGraphState.RESPONSE, "Erro: " + message
        );
    }
}