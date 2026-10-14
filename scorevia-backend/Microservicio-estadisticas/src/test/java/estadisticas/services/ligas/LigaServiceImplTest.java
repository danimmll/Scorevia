package estadisticas.services.ligas;

import estadisticas.dto.ligas.LigaDTO;
import estadisticas.dto.ligas.TemporadaLigaDTO;
import estadisticas.entities.ligas.Liga;
import estadisticas.exception.NotFoundException;
import estadisticas.mappers.ligas.LigaMapper;
import estadisticas.repositories.ligas.ClasificacionRepository;
import estadisticas.repositories.ligas.LigaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import estadisticas.repositories.partidos.PartidoRepository;

@ExtendWith(MockitoExtension.class)
class LigaServiceImplTest {

    @Mock private LigaRepository ligaRepository;
    @Mock private LigaMapper ligaMapper;
    @Mock private ClasificacionRepository clasificacionRepository;
    @Mock private PartidoRepository partidoRepository;

    @InjectMocks private LigaServiceImpl ligaService;

    @Test
    void obtenerTodasLigas_RetornaLista() {
        Liga liga = new Liga();
        LigaDTO dto = new LigaDTO();

        when(ligaRepository.findAll()).thenReturn(List.of(liga));
        when(ligaMapper.toDto(liga)).thenReturn(dto);

        List<LigaDTO> resultado = ligaService.obtenerTodasLigas();

        assertNotNull(resultado);
        assertEquals(1, resultado.size());
        verify(ligaRepository).findAll();
    }

    @Test
    void obtenerLigaPorId_Existe_RetornaDTO() {
        Liga liga = new Liga();
        LigaDTO dto = new LigaDTO();

        when(ligaRepository.findById(1)).thenReturn(Optional.of(liga));
        when(ligaMapper.toDto(liga)).thenReturn(dto);

        LigaDTO resultado = ligaService.obtenerLigaPorId(1);

        assertNotNull(resultado);
        verify(ligaRepository).findById(1);
    }

    @Test
    void obtenerLigaPorId_NoExiste_LanzaException() {
        when(ligaRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> ligaService.obtenerLigaPorId(99));
    }

    @Test
    void obtenerTemporadasDisponibles_marcaComoSinDatosLaTemporadaAunNoJugada() {
        // Caso real: la API publica la clasificación de la temporada siguiente en cuanto hay
        // calendario, con todos los equipos a cero partidos. El cliente necesita distinguirla
        // para no abrir la web en una tabla vacía.
        when(partidoRepository.resumenTemporadasPorLiga(140)).thenReturn(List.of(
                new Object[]{2026, 0L},
                new Object[]{2025, 760L}
        ));

        List<TemporadaLigaDTO> temporadas = ligaService.obtenerTemporadasDisponibles(140);

        assertEquals(2, temporadas.size());
        assertEquals(2026, temporadas.getFirst().getTemporada());
        assertFalse(temporadas.getFirst().isConDatos());
        assertEquals(2025, temporadas.get(1).getTemporada());
        assertTrue(temporadas.get(1).isConDatos());
    }

    @Test
    void obtenerTemporadasDisponibles_ligaSinClasificaciones_devuelveListaVacia() {
        when(partidoRepository.resumenTemporadasPorLiga(999)).thenReturn(List.of());

        assertTrue(ligaService.obtenerTemporadasDisponibles(999).isEmpty());
    }
}