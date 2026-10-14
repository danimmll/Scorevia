package estadisticas.repositories.equipos;

import estadisticas.entities.equipos.Estadio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EstadioRepository extends JpaRepository<Estadio, Integer> {
}