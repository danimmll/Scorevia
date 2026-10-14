package estadisticas.repositories.partidos;

import java.util.List;
import java.util.Optional;

import estadisticas.entities.equipos.Equipo;
import estadisticas.entities.partidos.Partido;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import estadisticas.entities.partidos.EstadisticaEquipoPartido;

@Repository
public interface EstadisticaEquipoPartidoRepository extends CrudRepository<EstadisticaEquipoPartido, Integer> {

    List<EstadisticaEquipoPartido> findByEquipo_IdEquipo(Integer idEquipo);

    Optional<EstadisticaEquipoPartido> findByPartido_IdPartidoAndEquipo_IdEquipo(Integer idPartido, Integer idEquipo);

    // Comprueba si un partido ya tiene las estadísticas de equipo descargadas
    boolean existsByPartidoIdPartido(Integer idPartido);

    @EntityGraph(attributePaths = {"equipo"})
    List<EstadisticaEquipoPartido> findByPartidoIdPartido(Integer idPartido);

    List<EstadisticaEquipoPartido> findByPartido_Arbitro_IdArbitro(Integer idArbitro);

    @Query("SELECT e FROM EstadisticaEquipoPartido e " +
            "WHERE e.equipo.idEquipo = :equipoId " +
            "AND e.partido.liga.idLiga = :ligaId " +
            "AND e.partido.temporada = :temporada")
    List<EstadisticaEquipoPartido> findByEquipoIdAndPartidoLigaIdAndPartidoTemporada(
            @Param("equipoId") Integer equipoId,
            @Param("ligaId") Integer ligaId,
            @Param("temporada") Integer temporada);

    Optional<EstadisticaEquipoPartido> findByPartidoAndEquipo(Partido partido, Equipo equipo);
}
