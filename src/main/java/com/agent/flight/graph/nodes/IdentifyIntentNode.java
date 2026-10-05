package com.agent.flight.graph.nodes;

import com.agent.flight.graph.StockGraphState;
import com.agent.flight.graph.StockIntentClassifier;
import com.agent.flight.graph.StockResearchPlan;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.Map;
import java.util.Objects;

public class IdentifyIntentNode implements NodeAction<StockGraphState> {

    private final StockIntentClassifier classifier;

    public IdentifyIntentNode(StockIntentClassifier classifier) {
        this.classifier = Objects.requireNonNull(classifier);
    }

    @Override
    public Map<String, Object> apply(StockGraphState state) {

        StockResearchPlan plan = classifier.classify(state.question());

        if (plan == null
                || plan.intents() == null
                || plan.intents().isEmpty()
                || plan.symbols() == null) {
            throw new IllegalStateException(
                    "O classificador retornou um plano incompleto."
            );
        }
        return Map.of(StockGraphState.PLAN, plan);
    }
}
