package com.agent.flight.config;

import dev.langchain4j.community.store.embedding.redis.RedisEmbeddingStore;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import redis.clients.jedis.RedisClient;

@Configuration
public class SemanticCacheConfig {

    @Bean(destroyMethod = "close")
    @Lazy
    public RedisClient semanticCacheRedisClient(
            @Value("${semantic-cache.redis.host}") String host,
            @Value("${semantic-cache.redis.port}") int port) {
        return RedisClient.create(host, port);
    }

    @Bean
    @Lazy
    public SemanticCache semanticCache(
            RedisClient semanticCacheRedisClient,
            @Value("${cv.openai.api-key}") String apiKey,
            @Value("${semantic-cache.embedding.model}") String model,
            @Value("${semantic-cache.embedding.dimension}") int dimension,
            @Value("${semantic-cache.redis.index}") String index,
            @Value("${semantic-cache.redis.prefix}") String prefix,
            @Value("${semantic-cache.min-score}") double minScore) {
        var embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(model)
                .dimensions(dimension)
                .build();
        var embeddingStore = RedisEmbeddingStore.builder()
                .unifiedJedis(semanticCacheRedisClient)
                .indexName(index)
                .prefix(prefix)
                .dimension(dimension)
                .metadataConfig(cacheMetadataSchema())
                .build();
        return new SemanticCache(embeddingModel, embeddingStore, minScore);
    }

    static java.util.Map<String, redis.clients.jedis.search.schemafields.SchemaField> cacheMetadataSchema() {
        return java.util.Map.of(
                "cacheQuestion", redis.clients.jedis.search.schemafields.TextField.of("$.cacheQuestion").as("cacheQuestion"),
                "cacheExpiresAt", redis.clients.jedis.search.schemafields.NumericField.of("$.cacheExpiresAt").as("cacheExpiresAt"));
    }
}
