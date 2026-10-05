package com.agent.flight.service;

import com.agent.flight.domain.SearchFligths;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FlightService {


    private final ChatModel chatModel;

    public String search(final String search){

        SearchFligths agent = AgenticServices
                .agentBuilder(SearchFligths.class)
                .chatModel(chatModel)
//                .tools(flightTools)
                .outputKey("search")
                .build();

        return agent.search(search);
    }
}
