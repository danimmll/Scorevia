package predicciones.services.predictionengine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import predicciones.dto.internal.EquipoDTO;
import predicciones.dto.internal.EventoPartidoDTO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PredictionEngineServiceImplTest {

    private PredictionEngineServiceImpl predictionEngine;

    @BeforeEach
    void setUp() {
        predictionEngine = new PredictionEngineServiceImpl();
    }

    @Test
    void calcularProbabilidadesPartido_EscenarioIgualado_ProbabilidadesSimilares() {
        double lambdaLocal = 1.5;
        double lambdaVisitante = 1.5;

        Map<String, BigDecimal> resultado = predictionEngine.calcularProbabilidadesPartido(lambdaLocal, lambdaVisitante);

        assertNotNull(resultado);
        assertTrue(resultado.containsKey("victoriaLocal"));
        assertTrue(resultado.containsKey("empate"));
        assertTrue(resultado.containsKey("victoriaVisitante"));

        double vLocal = resultado.get("victoriaLocal").doubleValue();
        double empate = resultado.get("empate").doubleValue();
        double vVisitante = resultado.get("victoriaVisitante").doubleValue();

        assertEquals(vLocal, vVisitante, 2.0);
        assertTrue(empate > 15.0 && empate < 35.0);
        assertEquals(100.0, vLocal + empate + vVisitante, 0.1);
    }

    @Test
    void calcularProbabilidadesPartido_FavoritoClaro_GanaFavorito() {
        double lambdaLocal = 2.5;
        double lambdaVisitante = 0.5;

        Map<String, BigDecimal> resultado = predictionEngine.calcularProbabilidadesPartido(lambdaLocal, lambdaVisitante);

        double vLocal = resultado.get("victoriaLocal").doubleValue();
        double vVisitante = resultado.get("victoriaVisitante").doubleValue();

        assertTrue(vLocal > 70.0, "El favorito claro debería tener más de 70% de victoria");
        assertTrue(vVisitante < 15.0, "El underdog debería tener muy pocas opciones");
    }

    @Test
    void calcularProbabilidadesLive_LocalGanando_SeMantieneVentaja() {
        double lambdaLocalRestante = 0.2;
        double lambdaVisitanteRestante = 0.2;
        int golesLocalActual = 2;
        int golesVisitanteActual = 0;

        Map<String, BigDecimal> resultado = predictionEngine.calcularProbabilidades(
                lambdaLocalRestante, lambdaVisitanteRestante, golesLocalActual, golesVisitanteActual);

        double vLocal = resultado.get("victoriaLocal").doubleValue();
        double empate = resultado.get("empate").doubleValue();
        double vVisitante = resultado.get("victoriaVisitante").doubleValue();

        assertTrue(vLocal > 95.0, "Con 2-0 y poco tiempo, la victoria local es casi segura");
        assertTrue(empate < 5.0, "Empatar requiere 2 goles, muy improbable");
        assertTrue(vVisitante < 1.0, "Victoria visitante debería ser casi 0");
    }

    @Test
    void calcularProbabilidadesLive_TiempoAcabado_LambdasCero() {
        // En el minuto 90+, si quedan 0 goles esperados y el partido va 1-1
        Map<String, BigDecimal> resultado = predictionEngine.calcularProbabilidades(
                0.0, 0.0, 1, 1);

        double empate = resultado.get("empate").doubleValue();

        // Debe devolver 100% al empate al no haber opción a más goles
        assertTrue(empate > 99.0);
    }

    @Test
    void calcularProporcionTiempoRestante_CurvaExponencial() {
        double inicio = predictionEngine.calcularProporcionTiempoRestante(0);
        double mitad = predictionEngine.calcularProporcionTiempoRestante(45);
        double finalPartido = predictionEngine.calcularProporcionTiempoRestante(90);
        double descuento = predictionEngine.calcularProporcionTiempoRestante(95);

        assertEquals(1.0, inicio, 0.01);
        assertEquals(0.0, finalPartido, 0.01);
        assertEquals(0.0, descuento, 0.01);

        // Al descanso (45 mins) por la curva exponencial de 1.25, queda MÁS del 50% de probabilidad de gol
        // 1.0 - (45/90)^1.25 = 1.0 - 0.5^1.25 = 1.0 - 0.4204 = 0.579
        assertTrue(mitad > 0.55 && mitad < 0.60);
    }

    // ---- aplicarImpactoEventosEnVivo ----

    private static final Long ID_EQUIPO_LOCAL = 10L;
    private static final Long ID_EQUIPO_VISITANTE = 20L;

    private EventoPartidoDTO tarjetaRoja(int minuto, Integer idEquipoDelEvento) {
        EventoPartidoDTO evento = new EventoPartidoDTO();
        evento.setTipoEvento("Card");
        evento.setDetalle("Red Card");
        evento.setTiempoMinuto(minuto);
        if (idEquipoDelEvento != null) {
            evento.setEquipo(EquipoDTO.builder().idEquipo(idEquipoDelEvento).build());
        }
        return evento;
    }

    @Test
    void aplicarImpactoEventosEnVivo_SinEventos_DevuelveLambdasSinCambios() {
        double[] resultadoNull = predictionEngine.aplicarImpactoEventosEnVivo(2.0, 1.0, null, ID_EQUIPO_LOCAL, ID_EQUIPO_VISITANTE);
        double[] resultadoVacio = predictionEngine.aplicarImpactoEventosEnVivo(2.0, 1.0, List.of(), ID_EQUIPO_LOCAL, ID_EQUIPO_VISITANTE);

        assertArrayEquals(new double[]{2.0, 1.0}, resultadoNull, 0.0001);
        assertArrayEquals(new double[]{2.0, 1.0}, resultadoVacio, 0.0001);
    }

    @Test
    void aplicarImpactoEventosEnVivo_RojaEquipoLocalTemprana_PenalizaLocalYBeneficiaVisitante() {
        EventoPartidoDTO roja = tarjetaRoja(10, ID_EQUIPO_LOCAL.intValue());

        double[] resultado = predictionEngine.aplicarImpactoEventosEnVivo(
                2.0, 1.0, List.of(roja), ID_EQUIPO_LOCAL, ID_EQUIPO_VISITANTE);

        // fraccionRestante=(90-10)/90=0.8889 -> impacto=0.2667
        assertEquals(1.46667, resultado[0], 0.001, "El lambda del expulsado debe reducirse");
        assertEquals(1.13333, resultado[1], 0.001, "El lambda del rival debe aumentar");
    }

    @Test
    void aplicarImpactoEventosEnVivo_RojaEquipoVisitante_PenalizaVisitanteYBeneficiaLocal() {
        EventoPartidoDTO roja = tarjetaRoja(10, ID_EQUIPO_VISITANTE.intValue());

        double[] resultado = predictionEngine.aplicarImpactoEventosEnVivo(
                2.0, 1.0, List.of(roja), ID_EQUIPO_LOCAL, ID_EQUIPO_VISITANTE);

        assertEquals(2.26667, resultado[0], 0.001, "El lambda del rival del expulsado debe aumentar");
        assertEquals(0.73333, resultado[1], 0.001, "El lambda del equipo expulsado debe reducirse");
    }

    @Test
    void aplicarImpactoEventosEnVivo_RojaCercaDelFinal_ImpactoCasiNulo() {
        EventoPartidoDTO roja = tarjetaRoja(89, ID_EQUIPO_LOCAL.intValue());

        double[] resultado = predictionEngine.aplicarImpactoEventosEnVivo(
                2.0, 1.0, List.of(roja), ID_EQUIPO_LOCAL, ID_EQUIPO_VISITANTE);

        assertEquals(2.0, resultado[0], 0.01, "A un minuto del final, la roja apenas debe afectar");
        assertEquals(1.0, resultado[1], 0.01);
    }

    @Test
    void aplicarImpactoEventosEnVivo_RojaSinMinuto_AsumeMinutoCeroYAplicaImpactoMaximo() {
        EventoPartidoDTO roja = tarjetaRoja(0, ID_EQUIPO_LOCAL.intValue());
        roja.setTiempoMinuto(null);

        double[] resultado = predictionEngine.aplicarImpactoEventosEnVivo(
                2.0, 1.0, List.of(roja), ID_EQUIPO_LOCAL, ID_EQUIPO_VISITANTE);

        // minutoRoja=0 -> fraccionRestante=1.0 -> impacto=0.30 (el máximo posible)
        assertEquals(1.4, resultado[0], 0.001);
        assertEquals(1.15, resultado[1], 0.001);
    }

    @Test
    void aplicarImpactoEventosEnVivo_TarjetaAmarilla_NoAfectaLambdas() {
        EventoPartidoDTO amarilla = tarjetaRoja(10, ID_EQUIPO_LOCAL.intValue());
        amarilla.setDetalle("Yellow Card");

        double[] resultado = predictionEngine.aplicarImpactoEventosEnVivo(
                2.0, 1.0, List.of(amarilla), ID_EQUIPO_LOCAL, ID_EQUIPO_VISITANTE);

        assertArrayEquals(new double[]{2.0, 1.0}, resultado, 0.0001);
    }

    @Test
    void aplicarImpactoEventosEnVivo_RojaSinEquipoAsociado_NoAfectaNingunLambda() {
        EventoPartidoDTO roja = tarjetaRoja(10, null);

        double[] resultado = predictionEngine.aplicarImpactoEventosEnVivo(
                2.0, 1.0, List.of(roja), ID_EQUIPO_LOCAL, ID_EQUIPO_VISITANTE);

        assertArrayEquals(new double[]{2.0, 1.0}, resultado, 0.0001);
    }

    // ---- obtenerMultiplicadorDixonColes (regresión: lambda/mu invertidas en 1-0 y 0-1) ----

    private double invocarDixonColes(double lambda, double mu, int golesLocal, int golesVisitante) throws Exception {
        java.lang.reflect.Method metodo = PredictionEngineServiceImpl.class.getDeclaredMethod(
                "obtenerMultiplicadorDixonColes", double.class, double.class, int.class, int.class);
        metodo.setAccessible(true);
        return (double) metodo.invoke(predictionEngine, lambda, mu, golesLocal, golesVisitante);
    }

    @Test
    void obtenerMultiplicadorDixonColes_ScorelineCruzado1_0_UsaLaTasaDelEquipoQueNoMarco() throws Exception {
        // Regresión Dixon & Coles (1997): en el 1-0 la corrección tau debe usar mu (la tasa
        // del VISITANTE, que no marcó), no lambda (la tasa del que sí marcó) — estaban invertidas.
        double lambda = 1.4;
        double mu = 0.9;

        double multiplicador = invocarDixonColes(lambda, mu, 1, 0);

        assertEquals(1.0 + (mu * -0.15), multiplicador, 0.0001);
        assertNotEquals(1.0 + (lambda * -0.15), multiplicador, 0.0001,
                "Con lambda != mu, usar la tasa equivocada (lambda) daría un valor distinto");
    }

    @Test
    void obtenerMultiplicadorDixonColes_ScorelineCruzado0_1_UsaLaTasaDelEquipoQueNoMarco() throws Exception {
        // Regresión Dixon & Coles (1997): en el 0-1 la corrección tau debe usar lambda (la tasa
        // del LOCAL, que no marcó), no mu (la tasa del que sí marcó) — estaban invertidas.
        double lambda = 1.4;
        double mu = 0.9;

        double multiplicador = invocarDixonColes(lambda, mu, 0, 1);

        assertEquals(1.0 + (lambda * -0.15), multiplicador, 0.0001);
        assertNotEquals(1.0 + (mu * -0.15), multiplicador, 0.0001,
                "Con lambda != mu, usar la tasa equivocada (mu) daría un valor distinto");
    }

    @Test
    void obtenerMultiplicadorDixonColes_OtrosScorelines_CubreCadaRama() throws Exception {
        double lambda = 1.4;
        double mu = 0.9;

        assertEquals(1.0 - (lambda * mu * -0.15), invocarDixonColes(lambda, mu, 0, 0), 0.0001);
        assertEquals(1.0 - (-0.15), invocarDixonColes(lambda, mu, 1, 1), 0.0001);
        assertEquals(1.0, invocarDixonColes(lambda, mu, 3, 2), 0.0001,
                "Fuera de 0-0/1-0/0-1/1-1 no hay corrección Dixon-Coles");
    }

    @Test
    void aplicarImpactoEventosEnVivo_RojaConEquipoSinIdEquipo_NoLanzaExcepcionYNoAfectaLambdas() {
        EventoPartidoDTO roja = tarjetaRoja(10, ID_EQUIPO_LOCAL.intValue());
        // Equipo presente pero malformado: sin idEquipo. No debe provocar NPE.
        roja.setEquipo(EquipoDTO.builder().build());

        double[] resultado = assertDoesNotThrow(() -> predictionEngine.aplicarImpactoEventosEnVivo(
                2.0, 1.0, List.of(roja), ID_EQUIPO_LOCAL, ID_EQUIPO_VISITANTE));

        assertArrayEquals(new double[]{2.0, 1.0}, resultado, 0.0001);
    }
}