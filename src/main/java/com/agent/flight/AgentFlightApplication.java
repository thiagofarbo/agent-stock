package com.agent.flight;

import com.agent.flight.service.FlightService;
import com.agent.flight.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Map;

@RequiredArgsConstructor
@SpringBootApplication
public class AgentFlightApplication {

	private final FlightService flightService;

	private final StockService stockService;

	public static void main(String[] args) {
		SpringApplication.run(AgentFlightApplication.class, args);
	}

	@Bean
	public CommandLineRunner testState() throws Exception {


		String response = stockService.searchWithLangGraph(
				"Calcule SMA e EMA de 2 pregões e RSI de 14 da BTC"
		);
		System.out.println(response);
        return null;
    }
}
