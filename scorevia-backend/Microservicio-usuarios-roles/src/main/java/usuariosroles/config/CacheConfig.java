package usuariosroles.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Por defecto Spring usa un ConcurrentMapCacheManager (sin límite de tamaño ni expiración)
 * para las cachés declaradas con @Cacheable/@CacheEvict en este servicio. Aquí se sustituye
 * por Caffeine con un tamaño acotado y un TTL de seguridad por caché, para que una entrada
 * nunca quede obsoleta para siempre si algún día una escritura no pasa por el @CacheEvict
 * correcto.
 */
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                construirCache("roles", 20, 20, TimeUnit.MINUTES)
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
