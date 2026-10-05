package com.agent.flight.config;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.util.Objects;
import java.util.Map;
import java.time.Clock;
import java.time.Duration;
import dev.langchain4j.data.document.Metadata;

import java.util.Optional;
import java.util.function.Supplier;

/** Cache de respostas, indexadas pelo embedding da pergunta. */
public class SemanticCache {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final double minScore;
    private final Clock clock;

    public SemanticCache(EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> embeddingStore,
                         double minScore) {
        this(embeddingModel, embeddingStore, minScore, Clock.systemUTC());
    }

    SemanticCache(EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> embeddingStore,
                  double minScore, Clock clock) {
        this.clock = Objects.requireNonNull(clock);
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel");
        this.embeddingStore = Objects.requireNonNull(embeddingStore, "embeddingStore");
        if (!Double.isFinite(minScore) || minScore < 0 || minScore > 1) {
            throw new IllegalArgumentException("minScore deve estar entre 0 e 1");
        }
        this.minScore = minScore;
    }

    public String getOrCompute(String question, Supplier<String> responseSupplier) {
        return lookup(question, null, responseSupplier);
    }

    /** Para dados de mercado: reutiliza somente a mesma pergunta dentro da validade. */
    public String getOrCompute(String question, Duration ttl, Supplier<String> responseSupplier) {
        if (ttl == null || ttl.toMillis() <= 0) throw new IllegalArgumentException("TTL deve ser positivo");
        return lookup(question, ttl, responseSupplier);
    }

    private String lookup(
            String question,
            Duration ttl,
            Supplier<String> responseSupplier
    ) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("A pergunta não pode ser vazia");
        }

        Objects.requireNonNull(responseSupplier, "responseSupplier");

        Embedding questionVector = embeddingModel.embed(question).content();

        Optional<String> cached = findCachedResponse(
                question,
                questionVector,
                ttl != null
        );

        if (cached.isPresent()) {
            return cached.get();
        }

        String response = responseSupplier.get();

        if (response != null
                && !response.isBlank()
                && !response.startsWith("Erro:")) {

            var metadata = ttl == null
                    ? new Metadata()
                    : Metadata.from(Map.of(
                    "cacheQuestion", question,
                    "cacheExpiresAt",
                    Math.addExact(clock.millis(), ttl.toMillis())
            ));

            embeddingStore.add(
                    questionVector,
                    TextSegment.from(response, metadata)
            );
        }

        return response;
    }

    /**
     * Consulta uma resposta para a mesma pergunta, ainda dentro da validade.
     * Não gera nem grava uma nova resposta.
     */
    public Optional<String> findFresh(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("A pergunta não pode ser vazia");
        }

        Embedding questionVector = embeddingModel.embed(question).content();

        return findCachedResponse(question, questionVector, true);
    }

    private Optional<String> findCachedResponse(
            String question,
            Embedding questionVector,
            boolean requireFresh
    ) {
        var request = EmbeddingSearchRequest.builder()
                .queryEmbedding(questionVector)
                .maxResults(1)
                .minScore(minScore)
                .build();

        var matches = embeddingStore.search(request).matches();

        if (matches.isEmpty()) {
            return Optional.empty();
        }

        var match = matches.getFirst();
        TextSegment cached = match.embedded();

        if (cached == null || cached.text().isBlank()) {
            return Optional.empty();
        }

        // Mantém o comportamento do cache sem TTL para conteúdo estável.
        if (!requireFresh) {
            return Optional.of(cached.text());
        }

        var metadata = cached.metadata().toMap();

        boolean exactQuestion =
                question.equals(metadata.get("cacheQuestion"));

        boolean fresh =
                metadata.get("cacheExpiresAt") instanceof Number expires
                        && expires.longValue() > clock.millis();

        if (exactQuestion && fresh) {
            return Optional.of(cached.text());
        }

        if (exactQuestion && !fresh) {
            embeddingStore.remove(match.embeddingId());
        }

        return Optional.empty();
    }
}
