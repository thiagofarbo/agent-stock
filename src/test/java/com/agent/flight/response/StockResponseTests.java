package com.agent.flight.response;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class StockResponseTests {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void readsStockDataResponseWithNullsAndApiFieldNames() {
        var response = mapper.readValue("""
                {
                  "meta": {"requested": 2, "returned": 2},
                  "data": [
                    {
                      "ticker": "TSLA", "name": "Tesla Inc",
                      "exchange_short": null, "exchange_long": null,
                      "mic_code": "IEXG", "currency": "USD", "price": 358.07,
                      "day_high": 365.09, "day_low": 355, "day_open": 357.54,
                      "52_week_high": null, "52_week_low": null, "market_cap": null,
                      "previous_close_price": 356.7,
                      "previous_close_price_time": "2026-09-15T15:59:59.000000",
                      "day_change": 0.38, "volume": 764436,
                      "is_extended_hours_price": false,
                      "last_trade_time": "2026-09-16T16:00:00.000000"
                    },
                    {
                      "ticker": "MSFT", "price": 490.46, "day_change": -1.32,
                      "52_week_high": 555.12, "52_week_low": 350.01,
                      "volume": 3000000000, "is_extended_hours_price": null,
                      "last_trade_time": "2026-09-16T15:59:56.123456"
                    }
                  ]
                }
                """, StockResponse.class);

        assertThat(response.getMeta().getRequested()).isEqualTo(2);
        assertThat(response.getMeta().getReturned()).isEqualTo(2);
        assertThat(response.getData()).hasSize(2);
        var tesla = response.getData().getFirst();
        assertThat(tesla.getTicker()).isEqualTo("TSLA");
        assertThat(tesla.getPrice()).isEqualByComparingTo("358.07");
        assertThat(tesla.getDayLow()).isEqualByComparingTo("355");
        assertThat(tesla.getExchangeShort()).isNull();
        assertThat(tesla.getMarketCap()).isNull();
        assertThat(tesla.getWeek52High()).isNull();
        assertThat(tesla.getMicCode()).isEqualTo("IEXG");
        assertThat(tesla.getExtendedHoursPrice()).isFalse();
        assertThat(tesla.getPreviousClosePriceTime()).isEqualTo(LocalDateTime.of(2026, 9, 15, 15, 59, 59));
        var microsoft = response.getData().get(1);
        assertThat(microsoft.getDayChange()).isEqualByComparingTo("-1.32");
        assertThat(microsoft.getWeek52High()).isEqualByComparingTo("555.12");
        assertThat(microsoft.getWeek52Low()).isEqualByComparingTo("350.01");
        assertThat(microsoft.getVolume()).isEqualTo(3000000000L);
        assertThat(microsoft.getExtendedHoursPrice()).isNull();
        assertThat(microsoft.getLastTradeTime().getNano()).isEqualTo(123456000);

        var json = mapper.readTree(mapper.writeValueAsString(response));
        assertThat(json.get("data").get(1).has("52_week_high")).isTrue();
        assertThat(json.get("data").get(0).has("is_extended_hours_price")).isTrue();
        assertThat(json.get("data").get(0).has("extendedHoursPrice")).isFalse();
    }

    @Test
    void acceptsEmptyData() {
        var response = mapper.readValue("""
                {"meta":{"requested":1,"returned":0},"data":[]}
                """, StockResponse.class);
        assertThat(response.getData()).isEmpty();
        assertThat(response.getMeta().getReturned()).isZero();
    }
}
