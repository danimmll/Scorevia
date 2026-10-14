package estadisticas.repositories.ligas;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import estadisticas.entities.ligas.Liga;

@Repository
public interface LigaRepository extends JpaRepository<Liga, Integer> {

    /** Pares (pais, bandera) para el catalogo de {@code BanderasPais}. */
    @Query("SELECT DISTINCT l.pais, l.bandera FROM Liga l WHERE l.pais IS NOT NULL AND l.bandera IS NOT NULL")
    List<Object[]> findPaisesConBandera();
}
