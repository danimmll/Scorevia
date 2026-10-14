package estadisticas.dto.external;

import lombok.Data;

/**
 * El marcador desglosado por fases que trae cada fixture.
 *
 * <p>Hoy solo se guarda {@code goals} (el marcador corriente), y eso pierde información en dos
 * casos que sí importan: una eliminatoria decidida en los penaltis queda como empate, y no hay
 * forma de saber el resultado al descanso.</p>
 */
@Data
public class ScoreDTO {
    private GoalsInfoDTO halftime;
    private GoalsInfoDTO fulltime;
    private GoalsInfoDTO extratime;
    private GoalsInfoDTO penalty;
}
