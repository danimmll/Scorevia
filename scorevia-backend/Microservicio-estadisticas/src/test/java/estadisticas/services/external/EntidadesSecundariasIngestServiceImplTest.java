package estadisticas.services.external;

import estadisticas.dto.external.*;
import estadisticas.entities.entrenadores.Entrenador;
import estadisticas.entities.equipos.Equipo;
import estadisticas.entities.equipos.Traspaso;
import estadisticas.entities.jugadores.HistorialBaja;
import estadisticas.entities.jugadores.Jugador;
import estadisticas.entities.ligas.Trofeo;
import estadisticas.repositories.entrenadores.EntrenadorRepository;
import estadisticas.repositories.equipos.EquipoRepository;
import estadisticas.repositories.equipos.TraspasoRepository;
import estadisticas.repositories.jugadores.HistorialBajaRepository;
import estadisticas.repositories.jugadores.JugadorRepository;
import estadisticas.repositories.ligas.TrofeoRepository;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntidadesSecundariasIngestServiceImplTest {

    @Mock private ApiFootballClient apiClient;
    @Mock private JugadorRepository jugadorRepository;
    @Mock private EquipoRepository equipoRepository;
    @Mock private EntrenadorRepository entrenadorRepository;
    @Mock private TrofeoRepository trofeoRepository;
    @Mock private HistorialBajaRepository historialBajaRepository;
    @Mock private TraspasoRepository traspasoRepository;

    // Real, no mock, y con un solo hilo: asi las lecturas salen aqui mismo y en orden, que es
    // lo que hace que estos tests sigan siendo deterministas despues de paralelizarlas.
    @Spy private PeticionesEnParalelo enParalelo = new PeticionesEnParalelo(1);

    @InjectMocks
    private EntidadesSecundariasIngestServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(apiClient.hayCuotaDisponibleParaBackfill()).thenReturn(true);
    }

    // ==========================================
    // TESTS JUGADORES Y ENTRENADORES
    // ==========================================

    @Test
    void ingestarTrofeosYBajasMasivo_ProcesaCorrectamente() {
        Jugador jugador = new Jugador();
        jugador.setIdJugador(1);

        Entrenador entrenador = new Entrenador();
        entrenador.setIdEntrenador(2);

        when(jugadorRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(jugador)))
                .thenReturn(new PageImpl<>(List.of()));

        when(entrenadorRepository.findPendientesDeTrofeos(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(entrenador)))
                .thenReturn(new PageImpl<>(List.of()));

        // Mocks de APIs
        ApiTrophiesResponseDTO trofeosResp = new ApiTrophiesResponseDTO();
        ApiTrophiesResponseDTO.TrophyDTO trophyDTO = new ApiTrophiesResponseDTO.TrophyDTO();
        trophyDTO.setLeague("La Liga");
        trophyDTO.setPlace("Winner");
        trofeosResp.setResponse(List.of(trophyDTO));

        ApiSidelinedResponseDTO bajasResp = new ApiSidelinedResponseDTO();
        ApiSidelinedResponseDTO.SidelinedDTO bajaDTO = new ApiSidelinedResponseDTO.SidelinedDTO();
        bajaDTO.setType("Hamstring");
        bajaDTO.setStart("2026-01-01");
        bajaDTO.setEnd("2026-02-01"); // Tiene fin, no está activo
        bajasResp.setResponse(List.of(bajaDTO));

        when(apiClient.getTrophies("1")).thenReturn(trofeosResp);
        when(apiClient.getSidelined("1")).thenReturn(bajasResp);
        ApiTrophiesPorEntrenadorDTO.EntrenadorConTrofeosDTO filaEntrenador =
                new ApiTrophiesPorEntrenadorDTO.EntrenadorConTrofeosDTO();
        filaEntrenador.setId(2);
        filaEntrenador.setTrophies(List.of(trophyDTO));
        ApiTrophiesPorEntrenadorDTO trofeosEntrenador = new ApiTrophiesPorEntrenadorDTO();
        trofeosEntrenador.setResponse(List.of(filaEntrenador));
        when(apiClient.getTrophiesByCoachs(List.of(2))).thenReturn(trofeosEntrenador);

        when(trofeoRepository.existeTrofeoJugador(
                anyInt(), any(), any(), any(), any())).thenReturn(false);

        HistorialBaja bajaExistente = new HistorialBaja();
        when(historialBajaRepository.findByJugador_IdJugadorAndTipoAndFechaInicio(anyInt(), any(), any()))
                .thenReturn(Optional.of(bajaExistente)); // Forzamos actualización de baja existente

        service.ingestarTrofeosYBajasMasivo();

        verify(trofeoRepository, times(2)).saveAll(anyList());
        verify(historialBajaRepository).saveAll(anyList());
        verify(jugadorRepository).save(jugador);
    }

    @Test
    void ingestarTrofeosYBajasMasivo_SaltaRecientes() {
        Jugador jugador = new Jugador();
        jugador.setIdJugador(1);
        // FIX SonarQube: Usar fecha fija en el futuro evita depender del reloj del sistema en los tests
        jugador.setUltimaSyncBajas(LocalDateTime.of(2099, Month.JANUARY, 1, 0, 0));

        when(jugadorRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(jugador)))
                .thenReturn(new PageImpl<>(List.of()));

        when(entrenadorRepository.findPendientesDeTrofeos(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of()));

        service.ingestarTrofeosYBajasMasivo();

        verify(apiClient, never()).getTrophies(anyString()); // Verifica que se salta la API
    }

    @Test
    void ingestarTrofeosYBajasMasivo_FechasIncompletasYNulls_LosIgnora() {
        Jugador jugador = new Jugador();
        jugador.setIdJugador(1);

        when(jugadorRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(jugador)))
                .thenReturn(new PageImpl<>(List.of()));
        when(entrenadorRepository.findPendientesDeTrofeos(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of()));

        // DTOs con valores nulos o corruptos para testear los IF y los CATCH
        ApiTrophiesResponseDTO trofeosResp = new ApiTrophiesResponseDTO();
        ApiTrophiesResponseDTO.TrophyDTO trophyBad = new ApiTrophiesResponseDTO.TrophyDTO();
        trophyBad.setLeague(null); // Esto debe disparar el continue
        trofeosResp.setResponse(List.of(trophyBad));

        ApiSidelinedResponseDTO bajasResp = new ApiSidelinedResponseDTO();
        ApiSidelinedResponseDTO.SidelinedDTO bajaBadDate = new ApiSidelinedResponseDTO.SidelinedDTO();
        bajaBadDate.setType("Hamstring");
        bajaBadDate.setStart("FECHA-MALA"); // Debe atrapar DateTimeParseException
        bajasResp.setResponse(List.of(bajaBadDate));

        when(apiClient.getTrophies("1")).thenReturn(trofeosResp);
        when(apiClient.getSidelined("1")).thenReturn(bajasResp);

        service.ingestarTrofeosYBajasMasivo();

        verify(trofeoRepository, never()).saveAll(anyList()); // No debió guardar trofeos nulos
        verify(historialBajaRepository, never()).saveAll(anyList()); // No debió guardar bajas con fechas nulas/malas
    }

    @Test
    void ingestarTrofeosYBajasMasivo_ManejaExcepcionInterna() {
        Jugador jugador = new Jugador();
        jugador.setIdJugador(1);

        when(jugadorRepository.findAll(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(jugador))).thenReturn(new PageImpl<>(List.of()));
        when(entrenadorRepository.findPendientesDeTrofeos(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of()));

        when(apiClient.getTrophies("1")).thenThrow(new RuntimeException("API CAIDA"));

        service.ingestarTrofeosYBajasMasivo(); // No debe petar el test gracias al try-catch interno

        verify(jugadorRepository, never()).save(jugador);
    }

    // ==========================================
    // TESTS TRASPASOS
    // ==========================================

    @Test
    void ingestarTrofeosDeEntrenadores_SinPalmares_LoMarcaIgualParaNoVolverAPreguntar() {
        // La mayoria de los 27.123 entrenadores no ha ganado nada. Mientras "no tiene trofeos"
        // fue la forma de decir "falta por hacer", cada relanzamiento gastaba 17.526 peticiones
        // para acabar igual que empezo. La marca es lo que separa las dos cosas.
        Entrenador entrenador = new Entrenador();
        entrenador.setIdEntrenador(7);

        // La API no menciona a los entrenadores sin palmares: la respuesta vuelve vacia.
        ApiTrophiesPorEntrenadorDTO vacio = new ApiTrophiesPorEntrenadorDTO();
        vacio.setResponse(List.of());
        when(apiClient.getTrophiesByCoachs(List.of(7))).thenReturn(vacio);
        when(entrenadorRepository.findPendientesDeTrofeos(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(entrenador)))
                .thenReturn(new PageImpl<>(List.of()));

        assertEquals(1, service.ingestarTrofeosDeEntrenadores());

        ArgumentCaptor<Entrenador> guardado = ArgumentCaptor.forClass(Entrenador.class);
        verify(entrenadorRepository).save(guardado.capture());
        assertNotNull(guardado.getValue().getUltimaSyncTrofeos(),
                "un entrenador al que ya se le ha preguntado tiene que quedar marcado");
        verify(trofeoRepository, never()).saveAll(anyList());
    }

    @Test
    void ingestarTrofeosDeEntrenadores_SiLaApiFalla_NoLoMarcaYNoSeQuedaEnBucle() {
        // La consulta devuelve siempre la primera pagina de pendientes, y un entrenador que
        // falla no se marca: sin recordar los fallidos, la misma pagina volveria una y otra vez.
        Entrenador entrenador = new Entrenador();
        entrenador.setIdEntrenador(9);

        when(apiClient.getTrophiesByCoachs(List.of(9))).thenThrow(new RuntimeException("502 de la API"));
        when(entrenadorRepository.findPendientesDeTrofeos(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(entrenador)));

        assertEquals(0, service.ingestarTrofeosDeEntrenadores());

        verify(apiClient, times(1)).getTrophiesByCoachs(List.of(9));
        verify(entrenadorRepository, never()).save(any(Entrenador.class));
    }

    @Test
    void ingestarTrofeosDeEntrenadores_ConPalmares_LoGuardaYLoMarca() {
        Entrenador entrenador = new Entrenador();
        entrenador.setIdEntrenador(11);

        ApiTrophiesResponseDTO.TrophyDTO trofeo = new ApiTrophiesResponseDTO.TrophyDTO();
        trofeo.setLeague("UEFA Champions League");
        trofeo.setPlace("Winner");
        trofeo.setSeason("2013/2014");

        ApiTrophiesPorEntrenadorDTO.EntrenadorConTrofeosDTO fila =
                new ApiTrophiesPorEntrenadorDTO.EntrenadorConTrofeosDTO();
        fila.setId(11);
        fila.setTrophies(List.of(trofeo));
        ApiTrophiesPorEntrenadorDTO respuesta = new ApiTrophiesPorEntrenadorDTO();
        respuesta.setResponse(List.of(fila));

        when(apiClient.getTrophiesByCoachs(List.of(11))).thenReturn(respuesta);
        when(trofeoRepository.existeTrofeoEntrenador(anyInt(), any(), any(), any(), any())).thenReturn(false);
        when(entrenadorRepository.findPendientesDeTrofeos(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(entrenador)))
                .thenReturn(new PageImpl<>(List.of()));

        assertEquals(1, service.ingestarTrofeosDeEntrenadores());

        verify(trofeoRepository).saveAll(anyList());
        ArgumentCaptor<Entrenador> guardado = ArgumentCaptor.forClass(Entrenador.class);
        verify(entrenadorRepository).save(guardado.capture());
        assertNotNull(guardado.getValue().getUltimaSyncTrofeos());
    }

    @Test
    void ingestarTraspasosPorEquipo_ProcesaNuevosYDiferentesTipos() {
        Equipo equipo = new Equipo();
        equipo.setIdEquipo(10);

        when(equipoRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(equipo)))
                .thenReturn(new PageImpl<>(List.of()));

        ApiTransfersResponseDTO transfersResp = new ApiTransfersResponseDTO();
        ApiTransfersResponseDTO.TransferWrapperDTO wrapper = getTransferWrapperDTO();
        transfersResp.setResponse(List.of(wrapper));

        when(apiClient.getTransfersByTeam(10)).thenReturn(transfersResp);
        when(jugadorRepository.findById(99)).thenReturn(Optional.empty()); // Fuerza a crear jugador básico
        when(jugadorRepository.save(any(Jugador.class))).thenAnswer(i -> i.getArgument(0));
        when(traspasoRepository.findByJugadorAndFecha(any(), any())).thenReturn(List.of());

        service.ingestarTraspasosPorEquipo();

        verify(traspasoRepository).saveAll(anyList());
        verify(equipoRepository).save(equipo);
    }

    private static ApiTransfersResponseDTO.@NonNull TransferWrapperDTO getTransferWrapperDTO() {
        ApiTransfersResponseDTO.TransferWrapperDTO wrapper = new ApiTransfersResponseDTO.TransferWrapperDTO();
        ApiTransfersResponseDTO.PlayerInfoDTO playerInfo = new ApiTransfersResponseDTO.PlayerInfoDTO();
        playerInfo.setId(99);
        wrapper.setPlayer(playerInfo);

        // Traspaso 1: Monetario
        ApiTransfersResponseDTO.TransferDetailDTO detailMonetario = new ApiTransfersResponseDTO.TransferDetailDTO();
        detailMonetario.setDate("2026-08-01");
        detailMonetario.setType("€ 50M"); // Es importe monetario

        // Traspaso 2: Libre (Sin equipos definidos)
        ApiTransfersResponseDTO.TransferDetailDTO detailLibre = new ApiTransfersResponseDTO.TransferDetailDTO();
        detailLibre.setDate("2025-01-01");
        detailLibre.setType("Free"); // No es importe

        wrapper.setTransfers(List.of(detailMonetario, detailLibre));
        return wrapper;
    }

    @Test
    void ingestarTraspasosPorEquipo_NormalizaTipoYCosteYDescartaRepetidosDelMismoLote() {
        // Cubre dos fallos que se daban a la vez. El primero, que la API mete en el campo
        // "type" tanto la categoría (Free Transfer) como el importe (€ 50M, 2M €), y
        // guardarlo tal cual hacía que la misma operación quedara con etiquetas distintas
        // según cuándo se sincronizara, sin coincidir nunca en la comprobación de duplicados.
        // El segundo, que esa comprobación solo miraba la base de datos, y nada se escribe
        // hasta el saveAll final, así que una operación repetida dentro de la misma respuesta
        // entraba tantas veces como viniera.
        Equipo equipo = new Equipo();
        equipo.setIdEquipo(10);

        when(equipoRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(equipo)))
                .thenReturn(new PageImpl<>(List.of()));

        ApiTransfersResponseDTO.TransferWrapperDTO wrapper = new ApiTransfersResponseDTO.TransferWrapperDTO();
        ApiTransfersResponseDTO.PlayerInfoDTO playerInfo = new ApiTransfersResponseDTO.PlayerInfoDTO();
        playerInfo.setId(99);
        wrapper.setPlayer(playerInfo);
        wrapper.setTransfers(List.of(
                detalle("2026-08-01", "€ 50M"),
                detalle("2026-08-01", "€ 50M"),   // repetido exacto dentro del mismo lote
                detalle("2026-07-01", "2M €"),    // importe con el símbolo detrás
                detalle("2026-06-01", "Free Transfer"),
                detalle("2026-05-01", "Back from Loan"),
                detalle("2026-04-01", "N/A")));

        ApiTransfersResponseDTO transfersResp = new ApiTransfersResponseDTO();
        transfersResp.setResponse(List.of(wrapper));

        when(apiClient.getTransfersByTeam(10)).thenReturn(transfersResp);
        when(jugadorRepository.findById(99)).thenReturn(Optional.of(new Jugador()));
        when(traspasoRepository.findByJugadorAndFecha(any(), any())).thenReturn(List.of());

        service.ingestarTraspasosPorEquipo();

        ArgumentCaptor<List<Traspaso>> captor = ArgumentCaptor.captor();
        verify(traspasoRepository).saveAll(captor.capture());
        List<Traspaso> guardados = captor.getValue();

        assertEquals(5, guardados.size(), "el duplicado exacto del lote no debería guardarse");
        assertEquals(List.of("TRANSFER", "TRANSFER", "FREE", "LOAN_RETURN", "UNKNOWN"),
                guardados.stream().map(Traspaso::getTipo).toList());
        // El importe se guarda solo en `coste`, siempre con el símbolo delante.
        assertEquals("€ 50M", guardados.getFirst().getCoste());
        assertEquals("€ 2M", guardados.get(1).getCoste());
        assertNull(guardados.get(2).getCoste());
        assertNull(guardados.get(3).getCoste());
    }

    private static ApiTransfersResponseDTO.TransferDetailDTO detalle(String fecha, String tipo) {
        ApiTransfersResponseDTO.TransferDetailDTO detalle = new ApiTransfersResponseDTO.TransferDetailDTO();
        detalle.setDate(fecha);
        detalle.setType(tipo);
        return detalle;
    }

    @Test
    void ingestarTrofeosDeEntrenadores_PideDeVeinteEnVeinte() {
        // 27.123 entrenadores de uno en uno son 27.123 peticiones, un 18% de la cuota del dia.
        // De veinte en veinte son 1.357. Ese es el ahorro que fija este test.
        List<Entrenador> veinticinco = new ArrayList<>();
        for (int id = 1; id <= 25; id++) {
            Entrenador entrenador = new Entrenador();
            entrenador.setIdEntrenador(id);
            veinticinco.add(entrenador);
        }

        ApiTrophiesPorEntrenadorDTO vacio = new ApiTrophiesPorEntrenadorDTO();
        vacio.setResponse(List.of());
        when(apiClient.getTrophiesByCoachs(anyList())).thenReturn(vacio);
        when(entrenadorRepository.findPendientesDeTrofeos(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(veinticinco))
                .thenReturn(new PageImpl<>(List.of()));

        assertEquals(25, service.ingestarTrofeosDeEntrenadores(),
                "los 25 quedan resueltos aunque la API no mencione a ninguno");

        ArgumentCaptor<List<Integer>> pedidos = ArgumentCaptor.forClass(List.class);
        verify(apiClient, times(2)).getTrophiesByCoachs(pedidos.capture());
        assertEquals(List.of(20, 5), pedidos.getAllValues().stream().map(List::size).toList());
        // Y todos marcados: el silencio de la API sobre un entrenador es "no tiene nada", no
        // "no se le ha preguntado". De lo contrario el relleno no terminaria nunca.
        verify(entrenadorRepository, times(25)).save(any(Entrenador.class));
    }

    @Test
    void ingestarTraspasos_NoCompletaUnHuecoConElClubQueYaEstaAlOtroLado() {
        // "(desconocido) -> Atletico" y "Atletico -> Girona" tienen un extremo a null cada una,
        // asi que la regla de emparejado las daba por la misma operacion y rellenaba el hueco
        // con el Atletico: quedaba "Atletico -> Atletico", que en la ficha del jugador sale como
        // un ascenso del filial que nunca existio. Medidos 12.928 casos asi en la base.
        Equipo atletico = new Equipo();
        atletico.setIdEquipo(530);
        Equipo girona = new Equipo();
        girona.setIdEquipo(547);

        Traspaso sinOrigen = new Traspaso();
        sinOrigen.setIdTraspaso(1L);
        sinOrigen.setFecha(LocalDate.of(2026, Month.JULY, 1));
        sinOrigen.setEquipoDestino(atletico);
        sinOrigen.setTipo("TRANSFER");

        Jugador jugador = new Jugador();
        jugador.setIdJugador(400);

        when(equipoRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(atletico)))
                .thenReturn(new PageImpl<>(List.of()));
        when(equipoRepository.findById(530)).thenReturn(Optional.of(atletico));
        when(equipoRepository.findById(547)).thenReturn(Optional.of(girona));
        when(jugadorRepository.findById(400)).thenReturn(Optional.of(jugador));
        when(traspasoRepository.findByJugadorAndFecha(any(), any())).thenReturn(List.of(sinOrigen));

        ApiTransfersResponseDTO respuesta = new ApiTransfersResponseDTO();
        respuesta.setResponse(List.of(traspasoDeLaApi(400, "2026-07-01", 530, 547, "Transfer")));
        when(apiClient.getTransfersByTeam(530)).thenReturn(respuesta);

        service.ingestarTraspasosPorEquipo();

        assertNull(sinOrigen.getEquipoOrigen(),
                "un hueco no se rellena con el club que ya esta al otro lado");
        ArgumentCaptor<List<Traspaso>> guardados = ArgumentCaptor.forClass(List.class);
        verify(traspasoRepository).saveAll(guardados.capture());
        assertEquals(1, guardados.getValue().size(), "la salida del Atletico es una fila aparte");
        assertEquals(530, guardados.getValue().get(0).getEquipoOrigen().getIdEquipo());
        assertEquals(547, guardados.getValue().get(0).getEquipoDestino().getIdEquipo());
    }

    @Test
    void ingestarTraspasosPorEquipo_SaltaRecientes() {
        Equipo equipo = new Equipo();
        equipo.setIdEquipo(10);
        // FIX SonarQube: Fecha fija futura
        equipo.setUltimaSyncTraspasos(LocalDateTime.of(2099, Month.JANUARY, 1, 0, 0));

        when(equipoRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(equipo)))
                .thenReturn(new PageImpl<>(List.of()));

        service.ingestarTraspasosPorEquipo();

        verify(apiClient, never()).getTransfersByTeam(anyInt());
    }

    @Test
    void ingestarTraspasosPorEquipo_TraspasoInvalido_EsIgnorado() {
        Equipo equipo = new Equipo();
        equipo.setIdEquipo(10);

        when(equipoRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(equipo)))
                .thenReturn(new PageImpl<>(List.of()));

        ApiTransfersResponseDTO transfersResp = new ApiTransfersResponseDTO();
        ApiTransfersResponseDTO.TransferWrapperDTO wrapper = getWrapperDTO();
        transfersResp.setResponse(List.of(wrapper));

        when(apiClient.getTransfersByTeam(10)).thenReturn(transfersResp);
        when(jugadorRepository.findById(99)).thenReturn(Optional.of(new Jugador()));

        service.ingestarTraspasosPorEquipo();

        verify(traspasoRepository, never()).saveAll(anyList()); // No guarda nada
    }

    private static ApiTransfersResponseDTO.@NonNull TransferWrapperDTO getWrapperDTO() {
        ApiTransfersResponseDTO.TransferWrapperDTO wrapper = new ApiTransfersResponseDTO.TransferWrapperDTO();
        ApiTransfersResponseDTO.PlayerInfoDTO playerInfo = new ApiTransfersResponseDTO.PlayerInfoDTO();
        playerInfo.setId(99);
        wrapper.setPlayer(playerInfo);

        // Traspaso sin fecha o con fecha mala
        ApiTransfersResponseDTO.TransferDetailDTO detailMalo = new ApiTransfersResponseDTO.TransferDetailDTO();
        detailMalo.setDate("FECHA-MALA");

        wrapper.setTransfers(List.of(detailMalo));
        return wrapper;
    }

    @Test
    void ingestarTraspasos_CuandoYaExisteConTipoDesconocido_LoCorrigeEnVezDeDuplicar() {
        // Es la causa de las 4.520 filas duplicadas que habia en la base: el tipo formaba
        // parte de la clave, asi que la misma operacion entraba una vez como 'UNKNOWN' y otra
        // como 'FREE' cuando la API concretaba el dato.
        Equipo equipo = new Equipo();
        equipo.setIdEquipo(10);
        when(equipoRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(equipo)))
                .thenReturn(new PageImpl<>(List.of()));

        ApiTransfersResponseDTO transfersResp = new ApiTransfersResponseDTO();
        transfersResp.setResponse(List.of(getTransferWrapperDTO()));
        when(apiClient.getTransfersByTeam(10)).thenReturn(transfersResp);
        when(jugadorRepository.findById(99)).thenReturn(Optional.empty());
        when(jugadorRepository.save(any(Jugador.class))).thenAnswer(i -> i.getArgument(0));

        Traspaso guardado = new Traspaso();
        guardado.setIdTraspaso(1L);   // viene de la base: ya tiene id
        guardado.setTipo("UNKNOWN");
        when(traspasoRepository.findByJugadorAndFecha(any(), any())).thenReturn(List.of(guardado));

        service.ingestarTraspasosPorEquipo();

        // Se corrige la fila que habia. El fixture manda "€ 50M", que se normaliza a TRANSFER.
        assertEquals("TRANSFER", guardado.getTipo());
        verify(traspasoRepository).save(guardado);
        // ...y no se inserta ninguna nueva.
        verify(traspasoRepository, never()).saveAll(anyList());
    }

    @Test
    void ingestarTraspasos_NoPisaUnTipoConocidoConUnDesconocido() {
        // Al reves se perderia informacion cada vez que la API tuviera un mal dia.
        Equipo equipo = new Equipo();
        equipo.setIdEquipo(10);
        when(equipoRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(equipo)))
                .thenReturn(new PageImpl<>(List.of()));

        ApiTransfersResponseDTO transfersResp = new ApiTransfersResponseDTO();
        ApiTransfersResponseDTO.TransferWrapperDTO wrapper = getTransferWrapperDTO();
        wrapper.getTransfers().get(0).setType("N/A");   // la API deja de saberlo
        transfersResp.setResponse(List.of(wrapper));
        when(apiClient.getTransfersByTeam(10)).thenReturn(transfersResp);
        when(jugadorRepository.findById(99)).thenReturn(Optional.empty());
        when(jugadorRepository.save(any(Jugador.class))).thenAnswer(i -> i.getArgument(0));

        Traspaso guardado = new Traspaso();
        guardado.setIdTraspaso(1L);
        guardado.setTipo("LOAN");
        when(traspasoRepository.findByJugadorAndFecha(any(), any())).thenReturn(List.of(guardado));

        service.ingestarTraspasosPorEquipo();

        assertEquals("LOAN", guardado.getTipo(), "Un dato bueno no se cambia por uno vacio");
        verify(traspasoRepository, never()).save(any(Traspaso.class));
    }

    @Test
    void ingestarTraspasos_CuandoLaFilaGuardadaNoTieneOrigen_LaCompletaEnVezDeDuplicar() {
        // El duplicado que quedaba vivo: la misma operacion se ingiere dos veces, una desde el
        // club que vende y otra desde el que compra. Si la primera vez el otro club aun no
        // estaba en nuestra tabla, resolverEquipo devolvio null y la fila quedo a medias; la
        // segunda entraba entera al lado. Medido en la base: 19.313 casos por el origen.
        Equipo equipo = new Equipo();
        equipo.setIdEquipo(530);
        when(equipoRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(equipo)))
                .thenReturn(new PageImpl<>(List.of()));

        Equipo girona = new Equipo();
        girona.setIdEquipo(547);
        when(equipoRepository.findById(547)).thenReturn(Optional.of(girona));
        when(equipoRepository.findById(530)).thenReturn(Optional.of(equipo));

        ApiTransfersResponseDTO respuesta = new ApiTransfersResponseDTO();
        respuesta.setResponse(List.of(traspasoDeLaApi(99, "2026-06-29", 547, 530, "Loan Return")));
        when(apiClient.getTransfersByTeam(530)).thenReturn(respuesta);
        when(jugadorRepository.findById(99)).thenReturn(Optional.of(new Jugador()));

        // Lo que ya hay guardado: la misma operacion, pero sin el club de origen.
        Traspaso aMedias = new Traspaso();
        aMedias.setIdTraspaso(1L);
        aMedias.setFecha(LocalDate.of(2026, 6, 29));
        aMedias.setEquipoDestino(equipo);
        aMedias.setTipo("LOAN_RETURN");
        when(traspasoRepository.findByJugadorAndFecha(any(), any())).thenReturn(List.of(aMedias));

        service.ingestarTraspasosPorEquipo();

        assertEquals(girona, aMedias.getEquipoOrigen(), "el hueco se rellena");
        verify(traspasoRepository).save(aMedias);
        verify(traspasoRepository, never()).saveAll(anyList());
    }

    @Test
    void ingestarTraspasos_UnaCesionYSuVueltaElMismoDiaSonDosOperaciones() {
        // El limite del arreglo de arriba: dos filas del mismo dia solo se funden si NO se
        // sabe que son distintas. Aqui los dos extremos son conocidos y estan cruzados, asi
        // que son una cesion y su vuelta, y las dos tienen que quedar.
        Equipo equipo = new Equipo();
        equipo.setIdEquipo(1);
        when(equipoRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(equipo)))
                .thenReturn(new PageImpl<>(List.of()));

        Equipo otro = new Equipo();
        otro.setIdEquipo(2);
        when(equipoRepository.findById(1)).thenReturn(Optional.of(equipo));
        when(equipoRepository.findById(2)).thenReturn(Optional.of(otro));

        ApiTransfersResponseDTO respuesta = new ApiTransfersResponseDTO();
        ApiTransfersResponseDTO.TransferWrapperDTO wrapper =
                traspasoDeLaApi(99, "2026-07-01", 1, 2, "Loan");
        wrapper.getTransfers().add(detalleConEquipos("2026-07-01", 2, 1, "Back from Loan"));
        respuesta.setResponse(List.of(wrapper));
        when(apiClient.getTransfersByTeam(1)).thenReturn(respuesta);
        when(jugadorRepository.findById(99)).thenReturn(Optional.of(new Jugador()));
        when(traspasoRepository.findByJugadorAndFecha(any(), any())).thenReturn(List.of());

        service.ingestarTraspasosPorEquipo();

        ArgumentCaptor<List<Traspaso>> captor = ArgumentCaptor.captor();
        verify(traspasoRepository).saveAll(captor.capture());
        assertEquals(2, captor.getValue().size());
    }

    @Test
    void ingestarTraspasos_DosOrigenesDistintosAlMismoDestinoElMismoDiaSonUnaSola() {
        // Dato malo de la API: un jugador no ficha dos veces por el mismo club el mismo dia.
        // Se han medido 4.036 grupos asi (Nottingham -> Blackburn y Ashington -> Blackburn).
        Equipo destino = new Equipo();
        destino.setIdEquipo(3);
        when(equipoRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(destino)))
                .thenReturn(new PageImpl<>(List.of()));

        Equipo origenA = new Equipo();
        origenA.setIdEquipo(1);
        Equipo origenB = new Equipo();
        origenB.setIdEquipo(2);
        when(equipoRepository.findById(1)).thenReturn(Optional.of(origenA));
        when(equipoRepository.findById(2)).thenReturn(Optional.of(origenB));
        when(equipoRepository.findById(3)).thenReturn(Optional.of(destino));

        ApiTransfersResponseDTO respuesta = new ApiTransfersResponseDTO();
        ApiTransfersResponseDTO.TransferWrapperDTO wrapper =
                traspasoDeLaApi(99, "2025-02-04", 1, 3, "Loan");
        wrapper.getTransfers().add(detalleConEquipos("2025-02-04", 2, 3, "Loan"));
        respuesta.setResponse(List.of(wrapper));
        when(apiClient.getTransfersByTeam(3)).thenReturn(respuesta);
        when(jugadorRepository.findById(99)).thenReturn(Optional.of(new Jugador()));
        when(traspasoRepository.findByJugadorAndFecha(any(), any())).thenReturn(List.of());

        service.ingestarTraspasosPorEquipo();

        ArgumentCaptor<List<Traspaso>> captor = ArgumentCaptor.captor();
        verify(traspasoRepository).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(origenA, captor.getValue().getFirst().getEquipoOrigen());
    }

    // ==========================================
    // TESTS TROFEOS: LAS FILAS RESUMEN
    // ==========================================

    @Test
    void procesarTrofeos_DescartaLaFilaResumenSinTemporadaQueRepiteUnaFechada() {
        // API-Football manda, ademas de una fila por titulo, UNA fila sin temporada por cada
        // competicion+resultado. No es un titulo mas: es un total. De las 108.788 filas sin
        // temporada de la base, 108.787 tienen su gemela fechada.
        Jugador jugador = new Jugador();
        jugador.setIdJugador(874);
        when(jugadorRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(jugador)))
                .thenReturn(new PageImpl<>(List.of()));

        ApiTrophiesResponseDTO trofeos = new ApiTrophiesResponseDTO();
        trofeos.setResponse(List.of(
                trofeoDeLaApi("UEFA Champions League", "Europe", "2016/2017", "Winner"),
                trofeoDeLaApi("UEFA Champions League", "Europe", "2017/2018", "Winner"),
                trofeoDeLaApi("UEFA Champions League", "Europe", null, "Winner"),
                trofeoDeLaApi("Copa del Rey", "Spain", null, "Winner")));
        when(apiClient.getTrophies("874")).thenReturn(trofeos);
        when(apiClient.getSidelined(anyString())).thenReturn(new ApiSidelinedResponseDTO());
        when(trofeoRepository.existeTrofeoJugador(anyInt(), anyString(), any(), anyString(), any()))
                .thenReturn(false);
        when(jugadorRepository.save(any(Jugador.class))).thenAnswer(i -> i.getArgument(0));
        when(entrenadorRepository.findPendientesDeTrofeos(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of()));

        service.ingestarTrofeosYBajasMasivo();

        ArgumentCaptor<List<Trofeo>> captor = ArgumentCaptor.captor();
        verify(trofeoRepository).saveAll(captor.capture());
        List<Trofeo> guardados = captor.getValue();

        // Las dos Champions fechadas, y la Copa del Rey sin fecha: esa no tiene gemela, asi
        // que ahi la temporada falta de verdad y tirarla seria perder un titulo.
        assertEquals(3, guardados.size());
        assertEquals(2, guardados.stream()
                .filter(t -> "UEFA Champions League".equals(t.getCompeticion())).count());
        assertNull(guardados.stream()
                .filter(t -> "Copa del Rey".equals(t.getCompeticion()))
                .findFirst().orElseThrow().getTemporada());
    }

    private static ApiTrophiesResponseDTO.TrophyDTO trofeoDeLaApi(
            String competicion, String pais, String temporada, String resultado) {
        ApiTrophiesResponseDTO.TrophyDTO dto = new ApiTrophiesResponseDTO.TrophyDTO();
        dto.setLeague(competicion);
        dto.setCountry(pais);
        dto.setSeason(temporada);
        dto.setPlace(resultado);
        return dto;
    }

    private static ApiTransfersResponseDTO.TransferWrapperDTO traspasoDeLaApi(
            int idJugador, String fecha, Integer idOrigen, Integer idDestino, String tipo) {
        ApiTransfersResponseDTO.TransferWrapperDTO wrapper = new ApiTransfersResponseDTO.TransferWrapperDTO();
        ApiTransfersResponseDTO.PlayerInfoDTO info = new ApiTransfersResponseDTO.PlayerInfoDTO();
        info.setId(idJugador);
        wrapper.setPlayer(info);
        wrapper.setTransfers(new ArrayList<>(List.of(detalleConEquipos(fecha, idOrigen, idDestino, tipo))));
        return wrapper;
    }

    private static ApiTransfersResponseDTO.TransferDetailDTO detalleConEquipos(
            String fecha, Integer idOrigen, Integer idDestino, String tipo) {
        ApiTransfersResponseDTO.TransferDetailDTO detalle = new ApiTransfersResponseDTO.TransferDetailDTO();
        detalle.setDate(fecha);
        detalle.setType(tipo);
        ApiTransfersResponseDTO.TeamTransferDTO equipos = new ApiTransfersResponseDTO.TeamTransferDTO();
        equipos.setOut(equipoDeLaApi(idOrigen));
        equipos.setIn(equipoDeLaApi(idDestino));
        detalle.setTeams(equipos);
        return detalle;
    }

    private static TeamInfoDTO equipoDeLaApi(Integer id) {
        if (id == null) {
            return null;
        }
        TeamInfoDTO info = new TeamInfoDTO();
        info.setId(id);
        return info;
    }
}
