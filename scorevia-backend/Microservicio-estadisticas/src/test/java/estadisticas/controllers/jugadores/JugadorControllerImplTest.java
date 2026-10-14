package estadisticas.controllers.jugadores;

import estadisticas.dto.equipos.TraspasoDTO;
import estadisticas.dto.jugadores.HistorialBajaDTO;
import estadisticas.dto.jugadores.JugadorDTO;
import estadisticas.dto.ligas.TrofeoDTO;
import estadisticas.services.jugadores.JugadorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class JugadorControllerImplTest {

    @Mock
    private JugadorService jugadorService;

    @InjectMocks
    private JugadorControllerImpl jugadorController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(jugadorController)
                // Fundamental para que resuelva los @Pageable en los controllers
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void obtenerPerfil_DebeRetornarJugador() throws Exception {
        Integer idJugador = 7;
        JugadorDTO dto = new JugadorDTO();
        dto.setNombre("Vinicius Jr");

        when(jugadorService.obtenerJugadorPorId(idJugador)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/jugadores/{id}", "7")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Vinicius Jr"));

        verify(jugadorService).obtenerJugadorPorId(idJugador);
    }

    @Test
    void obtenerPlantilla_DebeRetornarPaginaDeJugadores() throws Exception {
        Integer idEquipo = 541;
        JugadorDTO dto = new JugadorDTO();
        dto.setNombre("Vinicius Jr");

        // FIX: Usar PageRequest válido para que Jackson no lance UnsupportedOperationException
        Page<JugadorDTO> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1);

        when(jugadorService.obtenerPlantillaPorEquipo(eq(idEquipo), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/jugadores/equipo/{idEquipo}", idEquipo)
                        .param("page", "0")
                        .param("size", "20")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nombre").value("Vinicius Jr"));

        verify(jugadorService).obtenerPlantillaPorEquipo(eq(idEquipo), any(Pageable.class));
    }

    @Test
    void obtenerTrofeos_DebeRetornarListaDeTrofeos() throws Exception {
        Integer idJugador = 7;
        TrofeoDTO dto = new TrofeoDTO();
        dto.setCompeticion("Champions League");

        when(jugadorService.obtenerTrofeosJugador(idJugador)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/jugadores/{id}/trofeos", "7")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].competicion").value("Champions League"));

        verify(jugadorService).obtenerTrofeosJugador(idJugador);
    }

    @Test
    void obtenerBajas_DebeRetornarListaDeBajas() throws Exception {
        Integer idJugador = 7;
        HistorialBajaDTO dto = new HistorialBajaDTO();
        dto.setTipo("Lesión muscular");

        when(jugadorService.obtenerHistorialBajasJugador(idJugador)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/jugadores/{id}/bajas", "7")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("Lesión muscular"));

        verify(jugadorService).obtenerHistorialBajasJugador(idJugador);
    }

    @Test
    void obtenerTraspasos_DebeRetornarListaDeTraspasos() throws Exception {
        Integer idJugador = 7;
        TraspasoDTO dto = new TraspasoDTO();
        dto.setTipo("Fichaje");

        when(jugadorService.obtenerTraspasosJugador(idJugador)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/jugadores/{id}/traspasos", "7")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("Fichaje"));

        verify(jugadorService).obtenerTraspasosJugador(idJugador);
    }

    @Test
    void getJugadoresPorLiga_DebeRetornarPaginaDeJugadores() throws Exception {
        Integer idLiga = 140;
        Integer temporada = 2026;
        JugadorDTO dto = new JugadorDTO();
        dto.setNombre("Vinicius Jr");

        // FIX: Usar PageRequest válido
        Page<JugadorDTO> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1);

        when(jugadorService.getJugadoresPorLiga(eq(idLiga), eq(temporada), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/jugadores/liga/{idLiga}", idLiga)
                        .param("temporada", "2026")
                        .param("page", "0")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nombre").value("Vinicius Jr"));

        verify(jugadorService).getJugadoresPorLiga(eq(idLiga), eq(temporada), any(Pageable.class));
    }

    @Test
    void getAllJugadores_DebeRetornarPaginaGeneralDeJugadores() throws Exception {
        JugadorDTO dto = new JugadorDTO();
        dto.setNombre("Jugador Random");

        // FIX: Usar PageRequest válido
        Page<JugadorDTO> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1);

        when(jugadorService.getAllJugadores(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/jugadores")
                        .param("page", "0")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nombre").value("Jugador Random"));

        verify(jugadorService).getAllJugadores(any(Pageable.class));
    }
}