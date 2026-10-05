package com.agent.flight.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class TechnicalIndicatorsServiceTests {
    private final LocalDate from = LocalDate.of(2024, 1, 1);

    private List<TechnicalIndicatorsService.Candle> history(String... closes) {
        var result = new ArrayList<TechnicalIndicatorsService.Candle>();
        for (int i = 0; i < closes.length; i++) {
            result.add(new TechnicalIndicatorsService.Candle(from.plusDays(i), new BigDecimal(closes[i])));
        }
        return result;
    }

    @Test
    void calculatesSmaEmaAndSmoothedWilderRsiAfterSorting() {
        var candles = history("10", "12", "11", "14", "13");
        Collections.reverse(candles);
        var result = TechnicalIndicatorsService.calculate("TSLA", candles, 3, 3, from, from.plusDays(10));
        assertThat(result.sma()).isEqualByComparingTo("12.6667");
        assertThat(result.ema()).isEqualByComparingTo("12.7500");
        assertThat(result.rsi()).isEqualByComparingTo("66.6667");
        assertThat(result.asOf()).isEqualTo(from.plusDays(4));
        assertThat(result.lastClose()).isEqualByComparingTo("13");
    }

    @Test
    void handlesRsiExtremesAndFlatSeries() {
        assertThat(TechnicalIndicatorsService.calculate("X", history("1", "2", "3", "4"), 3, 3, from, from.plusDays(4)).rsi())
                .isEqualByComparingTo("100");
        assertThat(TechnicalIndicatorsService.calculate("X", history("4", "3", "2", "1"), 3, 3, from, from.plusDays(4)).rsi())
                .isEqualByComparingTo("0");
        assertThat(TechnicalIndicatorsService.calculate("X", history("2", "2", "2", "2"), 3, 3, from, from.plusDays(4)).rsi())
                .isEqualByComparingTo("50");
    }

    @Test
    void requiresOneMoreCloseForRsiThanForMovingAverages() {
        var result = TechnicalIndicatorsService.calculate("X", history("10", "12", "11"), 3, 3, from, from.plusDays(3));
        assertThat(result.sma()).isEqualByComparingTo("11");
        assertThat(result.ema()).isEqualByComparingTo("11");
        assertThat(result.rsi()).isNull();
        assertThat(result.notes()).anyMatch(note -> note.contains("necessários 4"));
        var shortResult = TechnicalIndicatorsService.calculate("X", history("10"), 3, 3, from, from.plusDays(3));
        assertThat(shortResult.sma()).isNull();
        assertThat(shortResult.ema()).isNull();
    }

    @Test
    void rejectsDuplicateDatesAndInvalidPrices() {
        var candles = history("10", "11");
        candles.add(candles.getFirst());
        assertThatIllegalArgumentException().isThrownBy(() -> TechnicalIndicatorsService.calculate("X", candles, 2, 2, from, from.plusDays(3)));
        assertThatIllegalArgumentException().isThrownBy(() -> TechnicalIndicatorsService.calculate("X", history("0", "11"), 2, 2, from, from.plusDays(3)));
    }
}
