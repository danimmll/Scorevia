package estadisticas.repositories.equipos;

import estadisticas.entities.jugadores.Jugador;
import estadisticas.entities.equipos.Traspaso;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TraspasoRepository extends JpaRepository<Traspaso, Long> {

    // Más reciente primero -- sin ORDER BY, "traspasos recientes" no era ni recientes ni
    // ordenados, salía en el orden físico en el que Postgres los devolviera.
    @EntityGraph(attributePaths = {"jugador", "equipoOrigen", "equipoDestino"})
    List<Traspaso> findByJugadorIdJugadorOrderByFechaDesc(Integer idJugador);

    // FIX: la versión anterior navegaba con "t.equipoDestino.idEquipo = :id OR
    // t.equipoOrigen.idEquipo = :id". En JPQL esa navegación genera un INNER JOIN implícito
    // POR CADA lado, así que un traspaso sin equipo de origen (agente libre) o sin destino
    // (fin de contrato) quedaba fuera del resultado aunque el equipo buscado fuera el otro
    // extremo -- en la ficha del Real Madrid no salían las salidas a coste cero. Comparando
    // las claves ajenas con LEFT JOIN explícito entran también esas filas.
    @EntityGraph(attributePaths = {"jugador", "equipoOrigen", "equipoDestino"})
    @Query("SELECT t FROM Traspaso t LEFT JOIN t.equipoOrigen o LEFT JOIN t.equipoDestino d "
            + "WHERE o.idEquipo = :idEquipo OR d.idEquipo = :idEquipo ORDER BY t.fecha DESC")
    List<Traspaso> findByEquipoOrderByFechaDesc(@org.springframework.data.repository.query.Param("idEquipo") Integer idEquipo);

    /**
     * Todos los traspasos que ya hay guardados de ese jugador en esa fecha.
     *
     * <p><b>Por que la fecha y no la clave entera.</b> La busqueda anterior exigia que
     * coincidieran ademas el equipo de origen y el de destino, y eso seguia duplicando filas
     * por un motivo que no se veia: {@code resolverEquipo} devuelve null cuando el club no
     * esta todavia en nuestra tabla de equipos. La MISMA operacion se ingiere dos veces —una
     * desde el endpoint del club que vende y otra desde el del que compra— y si la primera vez
     * el otro club aun no existia, esa fila quedo con el origen a null y la segunda entro
     * entera. Dos filas, la misma operacion. Medido: <b>19.313 casos</b> por el origen y
     * <b>11.423</b> por el destino.</p>
     *
     * <p>Trayendo las del dia se puede decidir en Java si son la misma operacion (ver
     * {@code esLaMismaOperacion}) y, sobre todo, <b>rellenar el hueco</b> en vez de insertar
     * al lado.</p>
     */
    List<Traspaso> findByJugadorAndFecha(Jugador jugador, LocalDate fecha);

}