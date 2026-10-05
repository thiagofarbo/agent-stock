package com.agent.flight.graph.nodes;

import com.agent.flight.graph.StockGraphState;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.Map;

/**
 * ValidateQuestionNode
 * Recebe o estado, verifica a pergunta e devolve a atualização que o grafo deve aplicar.
 */

public class ValidateQuestionNode implements NodeAction<StockGraphState> {
    @Override
    public Map<String, Object> apply(StockGraphState state) throws Exception {

        final String question = state.question();

        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Pergunta não informada");
        }
        return Map.of(StockGraphState.QUESTION, question.strip());
    }
}
