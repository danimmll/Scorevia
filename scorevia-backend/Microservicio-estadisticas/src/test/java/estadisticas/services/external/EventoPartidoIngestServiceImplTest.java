package estadisticas.services.external;

import estadisticas.entities.equipos.Equipo;
import estadisticas.entities.partidos.Partido;
import estadisticas.repositories.jugadores.JugadorRepository;
import estadisticas.repositories.partidos.EventoPartidoRepository;
import estadisticas.repositories.partidos.PartidoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventoPartidoIngestServiceImplTest {

    @Mock private ApiFootballClient apiClient;
    @Mock private EventoPartidoRepository eventoPartidoRepository;
    @Mock private JugadorRepository jugadorRepository;
    @Mock private PartidoRepository partidoRepository;
    // 'self' es el proxy de Spring inyectado de forma perezosa en producción; en el test
    // lo mockeamos directamente, ya que el propio comportamiento de ingestarEventosDePartido
    // no es lo que se está probando aquí (ver EventoPartidoIngestServiceImplIngestaEventosTest
    // si se quisiera cubrir esa rama con más detalle).
    @Mock private EventoPartidoIngestService self;

    @InjectMocks
    private EventoPartidoIngestServiceImpl service;

    @Test
    void ingestarEventosFaltantesHistoricos_SinPendientes_TerminaRapido() {
        when(apiClient.hayCuotaDisponibleParaBackfill()).thenReturn(true);
        when(partidoRepository.findPartidosConStatsSinEventos(any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        int procesados = service.ingestarEventosFaltantesHistoricos();

        assertEquals(0, procesados);
        verify(self, never()).ingestarEventosDePartido(any());
    }

    @Test
    void ingestarEventosFaltantesHistoricos_PaginaLlenaTodosIgnorados_AvanzaDePaginaEnVezDeParar() {
        // Regresión Fix 4: si los 500 partidos más antiguos de la página 0 se procesan
        // (y por tanto quedan ignorados dentro de la ronda) pero la paginación se quedara
        // fija en 0, la siguiente consulta volvería a traer la misma página llena y el
        // backfill se daría por terminado sin explorar más partidos pendientes reales que
        // existan más allá del lote de 500. Debe avanzar a la página 1 en su lugar.
        int tamanoLote = 500;
        List<Partido> partidosPagina0 = IntStream.rangeClosed(1, tamanoLote)
                .mapToObj(this::crearPartidoConId)
                .toList();

        when(apiClient.hayCuotaDisponibleParaBackfill()).thenReturn(true);

        when(partidoRepository.findPartidosConStatsSinEventos(argThat(p -> p != null && p.getPageNumber() == 0)))
                .thenReturn(new PageImpl<>(partidosPagina0));
        when(partidoRepository.findPartidosConStatsSinEventos(argThat(p -> p != null && p.getPageNumber() == 1)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        int procesados = service.ingestarEventosFaltantesHistoricos();

        assertEquals(tamanoLote, procesados);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(partidoRepository, atLeast(2)).findPartidosConStatsSinEventos(pageableCaptor.capture());
        assertTrue(pageableCaptor.getAllValues().stream().anyMatch(p -> p.getPageNumber() == 1),
                "Debe haber consultado la página 1 tras agotar (procesar) la página 0 llena");
    }

    private Partido crearPartidoConId(int id) {
        Partido p = new Partido();
        p.setIdPartido(id);
        Equipo local = new Equipo();
        local.setIdEquipo(10);
        Equipo visitante = new Equipo();
        visitante.setIdEquipo(20);
        p.setEquipoLocal(local);
        p.setEquipoVisitante(visitante);
        return p;
    }
}
