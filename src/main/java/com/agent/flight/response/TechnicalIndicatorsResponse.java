package com.agent.flight.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Espelha o JSON de TechnicalIndicatorsService.Result.
 * SMA, EMA e RSI podem ser nulos quando o histórico é insuficiente.
 * Datas, cobertura dos ativos e suficiência do histórico exigem regras de negócio.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TechnicalIndicatorsResponse(
        @NotBlank String symbol,
        @NotBlank String source,
        @NotNull LocalDate requestedFrom,
        @NotNull LocalDate requestedTo,
        @NotNull LocalDate historyFrom,
        @NotNull LocalDate asOf,
        @NotNull @Positive Integer observations,
        @NotNull @Positive BigDecimal lastClose,
        @NotNull @Min(2) @Max(250) Integer movingAveragePeriod,
        @PositiveOrZero BigDecimal sma,
        @PositiveOrZero BigDecimal ema,
        @NotNull @Min(2) @Max(250) Integer rsiPeriod,
        @DecimalMin("0") @DecimalMax("100") BigDecimal rsi,
        @NotNull List<@NotBlank String> notes
) implements Serializable { }
