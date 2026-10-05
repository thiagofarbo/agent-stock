package com.agent.flight.utils;

public class TokenUtils {

    public static void validateToken(final String apiToken) throws Exception {

        if (apiToken == null || apiToken.isBlank()) {
           throw  new Exception("Erro: configure STOCKDATA_API_TOKEN para consultar o StockData.");
        }
    }
}
