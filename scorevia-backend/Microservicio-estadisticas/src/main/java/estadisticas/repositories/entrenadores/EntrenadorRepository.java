package estadisticas.repositories.entrenadores;

import estadisticas.entities.entrenadores.Entrenador;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EntrenadorRepository extends JpaRepository<Entrenador, Integer> {

    List<Entrenador> findByEquipo_IdEquipo(Integer idEquipo);

    // Buscador por nombre para el autocompletado de técnicos.
    @EntityGraph(attributePaths = {"equipo"})
    Page<Entrenador> findByNombreContainingIgnoreCase(String nombre, Pageable pageable);

    @EntityGraph(attributePaths = {"equipo"})
    List<Entrenador> findByEquipo_IdEquipoIn(List<Integer> idEquipos);

    /**
     * Entrenadores a los que todavía no se les ha pedido el palmarés.
     *
     * <p>El orden por id es parte de la consulta a propósito: la ingesta la recorre paginada
     * y va escribiendo en las filas que lee, y un {@code findAll(PageRequest)} sin orden deja
     * a Postgres elegir, con lo que entre página y página se saltan y se repiten filas.</p>
     */
    @Query("select e from Entrenador e where e.ultimaSyncTrofeos is null order by e.idEntrenador")
    Page<Entrenador> findPendientesDeTrofeos(Pageable pageable);

    long countByUltimaSyncTrofeosIsNull();
}
