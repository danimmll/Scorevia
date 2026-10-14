package estadisticas.services.jugadores;

import estadisticas.utils.TemporadaTexto;
import estadisticas.dto.jugadores.HistorialBajaDTO;
import estadisticas.dto.jugadores.HistorialDorsalDTO;
import estadisticas.dto.jugadores.JugadorDTO;
import estadisticas.dto.equipos.TraspasoDTO;
import estadisticas.dto.ligas.TrofeoDTO;
import estadisticas.entities.jugadores.Jugador;
import estadisticas.exception.NoContentException;
import estadisticas.exception.NotFoundException;
import estadisticas.mappers.jugadores.HistorialBajaMapper;
import estadisticas.mappers.jugadores.HistorialDorsalMapper;
import estadisticas.mappers.jugadores.JugadorMapper;
import estadisticas.mappers.equipos.TraspasoMapper;
import estadisticas.mappers.ligas.TrofeoMapper;
import estadisticas.repositories.jugadores.HistorialBajaRepository;
import estadisticas.repositories.jugadores.HistorialDorsalRepository;
import estadisticas.repositories.jugadores.JugadorRepository;
import estadisticas.repositories.equipos.TraspasoRepository;
import estadisticas.repositories.ligas.ClasificacionRepository;
import estadisticas.repositories.ligas.TrofeoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import estadisticas.services.ligas.BanderasPais;

@Service
@RequiredArgsConstructor
public class JugadorServiceImpl implements JugadorService {

    private final JugadorRepository jugadorRepository;
    private final BanderasPais banderasPais;
    private final JugadorMapper jugadorMapper;

    private final TrofeoRepository trofeoRepository;
    private final TrofeoMapper trofeoMapper;

    private final HistorialBajaRepository historialBajaRepository;
    private final HistorialBajaMapper historialBajaMapper;

    private final HistorialDorsalRepository historialDorsalRepository;
    private final HistorialDorsalMapper historialDorsalMapper;

    private final TraspasoRepository traspasoRepository;
    private final TraspasoMapper traspasoMapper;

    private final ClasificacionRepository clasificacionRepository;

    @Override
    @Transactional(readOnly = true)
    public JugadorDTO obtenerJugadorPorId(Integer idJugador) {
        return jugadorRepository.findById(idJugador)
                .map(jugadorMapper::toDto)
                .orElseThrow(() -> new NotFoundException("No se encontró el jugador con ID: " + idJugador));
    }

    @Override
    public Page<JugadorDTO> obtenerPlantillaPorEquipo(Integer idEquipo, Pageable pageable) {
        Page<Jugador> jugadores = jugadorRepository.findByEquipo_IdEquipo(idEquipo, pageable);
        if (jugadores.isEmpty()) {
            throw new NoContentException("No se encontraron jugadores para el equipo con ID: " + idEquipo);
        }
        return jugadores.map(jugadorMapper::toDto);
    }

    /**
     * Los titulos del jugador, del mas reciente al mas antiguo.
     *
     * <p>La consulta no ordenaba nada, asi que llegaban en el orden en que los devolviera la
     * base: un palmares es una linea del tiempo y salia barajado. Ordenar en SQL no vale
     * aqui, porque {@code temporada} es texto con dos formatos ("2023/2024" y "2023") y en
     * 108.788 filas de 560.212 esta vacia; un {@code ORDER BY} a secas pondria justo esas
     * primeras. Ver {@link estadisticas.utils.TemporadaTexto}.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public List<TrofeoDTO> obtenerTrofeosJugador(Integer idJugador) {
        return trofeoRepository.findByJugador_IdJugador(idJugador).stream()
                .map(trofeoMapper::toDto)
                // La bandera se pone aqui y no en el mapper porque sale de otra tabla: el
                // trofeo solo guarda el nombre del pais.
                .peek(dto -> dto.setBanderaPais(banderasPais.de(dto.getPais())))
                .sorted(TemporadaTexto.masRecientePrimero(TrofeoDTO::getTemporada))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<HistorialBajaDTO> obtenerHistorialBajasJugador(Integer idJugador) {
        return historialBajaRepository.findByJugador_IdJugadorOrderByFechaInicioDesc(idJugador).stream()
                .map(historialBajaMapper::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TraspasoDTO> obtenerTraspasosJugador(Integer idJugador) {
        return traspasoRepository.findByJugadorIdJugadorOrderByFechaDesc(idJugador).stream()
                .map(traspasoMapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<HistorialDorsalDTO> obtenerHistorialDorsalesJugador(Integer idJugador) {
        return historialDorsalRepository.findByJugador_IdJugadorOrderByTemporadaDesc(idJugador).stream()
                .map(historialDorsalMapper::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("java:S2245")
    public JugadorDTO obtenerJugadorAleatorioParaJuego() {
        long totalJugadores = jugadorRepository.countByEquipoIsNotNull();

        if (totalJugadores == 0) {
            throw new NotFoundException("No hay jugadores válidos para el juego");
        }

        int indiceAleatorio = ThreadLocalRandom.current().nextInt((int) totalJugadores);
        Page<Jugador> pagina = jugadorRepository.findByEquipoIsNotNull(
                PageRequest.of(indiceAleatorio, 1, Sort.by("idJugador")));

        // Ventana de carrera: totalJugadores se contó antes de elegir el índice; si algún
        // jugador se borró justo entre medias, esa página puede venir vacía. Sin esto,
        // getFirst() lanzaría NoSuchElementException sin manejar (500 crudo).
        if (pagina.getContent().isEmpty()) {
            throw new NotFoundException("No hay jugadores válidos para el juego");
        }

        Jugador jugador = pagina.getContent().getFirst();
        return jugadorMapper.toDto(jugador);
    }

    @Override
    public Page<JugadorDTO> getAllJugadores(Pageable pageable) {
        // Prioriza a los jugadores con club: con el orden natural de la tabla, la primera
        // página del listado eran fichas sueltas sin equipo creadas por el ingest de traspasos.
        Page<Jugador> jugadores = jugadorRepository.findTodosPriorizandoConEquipo(pageable);
        if (jugadores.isEmpty()) {
            throw new NoContentException("No hay jugadores registrados en el sistema.");
        }
        return jugadores.map(jugadorMapper::toDto);
    }

    @Override
    public Page<JugadorDTO> getJugadoresPorLiga(Integer idLiga, Integer temporada, Pageable pageable) {
        List<Integer> idEquipos = clasificacionRepository.findByLiga_IdLigaAndTemporadaOrderByPosicionAsc(idLiga, temporada)
                .stream()
                .map(c -> c.getEquipo().getIdEquipo())
                .toList();
        if (idEquipos.isEmpty()) {
            throw new NoContentException("No se encontraron jugadores para la liga con ID: " + idLiga);
        }
        Page<Jugador> jugadores = jugadorRepository.findByEquipo_IdEquipoIn(idEquipos, pageable);
        if (jugadores.isEmpty()) {
            throw new NoContentException("No se encontraron jugadores para la liga con ID: " + idLiga);
        }
        return jugadores.map(jugadorMapper::toDto);
    }

    @Override
    public Page<JugadorDTO> buscarPorNombre(String nombre, Pageable pageable) {
        return jugadorRepository.findByNombreContainingIgnoreCase(nombre, pageable).map(jugadorMapper::toDto);
    }
}