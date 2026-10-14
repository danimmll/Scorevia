package estadisticas.repositories.jugadores;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import estadisticas.entities.jugadores.Jugador;

@Repository
public interface JugadorRepository extends JpaRepository<Jugador, Integer> {

    long countByEquipoIsNotNull();

    Page<Jugador> findByEquipoIsNotNull(Pageable pageable);

        // NUEVO METODO PAGINADO PARA LA VISTA PRINCIPAL
        @EntityGraph(attributePaths = {"equipo"})
        Page<Jugador> findByEquipo_IdEquipo(Integer idEquipo, Pageable pageable);

    /**
     * Desvincula del equipo a los jugadores que ya no estan en su plantilla.
     *
     * <p>`jugador.equipo` se usa como "equipo actual", pero solo se ASIGNABA: nada lo quitaba
     * nunca. Asi que la plantilla de un club era todo el que hubiera pasado por el desde que
     * existe la base. Medido: Toni Kroos seguia en el Real Madrid con
     * `fecha_equipo_actualizado` del 2024-05-08, que es su ultimo partido antes de retirarse,
     * y el Madrid figuraba con 46 jugadores.</p>
     */
    // El @Transactional NO es decorativo: sin el, esta consulta lanzaba
    // "No active transaction for update or delete query" en TODAS las llamadas. Quien la
    // invoca es ingestarJugadoresCompletosPorEquipo, que no es transaccional a proposito
    // (es un bucle de horas sobre 11.508 equipos y una transaccion asi no se sostiene), y
    // procesarPlantillaSeguro se traga la excepcion en su catch generico y sigue -- asi que
    // el fallo se veia como una linea de ERROR por equipo, el equipo se marcaba como
    // sincronizado igualmente, y el arreglo de "soltar a los que ya no estan" no llego a
    // ejecutarse ni una sola vez desde que se escribio. Kroos seguia en el Madrid.
    @Modifying
    @Transactional
    @Query("UPDATE Jugador j SET j.equipo = NULL WHERE j.equipo.idEquipo = :idEquipo "
            + "AND j.idJugador NOT IN :idsQueSiguen")
    int desvincularLosQueYaNoEstan(@Param("idEquipo") Integer idEquipo,
                                   @Param("idsQueSiguen") List<Integer> idsQueSiguen);

        // Vía Clasificacion (equipo+liga+temporada), no vía "Equipo.liga" -- mismo motivo que
        // EquipoServiceImpl.obtenerEquiposPorLiga: un equipo puede jugar liga + Champions + copa
        // a la vez, y Equipo.liga es una FK única que la última ingesta se pisa.
        @EntityGraph(attributePaths = {"equipo"})
        Page<Jugador> findByEquipo_IdEquipoIn(List<Integer> idEquipos, Pageable pageable);

        long countByEquipo_IdEquipoIn(List<Integer> idEquipos);

        @EntityGraph(attributePaths = {"equipo"})
        Page<Jugador> findByNombreContainingIgnoreCase(String nombre, Pageable pageable);

        @EntityGraph(attributePaths = {"equipo"})
        @NonNull
        Page<Jugador> findAll(@NonNull Pageable pageable);

    @Query("SELECT j FROM Jugador j WHERE j.perfilVerificado = false OR j.perfilVerificado IS NULL")
    Page<Jugador> findJugadoresPendientesDePerfil(Pageable pageable);

    /**
     * Listado general de jugadores, con los que tienen club por delante.
     *
     * El 91% de las fichas de la tabla las creó el ingest de traspasos y no tienen equipo ni
     * apenas datos: con un findAll() sin orden, la página de Jugadores abría con una pantalla
     * de nombres de sextas divisiones sin equipo ni foto. Aquí salen primero los que tienen
     * club, y el orden por id (estable) evita que la paginación baile entre peticiones.
     */
    @EntityGraph(attributePaths = {"equipo"})
    @Query("SELECT j FROM Jugador j ORDER BY CASE WHEN j.equipo IS NULL THEN 1 ELSE 0 END, j.idJugador")
    Page<Jugador> findTodosPriorizandoConEquipo(Pageable pageable);

}
