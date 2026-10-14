package estadisticas.services.equipos;

import estadisticas.dto.equipos.EquipoDTO;
import estadisticas.dto.equipos.TraspasoDTO;
import estadisticas.exception.NotFoundException;
import estadisticas.mappers.equipos.EquipoMapper;
import estadisticas.mappers.equipos.TraspasoMapper;
import estadisticas.repositories.equipos.EquipoRepository;
import estadisticas.repositories.equipos.TraspasoRepository;
import estadisticas.repositories.ligas.ClasificacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EquipoServiceImpl implements EquipoService {

    private final EquipoRepository equipoRepository;
    private final EquipoMapper equipoMapper;
    private final ClasificacionRepository clasificacionRepository;

    // Inyectamos el repositorio y el mapper de traspasos
    private final TraspasoRepository traspasoRepository;
    private final TraspasoMapper traspasoMapper;

    @Override
    @Transactional(readOnly = true)
    public EquipoDTO obtenerEquipoPorId(Integer idEquipo) {
        return equipoRepository.findById(idEquipo)
                .map(equipoMapper::toDto)
                .orElseThrow(() -> new NotFoundException("No se encontró el equipo con ID: " + idEquipo));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EquipoDTO> obtenerEquiposPorLiga(Integer idLiga, Integer temporada) {
        // Vía Clasificacion, no Equipo.liga -- ver el javadoc de la interfaz para el porqué.
        return clasificacionRepository.findByLiga_IdLigaAndTemporadaOrderByPosicionAsc(idLiga, temporada).stream()
                .map(clasificacion -> equipoMapper.toDto(clasificacion.getEquipo()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TraspasoDTO> obtenerTraspasosPorEquipo(Integer idEquipo) {
        // Busca donde el equipo sea el que vende (origen) o el que compra (destino)
        return traspasoRepository.findByEquipoOrderByFechaDesc(idEquipo)
                .stream()
                .map(traspasoMapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EquipoDTO> buscarPorNombre(String nombre, int limite) {
        return equipoRepository.findByNombreContainingIgnoreCase(nombre, PageRequest.of(0, limite))
                .map(equipoMapper::toDto)
                .toList();
    }
}