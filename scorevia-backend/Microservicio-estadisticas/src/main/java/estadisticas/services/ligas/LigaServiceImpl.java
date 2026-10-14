package estadisticas.services.ligas;

import estadisticas.dto.ligas.LigaDTO;
import estadisticas.dto.ligas.TemporadaLigaDTO;
import estadisticas.exception.NotFoundException;
import estadisticas.mappers.ligas.LigaMapper;
import estadisticas.repositories.ligas.ClasificacionRepository;
import estadisticas.repositories.ligas.LigaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import estadisticas.repositories.partidos.PartidoRepository;

@Service
@RequiredArgsConstructor
public class LigaServiceImpl implements LigaService {

    private final LigaRepository ligaRepository;
    private final ClasificacionRepository clasificacionRepository;
    private final PartidoRepository partidoRepository;
    private final LigaMapper ligaMapper;

    @Override
    @Transactional(readOnly = true)
    public List<LigaDTO> obtenerTodasLigas() {
        return ligaRepository.findAll().stream()
                .map(ligaMapper::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LigaDTO obtenerLigaPorId(Integer idLiga) {
        return ligaRepository.findById(idLiga)
                .map(ligaMapper::toDto)
                .orElseThrow(() -> new NotFoundException("No se encontró la liga con ID: " + idLiga));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable("temporadasLiga")
    public List<TemporadaLigaDTO> obtenerTemporadasDisponibles(Integer idLiga) {
        return partidoRepository.resumenTemporadasPorLiga(idLiga).stream()
                .map(fila -> TemporadaLigaDTO.builder()
                        .temporada((Integer) fila[0])
                        // La API publica el calendario de la temporada siguiente mucho antes de
                        // que empiece: esas temporadas existen pero no tienen ni un resultado, y
                        // no deben abrirse por defecto -- solo poder elegirse a mano.
                        .conDatos(fila[1] != null && ((Number) fila[1]).longValue() > 0)
                        .build())
                .toList();
    }
}