package estadisticas.repositories.partidos;

import estadisticas.entities.partidos.EventoPartido;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventoPartidoRepository extends JpaRepository<EventoPartido, Long> {

    // Extrae los eventos del partido resolviendo el problema N+1 con JOIN FETCH
    @Query("SELECT e FROM EventoPartido e " +
            "JOIN FETCH e.equipo " +
            "LEFT JOIN FETCH e.jugadorPrincipal " +
            "LEFT JOIN FETCH e.jugadorSecundario " +
            "WHERE e.partido.idPartido = :idPartido " +
            "ORDER BY e.tiempoMinuto ASC")
    List<EventoPartido> findEventosByPartidoConDetalles(@Param("idPartido") Integer idPartido);

    void deleteByPartido_IdPartido(Integer idPartido);

}