package predicciones.services.predictionengine;

import predicciones.dto.internal.EventoPartidoDTO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface PredictionEngineService {

    Map<String, BigDecimal> calcularProbabilidadesPartido(double expectedGoalsLocal, double expectedGoalsVisitante);

    Map<String, BigDecimal> calcularProbabilidades(double lambdaLocalRestante, double lambdaVisitanteRestante, int golesLocalActual, int golesVisitanteActual);

    double calcularProporcionTiempoRestante(int minutoActual);

    /**
     * Ajusta los Goles Esperados (Lambdas) en función del caos del directo (Tarjetas Rojas).
     */
    double[] aplicarImpactoEventosEnVivo(double lambdaLocal, double lambdaVisitante,
                                         List<EventoPartidoDTO> eventos,
                                         Long idEquipoLocal,
                                         Long idEquipoVisitante);
}