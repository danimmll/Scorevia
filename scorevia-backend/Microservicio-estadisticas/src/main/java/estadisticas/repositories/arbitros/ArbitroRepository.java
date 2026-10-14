package estadisticas.repositories.arbitros;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import estadisticas.entities.arbitros.Arbitro;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ArbitroRepository extends CrudRepository<Arbitro, Integer> {

    // nombre+pais, no solo nombre: API-Football no da un ID estable de árbitro, y dos
    // árbitros reales distintos pueden compartir nombre en países distintos. "pais" nunca
    // debe llegar aquí como null (ver PartidoIngestServiceImpl.resolverArbitro): un UNIQUE
    // de Postgres trata NULL como distinto de cualquier otro NULL, así que con null se
    // colaría una fila duplicada por cada ingesta en vez de reutilizar la existente.
    Optional<Arbitro> findByNombreAndPais(String nombre, String pais);

    // INSERT ... ON CONFLICT DO NOTHING: nunca lanza excepción ante una carrera (dos
    // ingestas concurrentes creando el mismo árbitro nuevo), a diferencia de un guardado
    // normal con captura de la violación de integridad -- en PostgreSQL, un INSERT fallido
    // por violar el UNIQUE de nombre+pais deja la transacción "abortada", y cualquier consulta
    // posterior en esa misma transacción (incluida una relectura de recuperación dentro de esa
    // captura) fallaba también en vez de recuperar la fila que ganó la otra ingesta.
    //
    // @Transactional es obligatorio aquí: Spring Data exige transacción activa para
    // cualquier @Modifying, y quien llama (PartidoIngestServiceImpl.resolverArbitro) no
    // abre ninguna. Sin él, cualquier partido cuyo árbitro no estuviera ya en la base fallaba
    // con "TransactionRequiredException: No active transaction for update or delete query"
    // y se perdía entero -- en el backfill del 2026-09-04 se cayeron así 58 de 451 partidos
    // de un solo día, en silencio salvo por el log.
    //
    // Un ON CONFLICT solo es válido si coincide *literalmente* con un índice único que
    // exista de verdad. Este decía `(nombre, pais)` desde siempre, pero la base viva no lo
    // tenía: llevaba dos únicos redundantes sobre `nombre` a secas y `pais` era nullable
    // con 17.909 filas a NULL, pese a que init.sql declaraba `NOT NULL DEFAULT ''` y
    // `UNIQUE(nombre, pais)` -- otra migración que nunca llegó a la base porque init.sql
    // solo se ejecuta sobre un volumen vacío. La consulta fallaba con "there is no unique
    // or exclusion constraint matching the ON CONFLICT specification" y se perdía el
    // partido entero; estaba tapado por la falta de @Transactional, que reventaba antes.
    // Corregido en la base el 2026-09-04: pais NOT NULL DEFAULT '' y el UNIQUE compuesto.
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(value = "INSERT INTO sch_estadisticas.arbitros (nombre, pais) VALUES (:nombre, :pais) " +
            "ON CONFLICT (nombre, pais) DO NOTHING", nativeQuery = true)
    void insertarSiNoExiste(@Param("nombre") String nombre, @Param("pais") String pais);

    /**
     * Tarjetas mostradas por un árbitro y en cuántos de sus partidos consta el timeline.
     *
     * <p>Una sola fila con tres columnas, en este orden: amarillas, rojas y partidos suyos con
     * eventos registrados. Se devuelve como {@code List<Object[]>} y no como proyección con
     * getters porque una consulta nativa no garantiza el mapeo de alias a nombres de método;
     * la lista trae una fila o ninguna.</p>
     *
     * <p>El tercer número no es decorativo: es el divisor honesto de "amarillas por partido".
     * Muchos partidos antiguos nunca llegaron a tener eventos ingeridos, y dividir entre TODOS
     * los partidos dirigidos hundiría la media de cualquier árbitro veterano. Por eso cuenta
     * partidos con CUALQUIER evento, no solo con tarjeta: un partido sin ninguna amarilla es
     * un dato real que debe bajar la media, siempre que sepamos que su timeline se ingirió.</p>
     *
     * <p>La doble amarilla cuenta como roja además de como amarilla, que es lo que pasa de
     * verdad en el campo: API-Football manda ese evento con detalle propio y no repite la
     * amarilla, así que sin este caso las expulsiones por doble amarilla no saldrían.</p>
     */
    @Query(value = """
            SELECT COALESCE(SUM(CASE WHEN e.tipo_evento ILIKE 'card'
                                      AND LOWER(e.detalle) LIKE '%yellow%' THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN e.tipo_evento ILIKE 'card'
                                      AND (LOWER(e.detalle) LIKE '%red%'
                                        OR LOWER(e.detalle) LIKE '%second yellow%') THEN 1 ELSE 0 END), 0),
                   COUNT(DISTINCT e.id_partido)
            FROM sch_estadisticas.eventos_partido e
            JOIN sch_estadisticas.partidos p ON p.id_partido = e.id_partido
            WHERE p.id_arbitro = :idArbitro
            """, nativeQuery = true)
    List<Object[]> contarTarjetasPorArbitro(@Param("idArbitro") Integer idArbitro);
}
