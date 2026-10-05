package com.agent.flight.config;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class SemanticCacheTests {
    private final EmbeddingModel model = new EmbeddingModel() {
        @Override
        public Response<Embedding> embed(String text) {
            // Perguntas equivalentes têm vetores próximos; outro assunto é ortogonal.
            return Response.from(Embedding.from(switch (text) {
                case "outra pergunta" -> new float[]{0, 1, 0};
                case "pergunta equivalente" -> new float[]{1, 0.01f, 0};
                default -> new float[]{1, 0, 0};
            }));
        }
    };
    private final SemanticCache cache = new SemanticCache(model, new InMemoryEmbeddingStore<TextSegment>(), 0.9);

    @Test
    void generatesOnMissAndReusesSimilarQuestion() {
        var calls = new AtomicInteger();
        assertThat(cache.getOrCompute("pergunta", () -> "resposta " + calls.incrementAndGet()))
                .isEqualTo("resposta 1");
        assertThat(cache.getOrCompute("pergunta equivalente", () -> "resposta " + calls.incrementAndGet()))
                .isEqualTo("resposta 1");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void unrelatedQuestionDoesNotReuseResponse() {
        cache.getOrCompute("pergunta", () -> "primeira resposta");
        assertThat(cache.getOrCompute("outra pergunta", () -> "outra resposta")).isEqualTo("outra resposta");
    }

    @Test
    void doesNotCacheNullBlankOrFailedGeneration() {
        assertThat(cache.getOrCompute("pergunta", () -> null)).isNull();
        assertThat(cache.getOrCompute("pergunta", () -> " ")).isBlank();
        assertThatThrownBy(() -> cache.getOrCompute("pergunta", () -> {
            throw new IllegalStateException("Falha no provedor");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(cache.getOrCompute("pergunta", () -> "nova resposta")).isEqualTo("nova resposta");
    }

    @Test
    void rejectsInvalidInputs() {
        assertThatIllegalArgumentException().isThrownBy(() -> cache.getOrCompute(" ", () -> "resposta"));
        assertThatIllegalArgumentException().isThrownBy(() -> cache.getOrCompute(null, () -> "resposta"));
        assertThatIllegalArgumentException().isThrownBy(() -> new SemanticCache(model,
                new InMemoryEmbeddingStore<>(), Double.NaN));
        assertThatIllegalArgumentException().isThrownBy(() -> new SemanticCache(model,
                new InMemoryEmbeddingStore<>(), 1.1));
    }
    @Test
    void marketCacheRequiresExactQuestionAndExpires() {
        var store = new InMemoryEmbeddingStore<TextSegment>();
        var now = java.time.Instant.parse("2024-01-01T00:00:00Z");
        var cache = new SemanticCache(model, store, 0.9, java.time.Clock.fixed(now, java.time.ZoneOffset.UTC));
        var ttl = java.time.Duration.ofSeconds(60);
        assertThat(cache.getOrCompute("pergunta", ttl, () -> "primeira")).isEqualTo("primeira");
        assertThat(cache.getOrCompute("pergunta", ttl, () -> "não executar")).isEqualTo("primeira");
        assertThat(cache.getOrCompute("pergunta equivalente", ttl, () -> "outra")).isEqualTo("outra");
        var later = new SemanticCache(model, store, 0.9,
                java.time.Clock.fixed(now.plusSeconds(60), java.time.ZoneOffset.UTC));
        assertThat(later.getOrCompute("pergunta", ttl, () -> "atualizada")).isEqualTo("atualizada");
    }

    @Test
    void marketCacheIgnoresOldEntriesWithoutValidity() {
        cache.getOrCompute("pergunta", () -> "legado");
        assertThat(cache.getOrCompute("pergunta", java.time.Duration.ofSeconds(60), () -> "nova"))
                .isEqualTo("nova");
    }

}
