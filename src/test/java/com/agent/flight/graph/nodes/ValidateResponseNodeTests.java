package com.agent.flight.graph.nodes;

import com.agent.flight.graph.StockGraphState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ValidateResponseNodeTests {

    private final ValidateResponseNode node = new ValidateResponseNode();

    @Test
    void rejectsPricePlaceholder() {
        var state = new StockGraphState(Map.of(
                StockGraphState.RESPONSE,
                "Tesla (TSLA): $XXX,XX"
        ));

        var updates = node.apply(state);

        assertThat(updates.get(StockGraphState.RESPONSE_FORMAT_VALID))
                .isEqualTo(false);

        assertThat(updates.get(StockGraphState.VALIDATION_ERROR))
                .isEqualTo("PRICE_PLACEHOLDER");

        assertThat((String) updates.get(StockGraphState.RESPONSE))
                .doesNotContain("$XXX,XX");
    }

    @Test
    void rejectsMissingResponse() {
        var state = new StockGraphState(Map.of());

        var updates = node.apply(state);

        assertThat(updates.get(StockGraphState.VALIDATION_ERROR))
                .isEqualTo("EMPTY_RESPONSE");
    }

    @Test
    void acceptsTextWithoutKnownFormatProblems() {
        var state = new StockGraphState(Map.of(
                StockGraphState.RESPONSE,
                "TSLA: USD 123.45 — valor fictício deste teste."
        ));

        var updates = node.apply(state);

        assertThat(updates.get(StockGraphState.RESPONSE_FORMAT_VALID))
                .isEqualTo(true);
    }
}