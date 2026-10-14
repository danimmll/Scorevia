package predicciones.services.prediction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import predicciones.dto.ArbitroStatsDTO;
import predicciones.dto.ContextoJugadorPrediccionDTO;
import predicciones.dto.FormaPrediccionDTO;
import predicciones.dto.LivePredictionDTO;
import predicciones.dto.internal.ClasificacionDTO;
import predicciones.dto.internal.EntrenadorDTO;
import predicciones.dto.internal.EstadisticaEquipoDTO;
import predicciones.dto.internal.EstadisticaJugadorPartidoDTO;
import predicciones.dto.internal.HistorialBajaDTO;
import predicciones.dto.internal.JugadorDTO;
import predicciones.dto.internal.PartidoDTO;
import predicciones.exception.ConflictException;
import predicciones.exception.ForbiddenException;
import predicciones.exception.NotFoundException;
import predicciones.services.predictionengine.PredictionEngineService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PredictionServiceImplTest {

    @Mock
    private EstadisticasFeignGateway estadisticasFeignGateway;

    @Mock
    private PredictionEngineService predictionEngine;

    @InjectMocks
    private PredictionServiceImpl predictionService;

    private static final Integer ID_USUARIO = 1;

    @BeforeEach
    void setUp() {
        // Por defecto simulamos un usuario PREMIUM: la mayoría de los tests de esta clase
        // prueban el motor de cálculo, no el control de acceso.
        autenticarComo("ROLE_PREMIUM");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarComo(String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("test@test.com", null, List.of(new SimpleGrantedAuthority(authority))));
    }

    @Test
    void predecirPartido_UsuarioFree_LanzaForbiddenException() {
        autenticarComo("ROLE_FREE");

        assertThrows(ForbiddenException.class, () -> predictionService.predecirPartido(1, ID_USUARIO));
        verifyNoInteractions(estadisticasFeignGateway);
    }

    @Test
    void predecirPartidoEnVivo_UsuarioFree_LanzaForbiddenException() {
        autenticarComo("ROLE_FREE");

        assertThrows(ForbiddenException.class, () -> predictionService.predecirPartidoEnVivo(1, 30, ID_USUARIO));
        verifyNoInteractions(estadisticasFeignGateway);
    }

    @Test
    void predecirPartido_UsuarioPremium_PermiteAcceso() {
        when(estadisticasFeignGateway.obtenerContexto(1)).thenReturn(buildContextoCompleto());
        when(predictionEngine.calcularProbabilidadesPartido(anyDouble(), anyDouble())).thenReturn(new HashMap<>());

        assertDoesNotThrow(() -> predictionService.predecirPartido(1, ID_USUARIO));
    }

    @Test
    void predecirPartido_UsuarioAdmin_PermiteAcceso() {
        autenticarComo("ROLE_ADMIN");
        when(estadisticasFeignGateway.obtenerContexto(1)).thenReturn(buildContextoCompleto());
        when(predictionEngine.calcularProbabilidadesPartido(anyDouble(), anyDouble())).thenReturn(new HashMap<>());

        assertDoesNotThrow(() -> predictionService.predecirPartido(1, ID_USUARIO));
    }

    @Test
    void predecirPartido_SinAutenticacion_LanzaForbiddenException() {
        SecurityContextHolder.clearContext();

        assertThrows(ForbiddenException.class, () -> predictionService.predecirPartido(1, ID_USUARIO));
    }

    @Test
    void predecirPartido_FlujoCompletoConModificadores_RetornaPrediccion() {
        // Arrange
        Integer idPartido = 1;
        FormaPrediccionDTO contexto = buildContextoCompleto();
        when(estadisticasFeignGateway.obtenerContexto(idPartido)).thenReturn(contexto);

        Map<String, BigDecimal> mockResult = new HashMap<>();
        mockResult.put("victoriaLocal", BigDecimal.valueOf(50.0));
        when(predictionEngine.calcularProbabilidadesPartido(anyDouble(), anyDouble())).thenReturn(mockResult);

        // Act
        Map<String, BigDecimal> resultado = predictionService.predecirPartido(idPartido, ID_USUARIO);

        // Assert
        assertNotNull(resultado);
        assertEquals(BigDecimal.valueOf(50.0), resultado.get("victoriaLocal"));
        verify(predictionEngine).calcularProbabilidadesPartido(anyDouble(), anyDouble());
    }

    @Test
    void predecirPartidoEnVivo_CalculaTimeDecayYRetornaDTO() {
        // Arrange
        Integer idPartido = 1;
        FormaPrediccionDTO contexto = buildContextoCompleto();
        when(estadisticasFeignGateway.obtenerContexto(idPartido)).thenReturn(contexto);

        when(predictionEngine.calcularProporcionTiempoRestante(75)).thenReturn(0.25);

        // MOCK AÑADIDO: Evita el NullPointerException simulando el impacto de tarjetas rojas
        when(predictionEngine.aplicarImpactoEventosEnVivo(anyDouble(), anyDouble(), any(), anyLong(), anyLong()))
                .thenReturn(new double[]{1.5, 0.8});

        Map<String, BigDecimal> mockResult = new HashMap<>();
        mockResult.put("victoriaLocal", BigDecimal.valueOf(60.0));
        when(predictionEngine.calcularProbabilidades(anyDouble(), anyDouble(), eq(1), eq(0))).thenReturn(mockResult);

        // Act
        LivePredictionDTO resultado = predictionService.predecirPartidoEnVivo(idPartido, 75, ID_USUARIO);

        // Assert
        assertNotNull(resultado);
        assertEquals(75, resultado.getMinutoActual());
        assertEquals(1, resultado.getGolesLocalActuales());
        assertEquals(60.0, resultado.getProbabilidades().get("victoriaLocal").doubleValue());
    }

    @Test
    void predecirPartidoEnVivo_FeignLanzaExceptionAlObtenerEventos_LanzaConflictException() {
        Integer idPartido = 1;
        FormaPrediccionDTO contexto = buildContextoCompleto();
        when(estadisticasFeignGateway.obtenerContexto(idPartido)).thenReturn(contexto);
        when(estadisticasFeignGateway.obtenerEventosEnVivo(idPartido)).thenThrow(new RuntimeException("Feign caido"));

        assertThrows(ConflictException.class, () -> predictionService.predecirPartidoEnVivo(idPartido, 45, ID_USUARIO));
    }

    @Test
    void predecirPartidoEnVivo_ContextoNoEncontrado_LanzaNotFoundException() {
        when(estadisticasFeignGateway.obtenerContexto(anyInt())).thenReturn(null);

        assertThrows(NotFoundException.class, () -> predictionService.predecirPartidoEnVivo(1, 45, ID_USUARIO));
        verifyNoInteractions(predictionEngine);
    }

    @Test
    void predecirPartidoEnVivo_MinutoExtraTiempo_SeCapaA90ParaElCalculoDeTiempoRestante() {
        Integer idPartido = 1;
        FormaPrediccionDTO contexto = buildContextoCompleto();
        when(estadisticasFeignGateway.obtenerContexto(idPartido)).thenReturn(contexto);
        when(predictionEngine.calcularProporcionTiempoRestante(90)).thenReturn(0.0);
        when(predictionEngine.aplicarImpactoEventosEnVivo(anyDouble(), anyDouble(), any(), anyLong(), anyLong()))
                .thenReturn(new double[]{1.0, 1.0});
        when(predictionEngine.calcularProbabilidades(anyDouble(), anyDouble(), anyInt(), anyInt())).thenReturn(new HashMap<>());

        // Minuto real 97 (tiempo añadido): debe capearse a 90 para el decaimiento temporal.
        predictionService.predecirPartidoEnVivo(idPartido, 97, ID_USUARIO);

        verify(predictionEngine).calcularProporcionTiempoRestante(90);
        verify(predictionEngine, never()).calcularProporcionTiempoRestante(97);
    }

    @Test
    void obtenerContexto_RetornaNull_LanzaNotFoundException() {
        when(estadisticasFeignGateway.obtenerContexto(anyInt())).thenReturn(null);

        assertThrows(NotFoundException.class, () -> predictionService.predecirPartido(1, ID_USUARIO));
    }

    @Test
    void obtenerContexto_ListasVacias_LanzaNotFoundException() {
        FormaPrediccionDTO contextoIncompleto = FormaPrediccionDTO.builder()
                .historicoStatsEquiposLocal(new ArrayList<>())
                .historicoStatsEquiposVisitante(new ArrayList<>())
                .build();
        when(estadisticasFeignGateway.obtenerContexto(anyInt())).thenReturn(contextoIncompleto);

        assertThrows(NotFoundException.class, () -> predictionService.predecirPartido(1, ID_USUARIO));
    }

    @Test
    void obtenerContexto_FeignLanzaException_LanzaConflictException() {
        when(estadisticasFeignGateway.obtenerContexto(anyInt())).thenThrow(new RuntimeException("Feign caido"));

        assertThrows(ConflictException.class, () -> predictionService.predecirPartido(1, ID_USUARIO));
    }

    @Test
    void obtenerContexto_PartidoFinalizado_LanzaConflictException() {
        FormaPrediccionDTO contextoFinalizado = contextoMinimo().toBuilder()
                .partido(PartidoDTO.builder().estado("FINALIZADO").build())
                .build();
        when(estadisticasFeignGateway.obtenerContexto(anyInt())).thenReturn(contextoFinalizado);

        assertThrows(ConflictException.class, () -> predictionService.predecirPartido(1, ID_USUARIO));
        verifyNoInteractions(predictionEngine);
    }

    @Test
    void obtenerContexto_PartidoEnJuego_PermiteCalcularPrediccion() {
        FormaPrediccionDTO contextoEnJuego = contextoMinimo().toBuilder()
                .partido(PartidoDTO.builder().estado("EN_JUEGO").build())
                .build();
        when(estadisticasFeignGateway.obtenerContexto(anyInt())).thenReturn(contextoEnJuego);
        when(predictionEngine.calcularProbabilidadesPartido(anyDouble(), anyDouble())).thenReturn(new HashMap<>());

        assertDoesNotThrow(() -> predictionService.predecirPartido(1, ID_USUARIO));
    }

    @Test
    void calcularLambdas_ContextoConNulos_NoFallaYAplicaValoresPorDefecto() {
        // Usamos una fecha fija para evitar warnings de SonarQube por LocalDateTime.now()
        LocalDateTime fechaFija = LocalDateTime.of(2024, Month.JANUARY, 1, 12, 0);

        FormaPrediccionDTO contextoConNulos = FormaPrediccionDTO.builder()
                .idEquipoLocal(1L)
                .idEquipoVisitante(2L)
                .partido(PartidoDTO.builder().fecha(fechaFija).build())
                .historicoStatsEquiposLocal(List.of(new EstadisticaEquipoDTO())) // Goles esperados null
                .historicoStatsEquiposVisitante(List.of(new EstadisticaEquipoDTO()))
                .ultimosPartidosLocal(null)
                .ultimosPartidosVisitante(null)
                .ultimosPartidosLocalComoLocal(null)
                .ultimosPartidosVisitanteComoVisitante(null)
                .historialH2H(null)
                .bajasActivasLocal(null)
                .bajasActivasVisitante(null)
                .entrenadorLocal(null)
                .entrenadorVisitante(null)
                .datosArbitro(null)
                .build();

        when(estadisticasFeignGateway.obtenerContexto(anyInt())).thenReturn(contextoConNulos);
        when(predictionEngine.calcularProbabilidadesPartido(anyDouble(), anyDouble())).thenReturn(new HashMap<>());

        assertDoesNotThrow(() -> predictionService.predecirPartido(1, ID_USUARIO));
    }

    @Test
    void calcularLambdas_RivalConDefensaDebil_AumentaElAtaquePropio() {
        double lambdaLocalDefensaFuerte = obtenerLambdaLocalConDefensaVisitante(
                ClasificacionDTO.builder().partidosJugados(20).golesContra(10).build()); // 0.5 goles/partido encajados
        double lambdaLocalDefensaDebil = obtenerLambdaLocalConDefensaVisitante(
                ClasificacionDTO.builder().partidosJugados(20).golesContra(50).build()); // 2.5 goles/partido encajados

        assertTrue(lambdaLocalDefensaDebil > lambdaLocalDefensaFuerte,
                "El lambda local debería ser mayor cuando el rival encaja muchos más goles por partido");
    }

    private double obtenerLambdaLocalConDefensaVisitante(ClasificacionDTO clasificacionVisitante) {
        clearInvocations(predictionEngine);
        FormaPrediccionDTO contexto = buildContextoCompleto().toBuilder()
                .clasificacionVisitante(clasificacionVisitante)
                .build();

        when(estadisticasFeignGateway.obtenerContexto(anyInt())).thenReturn(contexto);
        when(predictionEngine.calcularProbabilidadesPartido(anyDouble(), anyDouble())).thenReturn(new HashMap<>());

        predictionService.predecirPartido(1, ID_USUARIO);

        ArgumentCaptor<Double> lambdaLocalCaptor = ArgumentCaptor.forClass(Double.class);
        verify(predictionEngine).calcularProbabilidadesPartido(lambdaLocalCaptor.capture(), anyDouble());
        return lambdaLocalCaptor.getValue();
    }

    @Test
    void calcularLambdas_BajaDeMaximoGoleador_PenalizaMasQueBajaDeSuplente() {
        List<ContextoJugadorPrediccionDTO> plantilla = List.of(
                plantillaJugador(1, 20), // máximo goleador
                plantillaJugador(2, 1)   // suplente casi sin goles
        );

        double lambdaSinMaximoGoleador = obtenerLambdaLocalConBaja(plantilla, 1);
        double lambdaSinSuplente = obtenerLambdaLocalConBaja(plantilla, 2);

        assertTrue(lambdaSinMaximoGoleador < lambdaSinSuplente,
                "Perder al máximo goleador debe penalizar más el ataque que perder a un suplente");
    }

    private ContextoJugadorPrediccionDTO plantillaJugador(int idJugador, int goles) {
        return ContextoJugadorPrediccionDTO.builder()
                .jugador(JugadorDTO.builder().idJugador(idJugador).build())
                .historialStatsTemporada(List.of(EstadisticaJugadorPartidoDTO.builder().goles(goles).build()))
                .build();
    }

    private double obtenerLambdaLocalConBaja(List<ContextoJugadorPrediccionDTO> plantilla, int idJugadorDeBaja) {
        clearInvocations(predictionEngine);
        FormaPrediccionDTO contexto = buildContextoCompleto().toBuilder()
                .plantillaLocalConStats(plantilla)
                .bajasActivasLocal(List.of(HistorialBajaDTO.builder().idJugador(idJugadorDeBaja).build()))
                .build();

        when(estadisticasFeignGateway.obtenerContexto(anyInt())).thenReturn(contexto);
        when(predictionEngine.calcularProbabilidadesPartido(anyDouble(), anyDouble())).thenReturn(new HashMap<>());

        predictionService.predecirPartido(1, ID_USUARIO);

        ArgumentCaptor<Double> lambdaLocalCaptor = ArgumentCaptor.forClass(Double.class);
        verify(predictionEngine).calcularProbabilidadesPartido(lambdaLocalCaptor.capture(), anyDouble());
        return lambdaLocalCaptor.getValue();
    }

    @Test
    void calcularModificadorPunteria_RatioMenorA0_8_ReduceLambdaUn10Porciento() {
        double lambdaNeutral = obtenerLambdaLocalConPunteria(1); // ratio 1/1.0 = 1.0 -> sin corrección
        double lambdaBajoRendimiento = obtenerLambdaLocalConPunteria(0); // ratio 0/1.0 = 0.0 -> modificador 0.90

        assertEquals(lambdaNeutral * 0.90, lambdaBajoRendimiento, 0.0001,
                "Un equipo que rinde muy por debajo de su xG debe reducir su lambda de ataque un 10%");
    }

    @Test
    void calcularModificadorPunteria_RatioMayorA1_2_AumentaLambdaUn10Porciento() {
        double lambdaNeutral = obtenerLambdaLocalConPunteria(1); // ratio 1/1.0 = 1.0 -> sin corrección
        double lambdaSobreRendimiento = obtenerLambdaLocalConPunteria(2); // ratio 2/1.0 = 2.0 -> modificador 1.10

        assertEquals(lambdaNeutral * 1.10, lambdaSobreRendimiento, 0.0001,
                "Un equipo que rinde muy por encima de su xG debe aumentar su lambda de ataque un 10%");
    }

    private double obtenerLambdaLocalConPunteria(int golesRealesEnEmpate) {
        clearInvocations(predictionEngine);
        LocalDateTime fechaFija = LocalDateTime.of(2024, Month.JANUARY, 1, 12, 0);

        // xG total = 1.0 (un único registro), así que el ratio real/xG (y por tanto el
        // modificador) queda determinado solo por golesRealesEnEmpate.
        EstadisticaEquipoDTO statLocal = EstadisticaEquipoDTO.builder().golesEsperados(BigDecimal.valueOf(1.0)).build();

        // Usamos un EMPATE a propósito: un resultado no-empate en esta misma lista de
        // partidos activaría también el modificador de racha, contaminando el aislamiento.
        PartidoDTO partidoPunteria = PartidoDTO.builder()
                .idEquipoLocal(1)
                .resultadoLocal(golesRealesEnEmpate)
                .resultadoVisitante(golesRealesEnEmpate)
                .fecha(fechaFija.minusDays(10)) // fuera de la ventana de fatiga (< 4 días)
                .build();

        FormaPrediccionDTO contexto = FormaPrediccionDTO.builder()
                .idEquipoLocal(1L)
                .idEquipoVisitante(2L)
                .partido(PartidoDTO.builder().fecha(fechaFija).build())
                .historicoStatsEquiposLocal(List.of(statLocal))
                .historicoStatsEquiposVisitante(List.of(new EstadisticaEquipoDTO()))
                .ultimosPartidosLocal(List.of(partidoPunteria))
                .build();

        when(estadisticasFeignGateway.obtenerContexto(anyInt())).thenReturn(contexto);
        when(predictionEngine.calcularProbabilidadesPartido(anyDouble(), anyDouble())).thenReturn(new HashMap<>());

        predictionService.predecirPartido(1, ID_USUARIO);

        ArgumentCaptor<Double> lambdaLocalCaptor = ArgumentCaptor.forClass(Double.class);
        verify(predictionEngine).calcularProbabilidadesPartido(lambdaLocalCaptor.capture(), anyDouble());
        return lambdaLocalCaptor.getValue();
    }

    @Test
    void calcularModificadorEntrenador_ConEntrenadorAsignado_AumentaLambdaUn2Porciento() {
        double lambdaSinEntrenador = obtenerLambdaLocalConEntrenador(null);
        double lambdaConEntrenador = obtenerLambdaLocalConEntrenador(EntrenadorDTO.builder().nombre("Ancelotti").build());

        assertEquals(lambdaSinEntrenador * 1.02, lambdaConEntrenador, 0.0001,
                "Tener un entrenador asignado debe aportar un pequeño extra del 2%");
    }

    private double obtenerLambdaLocalConEntrenador(EntrenadorDTO entrenadorLocal) {
        clearInvocations(predictionEngine);
        FormaPrediccionDTO contexto = contextoMinimo().toBuilder()
                .entrenadorLocal(entrenadorLocal)
                .build();

        when(estadisticasFeignGateway.obtenerContexto(anyInt())).thenReturn(contexto);
        when(predictionEngine.calcularProbabilidadesPartido(anyDouble(), anyDouble())).thenReturn(new HashMap<>());

        predictionService.predecirPartido(1, ID_USUARIO);

        ArgumentCaptor<Double> lambdaLocalCaptor = ArgumentCaptor.forClass(Double.class);
        verify(predictionEngine).calcularProbabilidadesPartido(lambdaLocalCaptor.capture(), anyDouble());
        return lambdaLocalCaptor.getValue();
    }

    @Test
    void calcularModificadorArbitro_ConAltaMediaDeRojas_AumentaAmbosLambdasUn3Porciento() {
        double[] lambdasSinArbitro = obtenerLambdasConArbitro(null);
        double[] lambdasArbitroDuro = obtenerLambdasConArbitro(ArbitroStatsDTO.builder().mediaTarjetasRojas(BigDecimal.valueOf(0.30)).build());

        assertEquals(lambdasSinArbitro[0] * 1.03, lambdasArbitroDuro[0], 0.0001);
        assertEquals(lambdasSinArbitro[1] * 1.03, lambdasArbitroDuro[1], 0.0001,
                "Un árbitro con media de rojas > 0.25 debe aumentar un 3% el lambda de AMBOS equipos");
    }

    @Test
    void calcularModificadorArbitro_ConMediaDeRojasBaja_NoAplicaCorreccion() {
        double[] lambdasSinArbitro = obtenerLambdasConArbitro(null);
        double[] lambdasArbitroSuave = obtenerLambdasConArbitro(ArbitroStatsDTO.builder().mediaTarjetasRojas(BigDecimal.valueOf(0.10)).build());

        assertArrayEquals(lambdasSinArbitro, lambdasArbitroSuave, 0.0001,
                "Un árbitro con media de rojas <= 0.25 no debe corregir los lambdas");
    }

    private double[] obtenerLambdasConArbitro(ArbitroStatsDTO datosArbitro) {
        clearInvocations(predictionEngine);
        FormaPrediccionDTO contexto = contextoMinimo().toBuilder()
                .datosArbitro(datosArbitro)
                .build();

        when(estadisticasFeignGateway.obtenerContexto(anyInt())).thenReturn(contexto);
        when(predictionEngine.calcularProbabilidadesPartido(anyDouble(), anyDouble())).thenReturn(new HashMap<>());

        predictionService.predecirPartido(1, ID_USUARIO);

        ArgumentCaptor<Double> lambdaLocalCaptor = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> lambdaVisitanteCaptor = ArgumentCaptor.forClass(Double.class);
        verify(predictionEngine).calcularProbabilidadesPartido(lambdaLocalCaptor.capture(), lambdaVisitanteCaptor.capture());
        return new double[]{lambdaLocalCaptor.getValue(), lambdaVisitanteCaptor.getValue()};
    }

    @Test
    void calcularModificadorH2H_LocalGanaElHistorial_AumentaSuLambdaYReduceElDelVisitante() {
        LocalDateTime fechaFija = LocalDateTime.of(2024, Month.JANUARY, 1, 12, 0);
        // Único cruce H2H, ganado por el equipo local (id 1): activa exactamente un paso de
        // +-0.02 en cada lado de modsH2H, sin acercarse a los topes de clamp [0.70,1.30].
        PartidoDTO h2hGanaLocal = PartidoDTO.builder()
                .idEquipoLocal(1).resultadoLocal(2).resultadoVisitante(0).fecha(fechaFija.minusDays(10))
                .build();

        double[] lambdasSinH2H = obtenerLambdasConH2H(null);
        double[] lambdasConH2H = obtenerLambdasConH2H(List.of(h2hGanaLocal));

        assertEquals(lambdasSinH2H[0] * 1.02, lambdasConH2H[0], 0.0001,
                "Ganar el historial H2H debe aumentar un 2% el lambda del equipo local");
        assertEquals(lambdasSinH2H[1] * 0.98, lambdasConH2H[1], 0.0001,
                "Perder el historial H2H debe reducir un 2% el lambda del equipo visitante");
    }

    private double[] obtenerLambdasConH2H(List<PartidoDTO> h2h) {
        clearInvocations(predictionEngine);
        FormaPrediccionDTO contexto = contextoMinimo().toBuilder()
                .historialH2H(h2h)
                .build();

        when(estadisticasFeignGateway.obtenerContexto(anyInt())).thenReturn(contexto);
        when(predictionEngine.calcularProbabilidadesPartido(anyDouble(), anyDouble())).thenReturn(new HashMap<>());

        predictionService.predecirPartido(1, ID_USUARIO);

        ArgumentCaptor<Double> lambdaLocalCaptor = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> lambdaVisitanteCaptor = ArgumentCaptor.forClass(Double.class);
        verify(predictionEngine).calcularProbabilidadesPartido(lambdaLocalCaptor.capture(), lambdaVisitanteCaptor.capture());
        return new double[]{lambdaLocalCaptor.getValue(), lambdaVisitanteCaptor.getValue()};
    }

    // Contexto sin ningún historial de partidos (rachas/fatiga/puntería neutros por listas
    // vacías o nulas) para aislar limpiamente un único modificador por test.
    private FormaPrediccionDTO contextoMinimo() {
        LocalDateTime fechaFija = LocalDateTime.of(2024, Month.JANUARY, 1, 12, 0);
        return FormaPrediccionDTO.builder()
                .idEquipoLocal(1L)
                .idEquipoVisitante(2L)
                .partido(PartidoDTO.builder().fecha(fechaFija).build())
                .historicoStatsEquiposLocal(List.of(EstadisticaEquipoDTO.builder().golesEsperados(BigDecimal.valueOf(1.0)).build()))
                .historicoStatsEquiposVisitante(List.of(new EstadisticaEquipoDTO()))
                .build();
    }

    // --- HELPER PARA CREAR UN CONTEXTO RICO QUE ACTIVE TODAS LAS RAMAS MATEMÁTICAS ---
    private FormaPrediccionDTO buildContextoCompleto() {
        // Congelamos el tiempo para que SonarQube no se queje de la dependencia del reloj del sistema
        LocalDateTime fechaFija = LocalDateTime.of(2024, Month.JANUARY, 1, 12, 0);
        // resultadoLocal/resultadoVisitante fijados a propósito: predecirPartidoEnVivo lee el
        // marcador real del contexto (obtenerGolesReales), NUNCA del cliente -- sin esto aquí
        // caían a 0-0 por defecto y los tests LIVE que verifican el marcador quedaban probando
        // un valor que la producción nunca podría devolver con este fixture.
        PartidoDTO partidoObjetivo = PartidoDTO.builder().fecha(fechaFija)
                .resultadoLocal(1).resultadoVisitante(0).build();

        // Stats: Local tiene buena puntería (xG 1.0, anota 2), Visitante mala (xG 3.0, anota 1)
        EstadisticaEquipoDTO statLocal = EstadisticaEquipoDTO.builder().golesEsperados(BigDecimal.valueOf(1.0)).build();
        EstadisticaEquipoDTO statVis = EstadisticaEquipoDTO.builder().golesEsperados(BigDecimal.valueOf(3.0)).build();

        // Rachas:
        // 1. Partido reciente (dentro de 45 días) ganado por local
        PartidoDTO pRachaReciente = PartidoDTO.builder().idEquipoLocal(1).resultadoLocal(2).resultadoVisitante(0).fecha(fechaFija.minusDays(10)).build();
        // 2. Partido intermedio (peso parcial) perdido por local
        PartidoDTO pRachaIntermedio = PartidoDTO.builder().idEquipoLocal(1).resultadoLocal(0).resultadoVisitante(1).fecha(fechaFija.minusDays(60)).build();
        // 3. Partido irrelevante (> 90 días)
        PartidoDTO pRachaViejo = PartidoDTO.builder().idEquipoLocal(1).resultadoLocal(1).resultadoVisitante(0).fecha(fechaFija.minusDays(100)).build();

        // Fatiga: Partido hace 2 días (activa penalización < 4 días)
        PartidoDTO pFatiga = PartidoDTO.builder().fecha(fechaFija.minusDays(2)).build();

        return FormaPrediccionDTO.builder()
                .idEquipoLocal(1L)
                .idEquipoVisitante(2L)
                .partido(partidoObjetivo)
                .historicoStatsEquiposLocal(List.of(statLocal))
                .historicoStatsEquiposVisitante(List.of(statVis))
                .ultimosPartidosLocal(List.of(pFatiga, pRachaReciente, pRachaIntermedio, pRachaViejo))
                .ultimosPartidosVisitante(List.of(pRachaReciente))
                .ultimosPartidosLocalComoLocal(List.of(pRachaReciente))
                .ultimosPartidosVisitanteComoVisitante(List.of(pRachaReciente))
                .historialH2H(List.of(pRachaReciente)) // Usamos el mismo DTO simulando un H2H donde gana local
                .bajasActivasLocal(List.of(new HistorialBajaDTO(), new HistorialBajaDTO())) // 2 bajas
                .bajasActivasVisitante(new ArrayList<>())
                .entrenadorLocal(EntrenadorDTO.builder().nombre("Ancelotti").build())
                .entrenadorVisitante(EntrenadorDTO.builder().nombre("Flick").build())
                .datosArbitro(ArbitroStatsDTO.builder().mediaTarjetasRojas(BigDecimal.valueOf(0.30)).build()) // Activa modificador de árbitro
                .build();
    }
}