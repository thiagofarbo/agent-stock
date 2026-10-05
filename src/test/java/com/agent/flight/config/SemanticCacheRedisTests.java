package com.agent.flight.config;

import dev.langchain4j.community.store.embedding.redis.RedisEmbeddingStore;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import redis.clients.jedis.RedisClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@EnabledIfSystemProperty(named = "redis.integration", matches = "true")
class SemanticCacheRedisTests {
    @Test
    void createsVectorIndexAndReadsPersistedAnswer() {
        String index = "cache-test-" + UUID.randomUUID();
        try (var client = RedisClient.create("localhost", Integer.getInteger("redis.test.port", 16379))) {
            var store = RedisEmbeddingStore.builder().unifiedJedis(client)
                    .indexName(index).prefix(index + ":").dimension(3).metadataConfig(SemanticCacheConfig.cacheMetadataSchema()).build();
            try {
                EmbeddingModel model = new EmbeddingModel() {
                    @Override
                    public Response<Embedding> embed(String text) {
                        return Response.from(Embedding.from(new float[]{1, 0, 0}));
                    }
                };
                var cache = new SemanticCache(model, store, 0.9);
                assertThat(cache.getOrCompute("pergunta", java.time.Duration.ofSeconds(60), () -> "resposta persistida"))
                        .isEqualTo("resposta persistida");
                // Um novo cache precisa encontrar o valor gravado pelo anterior.
                var secondCache = new SemanticCache(model, store, 0.9);
                assertThat(secondCache.getOrCompute("pergunta", java.time.Duration.ofSeconds(60), () -> fail("Não deve gerar novamente")))
                        .isEqualTo("resposta persistida");
                var expiredCache = new SemanticCache(model, store, 0.9, java.time.Clock.fixed(
                        java.time.Instant.now().plusSeconds(120), java.time.ZoneOffset.UTC));
                assertThat(expiredCache.getOrCompute("pergunta", java.time.Duration.ofSeconds(60), () -> "atualizada"))
                        .isEqualTo("atualizada");
            } finally {
                client.ftDropIndexDD(index);
            }
        }
    }
}
