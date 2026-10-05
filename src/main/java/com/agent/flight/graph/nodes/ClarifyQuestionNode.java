package com.agent.flight.graph.nodes;

import com.agent.flight.graph.StockGraphState;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.Map;

/**
 * Esse nó não chama o modelo novamente.
 * Ele aproveita a pergunta de esclarecimento produzida pelo classificador e a coloca em RESPONSE
 */

public class ClarifyQuestionNode implements NodeAction<StockGraphState> {

    @Override
    public Map<String, Object> apply(StockGraphState state) {
        var plan = state.plan()
                .orElseThrow(() ->
                        new IllegalStateException("Plano não encontrado."));

        String question = plan.clarificationQuestion();

        if (question == null || question.isBlank()) {
            question = "Qual ação e quais informações você deseja consultar?";
        }

        return Map.of(StockGraphState.RESPONSE, question);
    }
}