package predicciones.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * TTL corto a propósito: {@code contextoPrediccionLive} respalda el endpoint
 * /predicciones/en-vivo, pensado para ser sondeado repetidamente mientras un partido está
 * en curso. Sin caché, cada sondeo de cada usuario viendo el mismo partido recalculaba
 * desde cero el contexto completo (Feign a estadísticas + Dixon-Coles), multiplicando
 * carga redundante sobre estadísticas. 15s es lo bastante corto para no notarse como un
 * dato "viejo" en una predicción en vivo, y lo bastante largo para absorber ráfagas de
 * varios usuarios mirando el mismo partido a la vez.
 */
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                construirCache("contextoPrediccionLive", 200, 15, TimeUnit.SECONDS)
        ));
        return manager;
    }

    private CaffeineCache construirCache(String nombre, int tamanoMaximo, long duracion, TimeUnit unidad) {
        return new CaffeineCache(nombre, Caffeine.newBuilder()
                .maximumSize(tamanoMaximo)
                .expireAfterWrite(duracion, unidad)
                .build());
    }
}
