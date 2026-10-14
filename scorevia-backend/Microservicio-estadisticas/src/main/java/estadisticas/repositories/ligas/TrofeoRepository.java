package estadisticas.repositories.ligas;

import estadisticas.entities.ligas.Trofeo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrofeoRepository extends JpaRepository<Trofeo, Long> {

    // FIX: la versión derivada (existsBy...AndTemporadaAnd...) comparaba "temporada = ?"
    // directo -- cuando la API no trae temporada para un trofeo, unas veces llega como null y
    // otras como cadena vacía según la respuesta, así que la misma fila lógica no siempre se
    // reconocía como "ya existe" y se duplicaba en cada resync (mismo patrón que el bug de
    // traspasos). COALESCE trata null y "" como equivalentes en la comparación.
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM Trofeo t " +
            "WHERE t.jugador.idJugador = :idJugador AND t.competicion = :competicion " +
            "AND t.resultado = :resultado AND t.pais = :pais " +
            "AND COALESCE(t.temporada, '') = COALESCE(:temporada, '')")
    boolean existeTrofeoJugador(
            @Param("idJugador") Integer idJugador, @Param("competicion") String competicion,
            @Param("temporada") String temporada, @Param("resultado") String resultado, @Param("pais") String pais);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM Trofeo t " +
            "WHERE t.entrenador.idEntrenador = :idEntrenador AND t.competicion = :competicion " +
            "AND t.resultado = :resultado AND t.pais = :pais " +
            "AND COALESCE(t.temporada, '') = COALESCE(:temporada, '')")
    boolean existeTrofeoEntrenador(
            @Param("idEntrenador") Integer idEntrenador, @Param("competicion") String competicion,
            @Param("temporada") String temporada, @Param("resultado") String resultado, @Param("pais") String pais);

    @EntityGraph(attributePaths = {"jugador"})
    List<Trofeo> findByJugador_IdJugador(Integer idJugador);

    @EntityGraph(attributePaths = {"entrenador"})
    List<Trofeo> findByEntrenador_IdEntrenador(Integer idEntrenador);

}