package predicciones.services.predictionengine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import predicciones.dto.internal.EventoPartidoDTO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class PredictionEngineServiceImpl implements PredictionEngineService {

    // Límite de goles a calcular en la matriz para no saturar la CPU
    private static final int MAX_GOLES = 8;

    // Parámetro Rho (dependencia) típico en fútbol para el ajuste Dixon-Coles
    private static final double RHO_DIXON_COLES = -0.15;

    @Override
    public Map<String, BigDecimal> calcularProbabilidadesPartido(double lambdaLocal, double lambdaVisitante) {
        return calcularProbabilidades(lambdaLocal, lambdaVisitante, 0, 0);
    }

    @Override
    public Map<String, BigDecimal> calcularProbabilidades(double lambdaLocalRestante, double lambdaVisitanteRestante, int golesLocalActual, int golesVisitanteActual) {

        double probVictoriaLocal = 0.0;
        double probEmpate = 0.0;
        double probVictoriaVisitante = 0.0;

        for (int golesLocalFuturos = 0; golesLocalFuturos <= MAX_GOLES; golesLocalFuturos++) {
            for (int golesVisitanteFuturos = 0; golesVisitanteFuturos <= MAX_GOLES; golesVisitanteFuturos++) {

                double probPoissonPura = poisson(lambdaLocalRestante, golesLocalFuturos) * poisson(lambdaVisitanteRestante, golesVisitanteFuturos);
                double multiplicador = obtenerMultiplicadorDixonColes(lambdaLocalRestante, lambdaVisitanteRestante, golesLocalFuturos, golesVisitanteFuturos);
                double probCombinacion = probPoissonPura * multiplicador;

                int marcadorFinalLocal = golesLocalActual + golesLocalFuturos;
                int marcadorFinalVisitante = golesVisitanteActual + golesVisitanteFuturos;

                if (marcadorFinalLocal > marcadorFinalVisitante) {
                    probVictoriaLocal += probCombinacion;
                } else if (marcadorFinalLocal == marcadorFinalVisitante) {
                    probEmpate += probCombinacion;
                } else {
                    probVictoriaVisitante += probCombinacion;
                }
            }
        }

        double total = probVictoriaLocal + probEmpate + probVictoriaVisitante;

        if (total == 0.0) {
            total = 1.0;
            probEmpate = 1.0;
        }

        Map<String, BigDecimal> resultado = new HashMap<>();
        resultado.put("victoriaLocal", BigDecimal.valueOf((probVictoriaLocal / total) * 100).setScale(2, RoundingMode.HALF_UP));
        resultado.put("empate", BigDecimal.valueOf((probEmpate / total) * 100).setScale(2, RoundingMode.HALF_UP));
        resultado.put("victoriaVisitante", BigDecimal.valueOf((probVictoriaVisitante / total) * 100).setScale(2, RoundingMode.HALF_UP));

        return resultado;
    }

    private double obtenerMultiplicadorDixonColes(double lambda, double mu, int golesLocal, int golesVisitante) {
        // Fórmula tau de Dixon & Coles (1997): en los marcadores cruzados 1-0 y 0-1 la
        // corrección usa la tasa del equipo que NO marcó (mu para el 1-0, lambda para el
        // 0-1), no la del que marcó. Estaban invertidas.
        if (golesLocal == 0 && golesVisitante == 0) {
            return 1.0 - (lambda * mu * RHO_DIXON_COLES);
        } else if (golesLocal == 1 && golesVisitante == 0) {
            return 1.0 + (mu * RHO_DIXON_COLES);
        } else if (golesLocal == 0 && golesVisitante == 1) {
            return 1.0 + (lambda * RHO_DIXON_COLES);
        } else if (golesLocal == 1 && golesVisitante == 1) {
            return 1.0 - RHO_DIXON_COLES;
        }
        return 1.0;
    }

    private double poisson(double lambda, int k) {
        if (lambda <= 0) return k == 0 ? 1.0 : 0.0;
        return (Math.pow(lambda, k) * Math.exp(-lambda)) / factorial(k);
    }

    private long factorial(int n) {
        if (n == 0) return 1;
        long fact = 1;
        for (int i = 1; i <= n; i++) {
            fact *= i;
        }
        return fact;
    }

    @Override
    public double calcularProporcionTiempoRestante(int minutoActual) {
        if (minutoActual >= 90) return 0.0;
        double proporcionJugada = Math.pow(minutoActual / 90.0, 1.25);
        return 1.0 - proporcionJugada;
    }

    private static final double PESO_ROJA_MAX = 0.30;

    /**
     * NUEVO: Ajusta los Goles Esperados (Lambdas) en función de las rojas en directo.
     */
    public double[] aplicarImpactoEventosEnVivo(double lambdaLocal, double lambdaVisitante,
                                                List<EventoPartidoDTO> eventos,
                                                Long idEquipoLocal,
                                                Long idEquipoVisitante) {

        double[] lambdas = {lambdaLocal, lambdaVisitante};

        if (eventos == null || eventos.isEmpty()) {
            return lambdas;
        }

        for (EventoPartidoDTO evento : eventos) {
            if (esTarjetaRojaDirecta(evento)) {
                aplicarImpactoTarjetaRoja(evento, lambdas, idEquipoLocal, idEquipoVisitante);
            }
        }

        return lambdas;
    }

    private boolean esTarjetaRojaDirecta(EventoPartidoDTO evento) {
        return "Card".equalsIgnoreCase(evento.getTipoEvento())
                && "Red Card".equalsIgnoreCase(evento.getDetalle())
                && evento.getEquipo() != null
                && evento.getEquipo().getIdEquipo() != null;
    }

    private void aplicarImpactoTarjetaRoja(EventoPartidoDTO evento, double[] lambdas,
                                            Long idEquipoLocal, Long idEquipoVisitante) {
        int minutoRoja = (evento.getTiempoMinuto() != null ? evento.getTiempoMinuto() : 0)
                + (evento.getTiempoExtra() != null ? evento.getTiempoExtra() : 0);
        double fraccionPartidoRestante = Math.max(0, (90.0 - minutoRoja) / 90.0);
        double impacto = fraccionPartidoRestante * PESO_ROJA_MAX;

        long idEquipoEvento = evento.getEquipo().getIdEquipo().longValue();
        if (idEquipoLocal.equals(idEquipoEvento)) {
            lambdas[0] *= (1 - impacto);
            lambdas[1] *= (1 + (impacto * 0.5));
        } else if (idEquipoVisitante.equals(idEquipoEvento)) {
            lambdas[1] *= (1 - impacto);
            lambdas[0] *= (1 + (impacto * 0.5));
        }
    }
}