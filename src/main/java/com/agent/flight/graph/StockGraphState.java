package com.agent.flight.graph;

import org.bsc.langgraph4j.state.AgentState;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * O estado guarda os dados de uma execução e os transporta entre as etapas do grafo. Vamos começar com a pergunta e a resposta.
   O que cada parte faz:
     AgentState: classe do LangGraph4j que armazena o estado.
     initialData: dados recebidos no começo da execução.
     question(): recupera a pergunta obrigatória.
     response(): retorna um Optional, pois a resposta ainda não existe no início.


 */
public class StockGraphState extends AgentState {

    public static final String PLAN = "plan";
    public static final String QUESTION = "question";
    public static final String RESPONSE = "response";
    public static final String CACHE_HIT = "cache_hit";
    public static final String TOOL_RESULTS_VALID = "tool_results_valid";
    public static final String TOOL_VALIDATION_ERROR = "tool_validation_error";

    public static final String RESPONSE_FORMAT_VALID = "response_format_valid";
    public static final String VALIDATION_ERROR = "validation_error";
    public static final String TOOL_EXECUTIONS = "tool_executions";

    public boolean toolResultsValid() {
        return this.<Boolean>value(TOOL_RESULTS_VALID).orElse(false);
    }

    public List<ToolExecutionEvidence> toolExecutions() {
        return this.<List<ToolExecutionEvidence>>value(TOOL_EXECUTIONS)
                .orElse(List.of());
    }

    public boolean responseFormatValid() {
        return this.<Boolean>value(RESPONSE_FORMAT_VALID).orElse(false);
    }

    /**
     * Constructs an AgentState with the given initial data.
     *
     * @param initialData the initial data for the agent state
     */
    public StockGraphState(Map<String, Object> initialData) {
        super(initialData);
    }

    public String question() {
        return this.<String>value(QUESTION)
                .orElseThrow(() ->
                        new IllegalStateException("Pergunta não informada"));
    }

    public Optional<String> response() {
        return this.<String>value(RESPONSE);
    }

    public Optional<StockResearchPlan> plan() {
        return this.<StockResearchPlan>value(PLAN);
    }

    public boolean cacheHit() {
        return this.<Boolean>value(CACHE_HIT).orElse(false);
    }
}
