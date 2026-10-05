package com.agent.flight.graph;

import java.util.List;
import java.io.Serializable;

/**
 * Esse objeto representará a interpretação da pergunta.
 * Os campos de esclarecimento permitem lidar com pedidos como “analise aquela empresa”, sem inventar um ativo.
 *
 * @param intents
 * @param symbols
 * @param needsClarification
 * @param clarificationQuestion
 */

public record StockResearchPlan (List<StockIntent> intents,
                                 List<String> symbols,
                                 boolean needsClarification,
                                 String clarificationQuestion) implements Serializable {
}
