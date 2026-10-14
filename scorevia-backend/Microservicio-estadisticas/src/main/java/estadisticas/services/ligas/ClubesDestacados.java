package estadisticas.services.ligas;

import estadisticas.repositories.ligas.ClasificacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Los clubes que juegan las competiciones destacadas.
 *
 * <h2>Por qué no basta con {@code equipos.id_liga}</h2>
 * Porque miente. Es una FK única que la última ingesta se pisa, y un club juega a la vez su
 * liga, la copa, la Champions y los amistosos de pretemporada. Medido el 2026-09-09 sobre la
 * base viva:
 *
 * <pre>
 *   Real Madrid       -> International Champions Cup
 *   Manchester United -> Friendlies Clubs
 *   Barcelona         -> International Champions Cup
 * </pre>
 *
 * <p>Filtrar por {@code e.id_liga IN (las 22 destacadas)} devolvía 260 equipos, pero <b>no los
 * grandes</b>: salían el Sillamäe Kalev y el Ararat-Armenia, que son los que casualmente
 * tenían ahí un id de una destacada. Con eso, el refresco de plantillas
 * recorría 218 equipos equivocados: el Real Madrid ni se miraba, y por eso Kroos seguía en la
 * plantilla después de tres intentos.</p>
 *
 * <h2>De dónde sale entonces</h2>
 * De {@code clasificaciones}, que es la tabla que relaciona equipo + liga + temporada de
 * verdad, y es el mismo criterio que ya usaban {@code ligaDeJugador} y
 * {@code EquipoServiceImpl.obtenerEquiposPorLiga}. Acotado a las temporadas recientes para no
 * arrastrar plantillas de clubes que hace diez años estaban en Primera, y sin las competiciones
 * continentales (ver {@code continentalesRaw}): <b>403 clubes</b> frente a 726 sin acotar, y
 * ahí sí están el Madrid, el United, el Bayern y el resto.
 *
 * <p>Cuesta 14 ms y se memoriza media hora, porque lo consultan varias sincronizaciones.</p>
 */
@Component
@RequiredArgsConstructor
public class ClubesDestacados {

    private static final long TTL_NANOS = Duration.ofMinutes(30).toNanos();

    private final ClasificacionRepository clasificacionRepository;
    private final LigasDestacadas ligasDestacadas;

    /** Desde qué temporada cuenta que un club "juega" una destacada. */
    @Value("${scorevia.destacadas.temporada-minima:2024}")
    private int temporadaMinima;

    /**
     * Competiciones que NO aportan clubes a esta lista, aunque sí sean destacadas.
     *
     * <p>Son las continentales (Champions, Europa, Conference, Libertadores, Sudamericana).
     * Siguen contando como destacadas para todo lo que gira alrededor de un partido —un
     * Madrid-Bayern de Champions es de lo más reconocible que hay—, pero por su tabla pasan
     * también los clubes que cayeron en una previa, como el Hamrun Spartans maltés, que juega
     * la Conference. Los clubes que
     * importan ya entran por su liga doméstica, así que quitarlas no pierde ninguno: se pasa
     * de 541 clubes a 403 y siguen estando Madrid, City, Bayern, Inter y compañía.</p>
     */
    @Value("${scorevia.destacadas.ligas-sin-clubes-propios:2,3,11,13,848}")
    private String continentalesRaw;

    private record Cacheados(List<Integer> ids, long expiraEn) { }

    private volatile Cacheados cache;

    public List<Integer> ids() {
        Cacheados actual = cache;
        if (actual != null && System.nanoTime() < actual.expiraEn()) {
            return actual.ids();
        }
        List<Integer> frescos = clasificacionRepository.idsEquiposDeLigasDesde(
                ligasQueAportanClubes(), temporadaMinima);
        cache = new Cacheados(frescos, System.nanoTime() + TTL_NANOS);
        return frescos;
    }

    private List<Integer> ligasQueAportanClubes() {
        java.util.Set<Integer> continentales = java.util.Arrays.stream(continentalesRaw.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .map(Integer::valueOf)
                .collect(java.util.stream.Collectors.toSet());
        List<Integer> domesticas = ligasDestacadas.comoLista().stream()
                .filter(id -> !continentales.contains(id))
                .toList();
        // Si alguien vaciara la lista de destacadas dejando solo continentales, mejor todas
        // que ninguna: un juego difícil se puede jugar, uno sin sujeto no.
        return domesticas.isEmpty() ? ligasDestacadas.comoLista() : domesticas;
    }

    /** Como literal de array de Postgres, para las consultas nativas. */
    public String comoLiteralPostgres() {
        return ids().stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }
}
