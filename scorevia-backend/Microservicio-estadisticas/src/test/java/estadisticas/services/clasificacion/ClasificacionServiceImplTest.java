package estadisticas.services.clasificacion;

import estadisticas.dto.equipos.EquipoDTO;
import estadisticas.dto.ligas.ClasificacionDTO;
import estadisticas.entities.ligas.Clasificacion;
import estadisticas.mappers.ligas.ClasificacionMapper;
import estadisticas.repositories.ligas.ClasificacionRepository;
import estadisticas.repositories.partidos.PartidoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClasificacionServiceImplTest {

    @Mock private ClasificacionRepository clasificacionRepository;
    @Mock private ClasificacionMapper clasificacionMapper;
    @Mock private PartidoRepository partidoRepository;

    @InjectMocks private ClasificacionServiceImpl clasificacionService;

    private ClasificacionDTO dtoConEquipo(Integer idEquipo) {
        ClasificacionDTO dto = new ClasificacionDTO();
        dto.setEquipo(EquipoDTO.builder().idEquipo(idEquipo).build());
        return dto;
    }

    /** Una fila tal como la devuelve la consulta: local, visitante, goles y goles. */
    private Object[] partido(int local, int visitante, int golesLocal, int golesVisitante) {
        return new Object[]{local, visitante, golesLocal, golesVisitante};
    }

    @Test
    void obtenerClasificacion_RetornaListaClasificacionDTO() {
        Clasificacion clasificacion = new Clasificacion();
        ClasificacionDTO dto = dtoConEquipo(10);

        when(clasificacionRepository.findByLiga_IdLigaAndTemporadaOrderByPosicionAsc(1, 2026))
                .thenReturn(List.of(clasificacion));
        when(clasificacionMapper.toDto(clasificacion)).thenReturn(dto);
        when(partidoRepository.findResultadosDeLigaYTemporada(1, 2026))
                .thenReturn(Collections.emptyList());

        List<ClasificacionDTO> resultado = clasificacionService.obtenerClasificacion(1, 2026);

        assertNotNull(resultado);
        assertEquals(1, resultado.size());
        verify(clasificacionRepository).findByLiga_IdLigaAndTemporadaOrderByPosicionAsc(1, 2026);
    }

    @Test
    void obtenerClasificacion_SinDatos_RetornaListaVacia() {
        when(clasificacionRepository.findByLiga_IdLigaAndTemporadaOrderByPosicionAsc(99, 2026))
                .thenReturn(Collections.emptyList());

        List<ClasificacionDTO> resultado = clasificacionService.obtenerClasificacion(99, 2026);

        assertNotNull(resultado);
        assertEquals(0, resultado.size());
    }

    @Test
    @DisplayName("La forma cuenta los partidos como local y como visitante, del mas antiguo al mas reciente")
    void obtenerClasificacion_CalculaFormaComoLocalYVisitante_MasAntiguoPrimero() {
        Clasificacion clasificacion = new Clasificacion();
        ClasificacionDTO dto = dtoConEquipo(10);

        when(clasificacionRepository.findByLiga_IdLigaAndTemporadaOrderByPosicionAsc(1, 2026))
                .thenReturn(List.of(clasificacion));
        when(clasificacionMapper.toDto(clasificacion)).thenReturn(dto);
        // Del mas antiguo al mas reciente, que es como los ordena la consulta:
        // empata en casa (E), gana fuera 3-1 (V), pierde en casa 1-2 (D).
        when(partidoRepository.findResultadosDeLigaYTemporada(1, 2026)).thenReturn(List.of(
                partido(10, 20, 0, 0),
                partido(20, 10, 1, 3),
                partido(10, 20, 1, 2)));

        List<ClasificacionDTO> resultado = clasificacionService.obtenerClasificacion(1, 2026);

        assertEquals(List.of("E", "V", "D"), resultado.get(0).getForma());
    }

    @Test
    @DisplayName("Se queda con los cinco ultimos, no con los primeros cinco")
    void obtenerClasificacion_SoloLosCincoUltimos() {
        Clasificacion clasificacion = new Clasificacion();
        ClasificacionDTO dto = dtoConEquipo(10);

        when(clasificacionRepository.findByLiga_IdLigaAndTemporadaOrderByPosicionAsc(1, 2026))
                .thenReturn(List.of(clasificacion));
        when(clasificacionMapper.toDto(clasificacion)).thenReturn(dto);
        // Siete partidos: dos derrotas al principio y luego cinco victorias. Si se quedara con
        // los primeros, la racha diria que el equipo va fatal cuando lleva cinco seguidas.
        when(partidoRepository.findResultadosDeLigaYTemporada(1, 2026)).thenReturn(List.of(
                partido(10, 20, 0, 1),
                partido(10, 20, 0, 2),
                partido(10, 20, 1, 0),
                partido(10, 20, 2, 0),
                partido(10, 20, 3, 0),
                partido(10, 20, 4, 0),
                partido(10, 20, 5, 0)));

        assertEquals(List.of("V", "V", "V", "V", "V"),
                clasificacionService.obtenerClasificacion(1, 2026).get(0).getForma());
    }

    @Test
    @DisplayName("Un equipo de la tabla que todavia no ha jugado sale sin racha, no rompe")
    void obtenerClasificacion_EquipoSinPartidos() {
        Clasificacion clasificacion = new Clasificacion();
        ClasificacionDTO dto = dtoConEquipo(77);

        when(clasificacionRepository.findByLiga_IdLigaAndTemporadaOrderByPosicionAsc(1, 2026))
                .thenReturn(List.of(clasificacion));
        when(clasificacionMapper.toDto(clasificacion)).thenReturn(dto);
        // Los partidos que hay son de otros dos equipos.
        when(partidoRepository.findResultadosDeLigaYTemporada(1, 2026))
                .thenReturn(List.<Object[]>of(partido(10, 20, 1, 0)));

        assertTrue(clasificacionService.obtenerClasificacion(1, 2026).get(0).getForma().isEmpty());
    }

    @Test
    @DisplayName("Una sola consulta para toda la tabla, no una por equipo")
    void obtenerClasificacion_UnaSolaConsultaDeForma() {
        // Es la razon de ser del cambio: con una consulta por equipo, una tabla de 20 tardaba
        // 11,4 segundos en cargar la primera vez.
        Clasificacion uno = new Clasificacion();
        Clasificacion dos = new Clasificacion();

        when(clasificacionRepository.findByLiga_IdLigaAndTemporadaOrderByPosicionAsc(1, 2026))
                .thenReturn(List.of(uno, dos));
        when(clasificacionMapper.toDto(uno)).thenReturn(dtoConEquipo(10));
        when(clasificacionMapper.toDto(dos)).thenReturn(dtoConEquipo(20));
        when(partidoRepository.findResultadosDeLigaYTemporada(1, 2026))
                .thenReturn(List.<Object[]>of(partido(10, 20, 1, 0)));

        clasificacionService.obtenerClasificacion(1, 2026);

        verify(partidoRepository).findResultadosDeLigaYTemporada(1, 2026);
    }
}
