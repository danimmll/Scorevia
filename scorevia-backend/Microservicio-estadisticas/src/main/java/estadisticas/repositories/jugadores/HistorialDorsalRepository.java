package estadisticas.repositories.jugadores;

import estadisticas.entities.jugadores.HistorialDorsal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HistorialDorsalRepository extends JpaRepository<HistorialDorsal, Long> {

    List<HistorialDorsal> findByJugador_IdJugadorOrderByTemporadaDesc(Integer idJugador);

}