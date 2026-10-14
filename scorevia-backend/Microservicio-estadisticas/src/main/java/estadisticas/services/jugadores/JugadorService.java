package estadisticas.services.jugadores;

import estadisticas.dto.jugadores.HistorialBajaDTO;
import estadisticas.dto.jugadores.HistorialDorsalDTO;
import estadisticas.dto.jugadores.JugadorDTO;
import estadisticas.dto.equipos.TraspasoDTO;
import estadisticas.dto.ligas.TrofeoDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Servicio para la gestión de Jugadores, historial de bajas (lesiones/sanciones)
 * y palmarés de trofeos.
 *
 * @author Daniel
 * @version 1.0
 */
public interface JugadorService {

    JugadorDTO obtenerJugadorPorId(Integer idJugador);

    Page<JugadorDTO> obtenerPlantillaPorEquipo(Integer idEquipo, Pageable pageable);

    List<TrofeoDTO> obtenerTrofeosJugador(Integer idJugador);

    List<HistorialBajaDTO> obtenerHistorialBajasJugador(Integer idJugador);

    List<TraspasoDTO> obtenerTraspasosJugador(Integer idJugador);

    /**
     * Recupera la trayectoria de dorsales y posiciones de un jugador a lo largo
     * de las temporadas, agrupado por equipo (fuente: backfill dorsales_posicion.py).
     * @param idJugador ID del jugador.
     * @return Lista de registros de historial de dorsales, ordenados de temporada más reciente a más antigua.
     */
    List<HistorialDorsalDTO> obtenerHistorialDorsalesJugador(Integer idJugador);

    JugadorDTO obtenerJugadorAleatorioParaJuego();

    Page<JugadorDTO> getAllJugadores(Pageable pageable);

    /**
     * Vía Clasificacion (equipo+liga+temporada), no vía "Equipo.liga" -- ver el javadoc de
     * EquipoService.obtenerEquiposPorLiga para el porqué. Requiere la temporada porque un
     * mismo equipo puede estar en varias competiciones a la vez y esa FK es única.
     */
    Page<JugadorDTO> getJugadoresPorLiga(Integer idLiga, Integer temporada, Pageable pageable);

    Page<JugadorDTO> buscarPorNombre(String nombre, Pageable pageable);
}