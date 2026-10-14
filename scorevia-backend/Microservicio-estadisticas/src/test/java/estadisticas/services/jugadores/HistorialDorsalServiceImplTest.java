package estadisticas.services.jugadores;

import estadisticas.dto.jugadores.HistorialDorsalDTO;
import estadisticas.entities.equipos.Equipo;
import estadisticas.entities.jugadores.HistorialDorsal;
import estadisticas.entities.jugadores.Jugador;
import estadisticas.mappers.jugadores.HistorialDorsalMapper;
import estadisticas.repositories.jugadores.HistorialDorsalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistorialDorsalServiceImplTest {

    @Mock
    private HistorialDorsalRepository historialDorsalRepository;

    @Mock
    private HistorialDorsalMapper historialDorsalMapper;

    @InjectMocks
    private HistorialDorsalServiceImpl historialDorsalService;

    @Test
    void obtenerHistorialPorJugador_DebeMapearYOrdenarPorTemporadaDesc() {
        Integer idJugador = 10;

        Equipo equipo = Equipo.builder().idEquipo(1).nombre("Real Madrid").build();
        Jugador jugador = Jugador.builder().idJugador(idJugador).nombre("Vinicius Jr").build();

        HistorialDorsal historialReciente = HistorialDorsal.builder()
                .idHistorial(2L).jugador(jugador).equipo(equipo).temporada(2025).dorsal(7).posicion("Delantero").build();
        HistorialDorsal historialAntiguo = HistorialDorsal.builder()
                .idHistorial(1L).jugador(jugador).equipo(equipo).temporada(2020).dorsal(20).posicion("Delantero").build();

        HistorialDorsalDTO dtoReciente = HistorialDorsalDTO.builder().idEquipo(1).temporada(2025).dorsal(7).posicion("Delantero").build();
        HistorialDorsalDTO dtoAntiguo = HistorialDorsalDTO.builder().idEquipo(1).temporada(2020).dorsal(20).posicion("Delantero").build();

        when(historialDorsalRepository.findByJugador_IdJugadorOrderByTemporadaDesc(idJugador))
                .thenReturn(List.of(historialReciente, historialAntiguo));
        when(historialDorsalMapper.toDto(historialReciente)).thenReturn(dtoReciente);
        when(historialDorsalMapper.toDto(historialAntiguo)).thenReturn(dtoAntiguo);

        List<HistorialDorsalDTO> resultado = historialDorsalService.obtenerHistorialPorJugador(idJugador);

        assertEquals(2, resultado.size());
        assertEquals(2025, resultado.get(0).getTemporada());
        assertEquals(2020, resultado.get(1).getTemporada());
    }

    @Test
    void obtenerHistorialPorJugador_SinHistorial_DevuelveListaVacia() {
        Integer idJugador = 99;
        when(historialDorsalRepository.findByJugador_IdJugadorOrderByTemporadaDesc(idJugador))
                .thenReturn(List.of());

        List<HistorialDorsalDTO> resultado = historialDorsalService.obtenerHistorialPorJugador(idJugador);

        assertTrue(resultado.isEmpty());
    }
}
