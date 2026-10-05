package com.agent.flight.domain;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface SearchFligths {

    @SystemMessage("""
    Voce e um analista de viagem pesquisando voos para um clientes utilizando a seguinte pesquisa
        {{search}}. Traga as pasagens , nao importa a empresa aerea, preco, horario, apenas traga as passagens.
        Inclua na pesquisa as datas, horarios, preco, numero do voo.
    """)
    @UserMessage("{{search}}")
    @Agent("Pesquisa voos de acordo com a pesquisa.")
    String search(@V("search") String search);

}