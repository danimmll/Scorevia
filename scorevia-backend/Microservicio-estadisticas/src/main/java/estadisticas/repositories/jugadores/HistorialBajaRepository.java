package estadisticas.repositories.jugadores;

import estadisticas.entities.jugadores.HistorialBaja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface HistorialBajaRepository extends JpaRepository<HistorialBaja, Long> {
    List<HistorialBaja> findByJugador_IdJugadorOrderByFechaInicioDesc(Integer idJugador);

    // Usado por la ingesta: si la baja ya existe (mismo jugador+tipo+inicio) se actualiza
    // en lugar de insertarla de nuevo, para no duplicar en cada re-sync.
    Optional<HistorialBaja> findByJugador_IdJugadorAndTipoAndFechaInicio(
            Integer idJugador, String tipo, LocalDate fechaInicio);

    // Obtiene las bajas médicas/sanciones activas de un equipo a fecha de hoy
    @Query("SELECT h FROM HistorialBaja h WHERE h.jugador.equipo.id = :equipoId AND " +
            "h.fechaInicio <= :fecha AND (h.fechaFin IS NULL OR h.fechaFin >= :fecha)")
    List<HistorialBaja> findBajasActivas(@Param("equipoId") Long equipoId, @Param("fecha") LocalDate fecha);
}