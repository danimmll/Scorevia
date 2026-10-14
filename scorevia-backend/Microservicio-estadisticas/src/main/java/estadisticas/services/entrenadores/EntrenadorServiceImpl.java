package estadisticas.services.entrenadores;

import estadisticas.utils.TemporadaTexto;
import estadisticas.dto.entrenadores.EntrenadorDTO;
import estadisticas.dto.entrenadores.HistorialEntrenadorDTO;
import estadisticas.dto.ligas.TrofeoDTO;
import estadisticas.exception.NotFoundException;
import estadisticas.mappers.entrenadores.EntrenadorMapper;
import estadisticas.mappers.entrenadores.HistorialEntrenadorMapper;
import estadisticas.mappers.ligas.TrofeoMapper;
import estadisticas.repositories.entrenadores.EntrenadorRepository;
import estadisticas.repositories.entrenadores.HistorialEntrenadorRepository;
import estadisticas.repositories.ligas.ClasificacionRepository;
import estadisticas.repositories.ligas.TrofeoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import estadisticas.services.ligas.BanderasPais;

@Service
@RequiredArgsConstructor
public class EntrenadorServiceImpl implements EntrenadorService {

    private final EntrenadorRepository entrenadorRepository;
    private final BanderasPais banderasPais;
    private final EntrenadorMapper entrenadorMapper;

    private final TrofeoRepository trofeoRepository;
    private final TrofeoMapper trofeoMapper;

    private final ClasificacionRepository clasificacionRepository;

    private final HistorialEntrenadorRepository historialEntrenadorRepository;
    private final HistorialEntrenadorMapper historialEntrenadorMapper;

    @Override
    @Transactional(readOnly = true)
    public EntrenadorDTO obtenerEntrenadorPorId(Integer idEntrenador) {
        return entrenadorRepository.findById(idEntrenador)
                .map(entrenadorMapper::toDto)
                .orElseThrow(() -> new NotFoundException("No se encontró el entrenador con ID: " + idEntrenador));
    }

    @Override
    @Transactional(readOnly = true)
    public EntrenadorDTO obtenerEntrenadorPorEquipo(Integer idEquipo) {
        // Usamos stream().findFirst() en caso de que la query devuelva una lista por historial,
        // aunque normalmente un equipo tiene 1 entrenador activo a la vez.
        return entrenadorRepository.findByEquipo_IdEquipo(idEquipo).stream()
                .findFirst()
                .map(entrenadorMapper::toDto)
                .orElseThrow(() -> new NotFoundException("No se encontró un entrenador activo para el equipo con ID: " + idEquipo));
    }

    /** Los titulos del entrenador, del mas reciente al mas antiguo (ver JugadorServiceImpl). */
    @Override
    @Transactional(readOnly = true)
    public List<TrofeoDTO> obtenerTrofeosEntrenador(Integer idEntrenador) {
        return trofeoRepository.findByEntrenador_IdEntrenador(idEntrenador).stream()
                .map(trofeoMapper::toDto)
                // La bandera se pone aqui y no en el mapper porque sale de otra tabla: el
                // trofeo solo guarda el nombre del pais.
                .peek(dto -> dto.setBanderaPais(banderasPais.de(dto.getPais())))
                .sorted(TemporadaTexto.masRecientePrimero(TrofeoDTO::getTemporada))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EntrenadorDTO> obtenerEntrenadoresPorLiga(Integer idLiga, Integer temporada) {
        List<Integer> idEquipos = clasificacionRepository.findByLiga_IdLigaAndTemporadaOrderByPosicionAsc(idLiga, temporada)
                .stream()
                .map(c -> c.getEquipo().getIdEquipo())
                .toList();
        if (idEquipos.isEmpty()) return List.of();
        return entrenadorRepository.findByEquipo_IdEquipoIn(idEquipos).stream()
                .map(entrenadorMapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<HistorialEntrenadorDTO> obtenerHistorialClubes(Integer idEntrenador) {
        return historialEntrenadorRepository.findByEntrenador_IdEntrenadorOrderByTemporadaDesc(idEntrenador).stream()
                .map(historialEntrenadorMapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<HistorialEntrenadorDTO> obtenerHistorialPorEquipo(Integer idEquipo) {
        return historialEntrenadorRepository.findByEquipo_IdEquipoOrderByTemporadaDesc(idEquipo).stream()
                .map(historialEntrenadorMapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EntrenadorDTO> buscarPorNombre(String nombre, int limite) {
        if (nombre == null || nombre.isBlank()) return List.of();
        return entrenadorRepository
                .findByNombreContainingIgnoreCase(nombre.trim(), PageRequest.of(0, limite))
                .map(entrenadorMapper::toDto)
                .toList();
    }
}