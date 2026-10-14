package predicciones.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LivePredictionDTO {
    private Integer idPartido;
    private Integer minutoActual;
    private Integer golesLocalActuales;
    private Integer golesVisitanteActuales;

    // Probabilidades en tiempo real
    private Map<String, BigDecimal> probabilidades;

    // Para depuración y para mostrar en el frontend la "ventaja" de tiempo
    private Double lambdaLocalRestante;
    private Double lambdaVisitanteRestante;
}