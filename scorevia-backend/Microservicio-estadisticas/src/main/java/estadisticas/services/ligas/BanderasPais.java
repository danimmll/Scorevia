package estadisticas.services.ligas;

import estadisticas.repositories.ligas.LigaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * La bandera de un pais a partir de su nombre.
 *
 * <h2>Para que</h2>
 * Un trofeo se guarda como "Super Cup" + pais. Sin la bandera, la ficha de un jugador es una
 * lista de "Super Cup", "Cup", "1 Lyga" que no dice de donde es ninguna: hay Supercopa en
 * cuarenta paises. Con la bandera al lado se lee de un vistazo.
 *
 * <h2>De donde salen</h2>
 * No hace falta pedir nada: la tabla de ligas ya guarda la bandera de su pais, que es la misma
 * URL de API-Football. Aqui solo se le da la vuelta al indice, de pais a bandera.
 *
 * <h2>Por que hay que normalizar el nombre</h2>
 * Los dos sitios escriben el pais distinto, y sin normalizar solo encajaban 159 de los 234
 * paises que aparecen en los trofeos:
 *
 * <pre>
 *   en trofeos          en ligas
 *   ------------------  --------------
 *   Bosnia and Herzegovina   Bosnia-Herzegovina
 *   China PR                 China-Pr
 *   Cape Verde               Cape-Verde-Islands
 * </pre>
 *
 * Se comparan en minusculas y sin separadores, asi que "China PR" y "China-Pr" son la misma
 * clave. Lo que no encaje se queda sin bandera y ya: la competicion sigue leyendose, y una
 * bandera equivocada seria peor que ninguna.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BanderasPais {

    private final LigaRepository ligaRepository;

    /**
     * Se calcula una vez y se guarda. Son ~170 entradas que cambian cuando entra una liga
     * nueva, o sea casi nunca; recorrer las ligas en cada ficha de jugador seria absurdo.
     */
    private final AtomicReference<Map<String, String>> cache = new AtomicReference<>();

    /** La URL de la bandera, o null si ese pais no esta o no se reconoce. */
    public String de(String pais) {
        if (pais == null || pais.isBlank()) {
            return null;
        }
        return mapa().get(normalizar(pais));
    }

    private Map<String, String> mapa() {
        Map<String, String> actual = cache.get();
        if (actual != null) {
            return actual;
        }
        Map<String, String> construido = new HashMap<>();
        for (Object[] fila : ligaRepository.findPaisesConBandera()) {
            String pais = (String) fila[0];
            String bandera = (String) fila[1];
            if (pais != null && bandera != null) {
                construido.putIfAbsent(normalizar(pais), bandera);
            }
        }
        log.info("Banderas por pais: {} paises indexados.", construido.size());
        cache.compareAndSet(null, construido);
        return cache.get();
    }

    /** "Bosnia and Herzegovina", "Bosnia-Herzegovina" y "bosnia herzegovina" -> la misma clave. */
    private String normalizar(String pais) {
        return pais.toLowerCase(Locale.ROOT)
                .replace(" and ", "")
                .replaceAll("[^a-z0-9]", "");
    }
}
