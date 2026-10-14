package estadisticas.repositories.ligas;

import estadisticas.entities.ligas.Clasificacion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClasificacionRepository extends JpaRepository<Clasificacion, Integer> {

    @EntityGraph(attributePaths = {"liga", "equipo"})
    List<Clasificacion> findByLiga_IdLigaAndTemporadaOrderByPosicionAsc(Integer idLiga, Integer temporada);

    Optional<Clasificacion> findByLiga_IdLigaAndEquipo_IdEquipoAndTemporada(Integer idLiga, Integer idEquipo, Integer temporada);

    /**
     * Temporadas de las que tenemos clasificación para una liga, de la más reciente a la más
     * antigua, junto con los partidos ya disputados en cada una.
     *
     * <p>Existe porque {@code Liga.temporadaActual} no basta para decidir qué temporada abrir
     * por defecto: unas veces apunta a una temporada que aún no ha empezado (tabla entera a
     * cero, 111 competiciones estaban así) y hasta la reparación de datos apuntaba a 2010 en
     * las grandes ligas. Con esto el cliente puede elegir la última temporada con datos DE
     * VERDAD y, de paso, ofrecer un selector de temporada real en vez de un año a mano.</p>
     */
    @Query("SELECT c.temporada, SUM(COALESCE(c.partidosJugados, 0)) FROM Clasificacion c "
            + "WHERE c.liga.idLiga = :idLiga GROUP BY c.temporada ORDER BY c.temporada DESC")
    List<Object[]> resumenTemporadasPorLiga(@Param("idLiga") Integer idLiga);

    /**
     * Como acabo un equipo en cada competicion que jugo esa temporada.
     *
     * <p>Es de donde salen los titulos de la pantalla de temporada: un {@code posicion = 1} en
     * la clasificacion final es un campeonato ganado. La tabla {@code trofeos} no vale aqui
     * porque cuelga de jugadores y entrenadores, no de equipos.</p>
     */
    @EntityGraph(attributePaths = {"liga"})
    List<Clasificacion> findByEquipo_IdEquipoAndTemporadaOrderByPosicionAsc(Integer idEquipo, Integer temporada);

    /**
     * Los clubes que juegan esas competiciones, desde esa temporada.
     *
     * <p>Esta es la relacion club-competicion de verdad. {@code equipos.id_liga} NO sirve: es
     * una FK unica que la ultima ingesta se pisa, y el 2026-09-09 el Real Madrid figuraba en
     * "International Champions Cup" y el Manchester United en "Friendlies Clubs". Ver
     * {@code ClubesDestacados}.</p>
     */
    @Query("SELECT DISTINCT c.equipo.idEquipo FROM Clasificacion c "
            + "WHERE c.liga.idLiga IN :idLigas AND c.temporada >= :temporadaMinima")
    List<Integer> idsEquiposDeLigasDesde(@Param("idLigas") List<Integer> idLigas,
                                         @Param("temporadaMinima") int temporadaMinima);
}
