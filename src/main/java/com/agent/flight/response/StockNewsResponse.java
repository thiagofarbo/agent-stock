package com.agent.flight.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Retorno de /v1/news/all. Uma lista de notícias vazia é válida. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StockNewsResponse(
        @NotNull @Valid NewsMeta meta,
        @NotNull List<@NotNull @Valid Article> data
) implements Serializable {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NewsMeta(
            @NotNull @PositiveOrZero Long found,
            @NotNull @PositiveOrZero Integer returned,
            @NotNull @Positive Integer limit,
            @NotNull @Positive Integer page
    ) implements Serializable { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Article(
            @NotBlank String uuid,
            @NotBlank String title,
            String description,
            String snippet,
            @NotBlank String url,
            @JsonProperty("image_url") String imageUrl,
            String language,
            @NotNull @JsonProperty("published_at") OffsetDateTime publishedAt,
            @NotBlank String source,
            @JsonProperty("relevance_score") BigDecimal relevanceScore,
            List<@NotNull @Valid Entity> entities
    ) implements Serializable { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entity(
            @NotBlank String symbol,
            String name,
            String exchange,
            @JsonProperty("exchange_long") String exchangeLong,
            String country,
            String type,
            String industry,
            @JsonProperty("match_score") BigDecimal matchScore,
            @DecimalMin("-1") @DecimalMax("1")
            @JsonProperty("sentiment_score") BigDecimal sentimentScore
    ) implements Serializable { }
}
