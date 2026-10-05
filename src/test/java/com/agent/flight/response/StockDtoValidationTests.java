package com.agent.flight.response;

import com.agent.flight.service.TechnicalIndicatorsService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StockDtoValidationTests {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    @Test
    void validatesNestedQuotesAndPreservesNullableFields() {
        var response = JSON.readValue("""
                {"meta":{"requested":1,"returned":1},"data":[{
                  "ticker":"TSLA","currency":"USD","price":358.07,
                  "last_trade_time":"2026-09-16T16:00:00.000000",
                  "exchange_short":null,"market_cap":null
                }]}
                """, StockResponse.class);
        assertThat(VALIDATOR.validate(response)).isEmpty();
        response.getData().getFirst().setPrice(BigDecimal.ZERO);
        assertThat(VALIDATOR.validate(response))
                .anyMatch(v -> v.getPropertyPath().toString().equals("data[0].price"));
    }

    @Test
    void rejectsEmptyQuotesButAcceptsEmptyNews() {
        var quotes = JSON.readValue("""
                {"meta":{"requested":1,"returned":0},"data":[]}
                """, StockResponse.class);
        assertThat(VALIDATOR.validate(quotes)).isNotEmpty();
        var news = JSON.readValue("""
                {"meta":{"found":0,"returned":0,"limit":5,"page":1},"data":[]}
                """, StockNewsResponse.class);
        assertThat(VALIDATOR.validate(news)).isEmpty();
    }

    @Test
    void readsNewsTimestampAndValidatesNestedArticle() {
        var news = JSON.readValue("""
                {"meta":{"found":1,"returned":1,"limit":5,"page":1},"data":[{
                  "uuid":"example-id","title":"Market update","url":"https://example.com/news",
                  "published_at":"2026-09-16T16:00:00.123456Z","source":"example.com",
                  "entities":[{"symbol":"TSLA","sentiment_score":0.5}],
                  "similar":[]
                }]}
                """, StockNewsResponse.class);
        assertThat(VALIDATOR.validate(news)).isEmpty();
        assertThat(news.data().getFirst().publishedAt().getNano()).isEqualTo(123456000);
        var invalid = JSON.readValue("""
                {"meta":{"found":1,"returned":1,"limit":5,"page":1},
                 "data":[{"title":" "}]}
                """, StockNewsResponse.class);
        assertThat(VALIDATOR.validate(invalid))
                .anyMatch(v -> v.getPropertyPath().toString().equals("data[0].title"));
    }

    @Test
    void readsActualIndicatorOutputWithInsufficientHistory() {
        var date = LocalDate.of(2026, 9, 16);
        var result = TechnicalIndicatorsService.calculate("TSLA",
                List.of(new TechnicalIndicatorsService.Candle(date, new BigDecimal("358.07"))),
                20, 14, date.minusDays(30), date);
        var dto = JSON.readValue(JSON.writeValueAsString(result), TechnicalIndicatorsResponse.class);
        assertThat(VALIDATOR.validate(dto)).isEmpty();
        assertThat(dto.rsi()).isNull();
        assertThat(dto.sma()).isNull();
        assertThat(dto.notes()).isNotEmpty();

        var invalidTree = JSON.valueToTree(result);
        ((tools.jackson.databind.node.ObjectNode) invalidTree).put("rsi", 101);
        var invalid = JSON.treeToValue(invalidTree, TechnicalIndicatorsResponse.class);
        assertThat(VALIDATOR.validate(invalid))
                .anyMatch(v -> v.getPropertyPath().toString().equals("rsi"));
    }

    @Test
    void rejectsMissingRequiredFieldsAndNullListElements() {
        assertThat(VALIDATOR.validate(JSON.readValue("{}", TechnicalIndicatorsResponse.class)))
                .anyMatch(v -> v.getPropertyPath().toString().equals("observations"));
        var response = JSON.readValue("""
                {"meta":{"requested":1,"returned":1},"data":[null]}
                """, StockResponse.class);
        assertThat(VALIDATOR.validate(response)).isNotEmpty();
    }
}
