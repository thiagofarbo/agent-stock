package com.agent.flight.graph.nodes;

import com.agent.flight.config.SemanticCache;
import com.agent.flight.graph.StockGraphState;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.Map;
import java.util.Objects;

public class LookupCacheNode implements NodeAction<StockGraphState> {

    private final SemanticCache semanticCache;

    public LookupCacheNode(SemanticCache semanticCache) {
        this.semanticCache = Objects.requireNonNull(semanticCache);
    }

    @Override
    public Map<String, Object> apply(StockGraphState state) {
        var cached = semanticCache.findFresh(state.question());

        if (cached.isPresent()) {
            return Map.of(
                    StockGraphState.CACHE_HIT, true,
                    StockGraphState.RESPONSE, cached.get()
            );
        }

        return Map.of(StockGraphState.CACHE_HIT, false);
    }
}