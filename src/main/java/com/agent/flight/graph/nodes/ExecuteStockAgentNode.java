package com.agent.flight.graph.nodes;

import com.agent.flight.domain.StockSearch;
import com.agent.flight.graph.StockGraphState;
import com.agent.flight.graph.ToolExecutionEvidence;
import com.agent.flight.tools.StockTools;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ExecuteStockAgentNode implements NodeAction<StockGraphState> {

    private final ChatModel chatModel;
    private final StockTools stockTools;

    public ExecuteStockAgentNode(
            ChatModel chatModel,
            StockTools stockTools
    ) {
        this.chatModel = Objects.requireNonNull(chatModel);
        this.stockTools = Objects.requireNonNull(stockTools);
    }

    @Override
    public Map<String, Object> apply(StockGraphState state) {
        var plan = state.plan()
                .orElseThrow(() ->
                        new IllegalStateException("Plano não encontrado."));

        if (plan.needsClarification()) {
            throw new IllegalStateException(
                    "O pedido precisa de esclarecimento antes da execução."
            );
        }

        List<ToolExecutionEvidence> executions = new ArrayList<>();

        StockSearch agent = AiServices
                .builder(StockSearch.class)
                .chatModel(chatModel)
                .tools(stockTools)
                .afterToolExecution(execution ->
                        executions.add(new ToolExecutionEvidence(
                                execution.request().name(),
                                execution.request().arguments(),
                                execution.result()
                        ))
                )
                .build();

        String request = """
                Pergunta original:
                %s

                Plano identificado:
                Intenções: %s
                Símbolos: %s

                Use as ferramentas adequadas para atender ao pedido.
                Preserve as datas e os períodos da pergunta original.
                Informe eventuais falhas ou dados indisponíveis.
                """.formatted(
                state.question(),
                plan.intents(),
                plan.symbols()
        );

        String response = agent.search(request);

        return Map.of(
                StockGraphState.RESPONSE,
                response == null ? "" : response,

                StockGraphState.TOOL_EXECUTIONS,
                List.copyOf(executions)
        );
    }
}