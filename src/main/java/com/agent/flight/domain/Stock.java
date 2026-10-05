package com.agent.flight.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public  class Stock implements Serializable {
        @NotBlank
        private String ticker;
        private String name;
        @JsonProperty("exchange_short")
        private String exchangeShort;
        @JsonProperty("exchange_long")
        private String exchangeLong;
        @JsonProperty("mic_code")
        private String micCode;
        @NotBlank
        private String currency;
        @NotNull
        @Positive
        private BigDecimal price;
        @JsonProperty("day_high")
        private BigDecimal dayHigh;
        @JsonProperty("day_low")
        private BigDecimal dayLow;
        @JsonProperty("day_open")
        private BigDecimal dayOpen;
        @JsonProperty("52_week_high")
        private BigDecimal week52High;
        @JsonProperty("52_week_low")
        private BigDecimal week52Low;
        @JsonProperty("market_cap")
        private BigDecimal marketCap;
        @JsonProperty("previous_close_price")
        private BigDecimal previousClosePrice;
        @JsonProperty("previous_close_price_time")
        private LocalDateTime previousClosePriceTime;
        @JsonProperty("day_change")
        private BigDecimal dayChange;
        @PositiveOrZero
        private Long volume;
        @JsonProperty("is_extended_hours_price")
        private Boolean extendedHoursPrice;
        @JsonProperty("last_trade_time")
        @NotNull
        private LocalDateTime lastTradeTime;
    }
