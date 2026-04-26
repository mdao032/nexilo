package com.nexilo.common.config;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Générateur de clés de cache basé sur SHA-256.
 *
 * <p>Produit une clé de la forme {@code ClassName.methodName:sha256(args)}.
 * Utilisé pour garantir l'idempotence entre différents uploads du même PDF
 * (lorsque la clé est dérivée du hash SHA-256 du contenu).
 *
 * <p>Usage : {@code @Cacheable(value="summaries", keyGenerator="sha256KeyGenerator")}
 */
@Component("sha256KeyGenerator")
public class CacheKeyGenerator implements KeyGenerator {

    @Override
    public Object generate(Object target, Method method, Object... params) {
        String raw = buildRaw(target, method, params);
        return target.getClass().getSimpleName() + "." + method.getName() + ":" + sha256(raw);
    }

    private String buildRaw(Object target, Method method, Object... params) {
        StringBuilder sb = new StringBuilder();
        sb.append(target.getClass().getName()).append('.').append(method.getName());
        for (Object p : params) {
            sb.append(':');
            sb.append(p != null ? p.toString() : "null");
        }
        return sb.toString();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16); // 16 chars suffisent
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 est garanti disponible dans tout JDK
            throw new IllegalStateException("SHA-256 non disponible", e);
        }
    }
}

