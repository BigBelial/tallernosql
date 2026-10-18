package com.noSQL.claseJava.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Collectors;

@Slf4j
@Configuration
public class RedisConfig implements CachingConfigurer {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Serializador JSON de Jackson 3 (es el que usa Spring Data Redis 4).
        // Guarda el nombre de la clase junto con cada valor, así cuando se lee de Redis
        // se recupera el objeto con su tipo real (Book, List<Loan>, etc.).
        // Por seguridad solo se aceptan clases de este proyecto y listas de java.util:
        // si alguien mete en Redis un JSON con otra clase, no se crea ese objeto.
        PolymorphicTypeValidator allowedTypes = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.noSQL.claseJava.")
                .allowIfSubType("java.util.")
                .build();
        GenericJacksonJsonRedisSerializer valueSerializer = GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(allowedTypes)
                .typePropertyName("@class")
                .build();

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(60))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(valueSerializer));

        // Las estadísticas cambian seguido, por eso duran menos tiempo en la caché
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .withCacheConfiguration(CacheNames.LOAN_STATS, config.entryTtl(Duration.ofSeconds(30)))
                .build();
    }

    // La llave de cada dato en la caché lleva el nombre del método, así varias consultas
    // pueden usar la misma caché sin pisarse. Ejemplo:
    // "bookQueries::getBooksByAuthor(Borges)" y "bookQueries::getBooksByCategory(Borges)"
    @Override
    public KeyGenerator keyGenerator() {
        return (target, method, params) -> method.getName() + Arrays.stream(params)
                .map(String::valueOf)
                .collect(Collectors.joining(",", "(", ")"));
    }

    // Si Redis se cae o no se puede leer un dato guardado, la petición no falla:
    // solo se escribe una advertencia en el log y se consulta directamente MongoDB.
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis GET failed on cache '{}' key '{}': {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Redis PUT failed on cache '{}' key '{}': {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis EVICT failed on cache '{}' key '{}': {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Redis CLEAR failed on cache '{}': {}", cache.getName(), exception.getMessage());
            }
        };
    }
}
