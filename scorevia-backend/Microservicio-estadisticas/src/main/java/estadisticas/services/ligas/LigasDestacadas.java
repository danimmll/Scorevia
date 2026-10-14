package estadisticas.services.ligas;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Las competiciones "grandes", en un solo sitio.
 *
 * <h2>Para que sirve</h2>
 * Varias piezas la usan, y por eso esta aqui y no dentro de ninguna de ellas:
 *
 * <ul>
 *   <li><b>El refresco de plantillas.</b> Solo se mantienen al dia, cada noche, las de los
 *       clubes que juegan estas competiciones (ver {@link ClubesDestacados}).</li>
 *   <li><b>El refresco del timeline en vivo.</b> En un partido que la gente esta mirando,
 *       enterarse de una tarjeta cinco minutos tarde se nota; en una liga regional, no.</li>
 * </ul>
 *
 * <h2>Cuales son</h2>
 * Las 22 que eligio el usuario: las cinco grandes ligas, las tres competiciones UEFA,
 * Portugal, Paises Bajos, Turquia y Belgica, las segundas divisiones de Espana, Italia y
 * Alemania mas la Championship, y Brasil, Argentina, Libertadores, Sudamericana, MLS y
 * Liga MX.
 *
 * <p>Medido sobre la base: <b>260 equipos, 13.729 jugadores</b> y 108.840 partidos.</p>
 */
@Component
public class LigasDestacadas {

    /**
     * Se pueden cambiar sin recompilar. Los ids son los de API-Football:
     * 2 Champions, 3 Europa League, 848 Conference, 11 Sudamericana, 13 Libertadores,
     * 39 Premier, 40 Championship, 61 Ligue 1, 71 Brasileirao, 78 Bundesliga,
     * 79 2.Bundesliga, 88 Eredivisie, 94 Primeira Liga, 128 Liga Argentina, 135 Serie A,
     * 136 Serie B, 140 La Liga, 141 LaLiga2, 144 Pro League, 203 Super Lig,
     * 253 MLS, 262 Liga MX.
     */
    @Value("${scorevia.destacadas.ligas:"
            + "2,3,11,13,39,40,61,71,78,79,88,94,128,135,136,140,141,144,203,253,262,848}")
    private String idsRaw;

    private volatile Set<Integer> ids;

    public Set<Integer> ids() {
        Set<Integer> actual = ids;
        if (actual == null) {
            actual = Arrays.stream(idsRaw.split(","))
                    .map(String::trim)
                    .filter(t -> !t.isEmpty())
                    .map(Integer::valueOf)
                    .collect(Collectors.toUnmodifiableSet());
            ids = actual;
        }
        return actual;
    }

    /** Como lista, que es lo que aceptan los {@code IN (:ligas)} de las consultas. */
    public List<Integer> comoLista() {
        return List.copyOf(ids());
    }

    /**
     * Como literal de array de Postgres ({@code "{140,39,...}"}).
     *
     * <p>Lo piden las consultas nativas que comparan con {@code = ANY(CAST(:ligas AS
     * integer[]))}: asi la lista entera viaja como UN parametro y el plan se reutiliza, en vez
     * de generar un {@code IN} distinto por cada tamano de lista.</p>
     */
    public String comoLiteralPostgres() {
        return ids().stream().map(String::valueOf).collect(Collectors.joining(",", "{", "}"));
    }
}
