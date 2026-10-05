package com.agent.flight.service;

import com.agent.flight.config.SemanticCache;
import com.agent.flight.domain.StockSearch;
import com.agent.flight.graph.StockGraphState;
import com.agent.flight.graph.StockIntentClassifier;
import com.agent.flight.graph.nodes.*;
import com.agent.flight.tools.StockTools;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;
import java.util.Map;

import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import org.springframework.stereotype.Service;

import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Service
public class StockService {

    private final ChatModel chatModel;
    private final StockTools stockTools;
    private final SemanticCache semanticCache;
    private final Duration cacheTtl;

    public StockService(ChatModel chatModel, StockTools stockTools, @Lazy SemanticCache semanticCache,
                        @Value("${stock.cache.ttl:60s}") Duration cacheTtl) {
        this.chatModel = chatModel;
        this.stockTools = stockTools;
        this.semanticCache = semanticCache;
        this.cacheTtl = cacheTtl;
    }

    public String search(final String search) {
        return semanticCache.getOrCompute(search, cacheTtl, () -> searchWithAgent(search));
    }

    private String searchWithAgent(final String search) {
        StockSearch agent = AgenticServices
                .agentBuilder(StockSearch.class)
                .chatModel(chatModel)
                .tools(stockTools)
                .outputKey("search")
                .build();

        return agent.search(search);
    }

    /**
     *
     * Valida e classifica o pedido, consulta o cache e,
     * quando necessário, executa o agente com ferramentas.
     *
     * @param search
     * @return
     */
    public String searchWithLangGraph(final String search) {
        if (search == null) {
            throw new IllegalArgumentException("Pergunta não informada");
        }

        var lookupCacheNode = new LookupCacheNode(semanticCache);

        StockIntentClassifier classifier = AiServices
                .builder(StockIntentClassifier.class)
                .chatModel(chatModel)
                .build();

        var validateQuestionNode = new ValidateQuestionNode();
        var identifyIntentNode = new IdentifyIntentNode(classifier);
        var clarifyQuestionNode = new ClarifyQuestionNode();
        var validateResponseNode = new ValidateResponseNode();

        var validateToolResultsNode = new ValidateToolResultsNode();

        var executeStockAgentNode = new ExecuteStockAgentNode(chatModel, stockTools);

        try {
            var graph = new StateGraph<>(StockGraphState::new)
                    .addNode(
                            "validate_question",
                            node_async(validateQuestionNode)
                    )
                    .addNode(
                            "identify_intent",
                            node_async(identifyIntentNode)
                    )
                    .addNode(
                            "clarify_question",
                            node_async(clarifyQuestionNode)
                    )
                    .addNode(
                            "lookup_cache",
                            node_async(lookupCacheNode)
                    )
                    .addNode(
                            "validate_response",
                            node_async(validateResponseNode)
                    )
                    .addNode(
                            "execute_agent",
                            node_async(executeStockAgentNode)
                    )
                    .addNode(
                            "validate_tool_results",
                            node_async(validateToolResultsNode)
                    )
                    .addEdge(START, "validate_question")
                    .addEdge("validate_question", "identify_intent")

                    .addConditionalEdges(
                            "identify_intent",
                            edge_async(state -> {
                                var plan = state.plan()
                                        .orElseThrow(() ->
                                                new IllegalStateException(
                                                        "Plano não encontrado."
                                                ));

                                return plan.needsClarification()
                                        ? "needs_clarification"
                                        : "ready";
                            }),
                            Map.of(
                                    "needs_clarification", "clarify_question",
                                    "ready", "lookup_cache"
                            )
                    )
                    .addEdge("clarify_question", END)
                    .addConditionalEdges(
                            "lookup_cache",
                            edge_async(state -> state.cacheHit() ? "hit" : "miss"),
                            Map.of(
                                    "hit", "validate_response",
                                    "miss", "execute_agent"
                            )
                    )
                    .addEdge("execute_agent", "validate_tool_results")
                    .addConditionalEdges(
                            "validate_tool_results",
                            edge_async(state ->
                                    state.toolResultsValid() ? "valid" : "invalid"
                            ),
                            Map.of(
                                    "valid", "validate_response",
                                    "invalid", END
                            )
                    )
                    .addEdge("validate_response", END)
                    .compile();

            var finalState = graph.invoke(
                    GraphInput.args(
                            Map.of(StockGraphState.QUESTION, search)
                    ),
                    RunnableConfig.builder().build()
            ).orElseThrow(() ->
                    new IllegalStateException(
                            "O grafo não retornou um estado final."
                    ));

            var executions = finalState.toolExecutions();

            return finalState.response()
                    .orElseThrow(() ->
                            new IllegalStateException(
                                    "O grafo terminou sem produzir uma resposta."
                            ));

        } catch (GraphStateException exception) {
            throw new IllegalStateException(
                    "Não foi possível montar o grafo de pesquisa.",
                    exception
            );
        }
    }
}
