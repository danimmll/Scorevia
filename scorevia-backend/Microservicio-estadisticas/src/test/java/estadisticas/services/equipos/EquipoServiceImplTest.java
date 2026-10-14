package estadisticas.services.equipos;

import estadisticas.dto.equipos.EquipoDTO;
import estadisticas.dto.equipos.TraspasoDTO;
import estadisticas.entities.equipos.Equipo;
import estadisticas.entities.equipos.Traspaso;
import estadisticas.entities.ligas.Clasificacion;
import estadisticas.exception.NotFoundException;
import estadisticas.mappers.equipos.EquipoMapper;
import estadisticas.mappers.equipos.TraspasoMapper;
import estadisticas.repositories.equipos.EquipoRepository;
import estadisticas.repositories.equipos.TraspasoRepository;
import estadisticas.repositories.ligas.ClasificacionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquipoServiceImplTest {

    @Mock private EquipoRepository equipoRepository;
    @Mock private EquipoMapper equipoMapper;
    @Mock private ClasificacionRepository clasificacionRepository;
    @Mock private TraspasoRepository traspasoRepository;
    @Mock private TraspasoMapper traspasoMapper;

    @InjectMocks private EquipoServiceImpl equipoService;

    @Test
    void obtenerEquipoPorId_Existe_RetornaDTO() {
        Equipo equipo = new Equipo();
        EquipoDTO dto = new EquipoDTO();

        when(equipoRepository.findById(1)).thenReturn(Optional.of(equipo));
        when(equipoMapper.toDto(equipo)).thenReturn(dto);

        EquipoDTO resultado = equipoService.obtenerEquipoPorId(1);

        assertNotNull(resultado);
        verify(equipoRepository).findById(1);
    }

    @Test
    void obtenerEquipoPorId_NoExiste_LanzaException() {
        when(equipoRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> equipoService.obtenerEquipoPorId(99));
    }

    @Test
    void obtenerEquiposPorLiga_ResuelveViaClasificacion_RetornaLista() {
        // Vía Clasificacion, no Equipo.liga -- ver el javadoc de EquipoService para el
        // porqué (un equipo puede jugar liga + Champions + copa a la vez, y Equipo.liga es
        // una FK única que la última ingesta se pisa).
        Equipo equipo = new Equipo();
        EquipoDTO dto = new EquipoDTO();
        Clasificacion clasificacion = new Clasificacion();
        clasificacion.setEquipo(equipo);

        when(clasificacionRepository.findByLiga_IdLigaAndTemporadaOrderByPosicionAsc(5, 2026))
                .thenReturn(List.of(clasificacion));
        when(equipoMapper.toDto(equipo)).thenReturn(dto);

        List<EquipoDTO> resultado = equipoService.obtenerEquiposPorLiga(5, 2026);

        assertNotNull(resultado);
        assertEquals(1, resultado.size());
    }

    @Test
    void obtenerTraspasosPorEquipo_RetornaLista() {
        Traspaso traspaso = new Traspaso();
        TraspasoDTO dto = new TraspasoDTO();

        when(traspasoRepository.findByEquipoOrderByFechaDesc(1))
                .thenReturn(List.of(traspaso));
        when(traspasoMapper.toDto(traspaso)).thenReturn(dto);

        List<TraspasoDTO> resultado = equipoService.obtenerTraspasosPorEquipo(1);

        assertNotNull(resultado);
        assertEquals(1, resultado.size());
    }
}