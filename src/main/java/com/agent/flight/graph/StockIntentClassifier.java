package com.agent.flight.graph;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface StockIntentClassifier {

    @SystemMessage("""
        Identifique as intenções e os símbolos das ações solicitadas.

        Intenções permitidas:
        - QUOTE: preço, cotação ou variação atual.
        - NEWS: notícias ou acontecimentos.
        - TECHNICAL_ANALYSIS: SMA, EMA, RSI ou análise técnica.
        - UNKNOWN: pedido fora dessas categorias.

        Uma pergunta pode conter várias intenções.

        Retorne os símbolos em letras maiúsculas, sem duplicatas.
        Se o usuário informar uma empresa, associe seu símbolo apenas
        quando a identificação for clara. Não invente símbolos.

        Se faltarem informações necessárias ou houver ambiguidade,
        marque needsClarification=true e escreva uma pergunta curta
        em clarificationQuestion.
        Caso contrário, use needsClarification=false e
        clarificationQuestion="".

        Sua função é classificar o pedido.
        Não consulte preços, não calcule indicadores e não responda
        à pesquisa financeira.
        """)
    @UserMessage("{{it}}")
    StockResearchPlan classify(String question);
}
