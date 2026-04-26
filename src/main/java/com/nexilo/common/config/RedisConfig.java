package com.nexilo.common.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration Redis et du gestionnaire de caches pour Nexilo.
 *
 * <p>TTL par cache :
 * <ul>
 *   <li>{@code summaries}   — 7 jours</li>
 *   <li>{@code extractions} — 7 jours</li>
 *   <li>{@code qna}         — 24 heures</li>
 * </ul>
 *
 * <p>La sérialisation JSON (GenericJackson2JsonRedisSerializer) est utilisée à la place
 * de la sérialisation Java native pour la portabilité entre déploiements.
 */
@Slf4j
@EnableCaching
@Configuration
public class RedisConfig {

    /**
     * Sérialiseur JSON partagé, configuré pour inclure l'information de type afin
     * de pouvoir désérialiser correctement les objets sans connaître leur type à l'avance.
     */
    @Bean
    public GenericJackson2JsonRedisSerializer genericJackson2JsonRedisSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        // Active le polymorphisme : stocke le type Java dans le JSON Redis
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return new GenericJackson2JsonRedisSerializer(mapper);
    }

    /**
     * Gestionnaire de caches Redis avec TTL personnalisés par cache.
     * Si Redis est indisponible au démarrage, bascule sur un NoOpCacheManager
     * (pas de cache — toutes les opérations passent directement en base/IA).
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                     GenericJackson2JsonRedisSerializer serializer) {
        try {
            // Test de connectivité Redis au démarrage
            connectionFactory.getConnection().ping();

            RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                    .serializeKeysWith(RedisSerializationContext.SerializationPair
                            .fromSerializer(new StringRedisSerializer()))
                    .serializeValuesWith(RedisSerializationContext.SerializationPair
                            .fromSerializer(serializer))
                    .disableCachingNullValues();

            Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
            cacheConfigs.put("summaries",   base.entryTtl(Duration.ofDays(7)));
            cacheConfigs.put("extractions", base.entryTtl(Duration.ofDays(7)));
            cacheConfigs.put("qna",         base.entryTtl(Duration.ofHours(24)));

            log.info("Cache Redis activé — summaries=7j, extractions=7j, qna=24h");
            return RedisCacheManager.builder(connectionFactory)
                    .cacheDefaults(base.entryTtl(Duration.ofHours(1)))
                    .withInitialCacheConfigurations(cacheConfigs)
                    .build();

        } catch (Exception e) {
            log.warn("Redis indisponible au démarrage ({}) — cache désactivé (NoOpCacheManager). " +
                     "Lancez Redis pour activer le cache.", e.getMessage());
            return new NoOpCacheManager();
        }
    }
}

