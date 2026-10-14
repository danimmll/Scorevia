// estadisticas/repositories/partidos/EstadisticaJugadorPartidoRepository.java
package estadisticas.repositories.partidos;

import java.util.List;
import java.util.Optional;

import estadisticas.entities.jugadores.Jugador;
import estadisticas.entities.partidos.Partido;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import estadisticas.entities.partidos.EstadisticaJugadorPartido;

@Repository
public interface EstadisticaJugadorPartidoRepository extends CrudRepository<EstadisticaJugadorPartido, Integer> {

    // "jugador" además de "equipo": la alineación necesita foto, dorsal e id de cada uno, y
    // sin traerlos en el mismo SELECT esto disparaba una consulta por jugador (22-40 por
    // partido). El mapeo a DTO ya leía jugador.nombre, así que el N+1 existía de antes.
    @EntityGraph(attributePaths = {"equipo", "jugador"})
    List<EstadisticaJugadorPartido> findByPartidoIdPartido(Integer idPartido);

    boolean existsByPartidoIdPartido(Integer idPartido);

    @Query("SELECT ejp FROM EstadisticaJugadorPartido ejp WHERE ejp.jugador.equipo.idEquipo = :equipoId " +
            "AND ejp.partido.liga.idLiga = :ligaId AND ejp.partido.temporada = :temporada")
    List<EstadisticaJugadorPartido> findHistorialEquipoTemporada(
            @Param("equipoId") Integer equipoId,
            @Param("ligaId") Long ligaId,
            @Param("temporada") Integer temporada);

    List<EstadisticaJugadorPartido> findByPartido_IdPartidoAndEquipo_IdEquipo(Integer idPartido, Integer idEquipo);

    /**
     * Lo que hizo un jugador en cada temporada y competicion, agregado de sus partidos.
     *
     * <p>Es la base de la seccion "por temporadas" de la ficha de jugador y de la pantalla de
     * detalle de temporada. Se calcula al vuelo sobre {@code estadisticas_jugador_partido}
     * (7 millones de filas) en vez de mantener una tabla de resumen: los datos ya estan, y una
     * tabla paralela solo anadiria una copia que se puede quedar desfasada cada vez que llega
     * un partido nuevo.</p>
     *
     * <p>Se agrupa tambien por equipo, no solo por temporada y competicion: un jugador puede
     * cambiar de club a mitad de temporada, y mezclar sus dos etapas en una sola fila
     * esconderia justo lo que se quiere ver.</p>
     *
     * <p>Medido con un jugador de 789 partidos: 32 ms en caliente, 400 ms en frio. Por eso el
     * servicio lo cachea.</p>
     *
     * @return filas de {@code [temporada, idLiga, liga, logoLiga, banderaLiga, idEquipo,
     *         equipo, escudoEquipo, partidos, minutos, goles, asistencias, amarillas, rojas,
     *         valoracionMedia, titularidades]}
     */
    @Query("SELECT p.temporada, l.idLiga, l.nombre, l.logo, l.bandera, "
            + "eq.idEquipo, eq.nombre, eq.escudoUrl, "
            + "COUNT(ejp), SUM(ejp.minutosJugados), SUM(ejp.goles), SUM(ejp.asistencias), "
            + "SUM(ejp.tarjetasAmarillas), SUM(ejp.tarjetasRojas), AVG(ejp.valoracion), "
            + "SUM(CASE WHEN ejp.titular = true THEN 1 ELSE 0 END) "
            + "FROM EstadisticaJugadorPartido ejp "
            + "JOIN ejp.partido p JOIN p.liga l JOIN ejp.equipo eq "
            + "WHERE ejp.jugador.idJugador = :idJugador AND p.temporada IS NOT NULL "
            + "GROUP BY p.temporada, l.idLiga, l.nombre, l.logo, l.bandera, "
            + "eq.idEquipo, eq.nombre, eq.escudoUrl "
            + "ORDER BY p.temporada DESC, COUNT(ejp) DESC")
    List<Object[]> resumenPorTemporada(@Param("idJugador") Integer idJugador);

    /**
     * Lo que hizo cada jugador de un equipo en una temporada, agregado de sus partidos.
     *
     * <p>Es la tabla de la pantalla de detalle de temporada: quien jugo, cuanto, y lo que
     * hizo. Sale de los partidos reales y no de la plantilla registrada, asi que incluye a
     * cualquiera que llegara a jugar aunque no figure en el historial de dorsales.</p>
     *
     * @return filas de {@code [idJugador, nombre, foto, posicion, partidos, minutos, goles,
     *         asistencias, amarillas, rojas, valoracionMedia, titularidades]}
     */
    @Query("SELECT j.idJugador, j.nombre, j.fotoUrl, j.posicion, "
            + "COUNT(ejp), SUM(ejp.minutosJugados), SUM(ejp.goles), SUM(ejp.asistencias), "
            + "SUM(ejp.tarjetasAmarillas), SUM(ejp.tarjetasRojas), AVG(ejp.valoracion), "
            + "SUM(CASE WHEN ejp.titular = true THEN 1 ELSE 0 END) "
            + "FROM EstadisticaJugadorPartido ejp "
            + "JOIN ejp.partido p JOIN ejp.jugador j "
            + "WHERE ejp.equipo.idEquipo = :idEquipo AND p.temporada = :temporada "
            + "GROUP BY j.idJugador, j.nombre, j.fotoUrl, j.posicion "
            // COALESCE y no un ORDER BY a secas: en Postgres, DESC pone los NULL PRIMERO, asi
            // que la plantilla se abria con los jugadores de los que no consta ni un minuto.
            // Se comprobo con el Real Madrid 2024 y los tres primeros salian a null.
            + "ORDER BY COALESCE(SUM(ejp.minutosJugados), 0) DESC")
    List<Object[]> resumenPlantillaDeTemporada(@Param("idEquipo") Integer idEquipo,
                                                @Param("temporada") Integer temporada);
}
