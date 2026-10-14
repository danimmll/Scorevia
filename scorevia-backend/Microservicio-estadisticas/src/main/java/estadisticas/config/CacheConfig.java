package estadisticas.config;

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
 * para las cachés declaradas con @Cacheable/@CacheEvict en este servicio. "contextoPrediccion"
 * en particular se cachea por id de partido y puede acumular miles de entradas con el tiempo,
 * así que aquí se sustituye por Caffeine con un tamaño acotado y un TTL de seguridad por
 * caché, para no crecer sin límite y para que una entrada nunca quede obsoleta para siempre
 * si algún día una escritura no pasa por el @CacheEvict correcto.
 */
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                construirCache("clasificaciones", 100, 30, TimeUnit.MINUTES),
                construirCache("partidosLiga", 200, 15, TimeUnit.MINUTES),
                construirCache("contextoPrediccion", 500, 15, TimeUnit.MINUTES),
                // El catálogo de temporadas de una liga solo cambia cuando entra una
                // sincronización nueva, y lo pide cada pantalla que lleva selector de temporada.
                construirCache("temporadasLiga", 1500, 30, TimeUnit.MINUTES),
                // TTL corto a propósito: en un partido en juego la alineación cambia sola
                // (entran suplentes, suben los minutos), así que no puede quedarse fija media
                // hora. En un partido terminado ya no cambia nunca, y ahí dos minutos bastan
                // para absorber las visitas seguidas a la misma ficha.
                construirCache("alineacionesPartido", 500, 2, TimeUnit.MINUTES),
                // 3 horas es exactamente cada cuánto refresca API-Football las cuotas
                // pre-partido: pedirlas más a menudo devuelve lo mismo y gasta cuota. Con
                // esto, el segundo visitante de una ficha no cuesta ninguna petición.
                construirCache("cuotasPartido", 2000, 3, TimeUnit.HOURS),
                // Las bajas las refresca cada 4 horas; se cachean 2 para que un cambio de
                // última hora (un sancionado que aparece la víspera) no tarde media tarde.
                construirCache("bajasPartido", 2000, 2, TimeUnit.HOURS),
                // Los rankings de una liga se mueven como mucho una vez por jornada.
                construirCache("rankingsLiga", 500, 6, TimeUnit.HOURS),
                // API-Football refresca el rendimiento de equipo dos veces al día; 6 horas
                // de caché no pierde nada y evita repetir la petición por cada visitante.
                construirCache("rendimientoEquipo", 1000, 6, TimeUnit.HOURS),
                // Agregados sobre estadisticas_jugador_partido (7 millones de filas). Cambian
                // solo cuando entra un partido nuevo del jugador o del equipo, asi que 6 horas
                // no ensena nada desfasado y evita repetir 400 ms de consulta en frio.
                construirCache("temporadasJugador", 2000, 6, TimeUnit.HOURS),
                construirCache("plantillaTemporada", 1000, 6, TimeUnit.HOURS)
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
