package estadisticas.repositories.entrenadores;

import estadisticas.entities.entrenadores.HistorialEntrenador;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HistorialEntrenadorRepository extends JpaRepository<HistorialEntrenador, Long> {

    @EntityGraph(attributePaths = {"equipo"})
    List<HistorialEntrenador> findByEntrenador_IdEntrenadorOrderByTemporadaDesc(Integer idEntrenador);

    @EntityGraph(attributePaths = {"entrenador"})
    List<HistorialEntrenador> findByEquipo_IdEquipoOrderByTemporadaDesc(Integer idEquipo);

    // Quién dirigía a este equipo en esta temporada concreta. Lo usa la alineación de un
    // partido: sin esto habría que enseñar al entrenador ACTUAL del club, y en un partido
    // antiguo eso es sencillamente falso.
    List<HistorialEntrenador> findByEquipo_IdEquipoAndTemporada(Integer idEquipo, Integer temporada);

    Optional<HistorialEntrenador> findByEntrenador_IdEntrenadorAndEquipo_IdEquipoAndTemporada(
            Integer idEntrenador, Integer idEquipo, Integer temporada);

    /**
     * Las etapas de entrenador de un equipo hasta una temporada, de la mas reciente hacia atras.
     *
     * <p>Hace falta porque el historial guarda <b>la temporada en que empieza</b> cada etapa, no
     * todas las que cubre. Comprobado con el Real Madrid: hay fila de 2021 (Ancelotti) y la
     * siguiente es de 2025, asi que preguntar por 2024 exacto no devuelve nada aunque Ancelotti
     * siguiera dirigiendo. Con esto se coge la etapa vigente en esa temporada, que es la de
     * temporada mas alta de las que no la superan.</p>
     */
    @EntityGraph(attributePaths = {"entrenador"})
    List<HistorialEntrenador> findByEquipo_IdEquipoAndTemporadaLessThanEqualOrderByTemporadaDesc(
            Integer idEquipo, Integer temporada);
}
