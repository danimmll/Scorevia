package estadisticas.dto.external;

import java.util.List;

import lombok.Data;

/**
 * Respuesta de {@code /trophies?coachs=id-id-id}, que agrupa el palmares por entrenador.
 *
 * <p>No es la misma forma que {@code /trophies?coach=id}: en singular la respuesta es la lista
 * de trofeos a pelo, y en plural es una lista de entrenadores, cada uno con la suya. Por eso
 * hay dos DTO y no uno con un campo de mas.</p>
 *
 * <p>La version en plural admite <b>20 ids por peticion</b>, comprobado contra la API el
 * 2026-09-08 (con 21 contesta "Maximum of 20 ids allowed"). Para los 27.123 entrenadores eso
 * son 1.357 peticiones en vez de 27.123: un 1% de la cuota diaria en lugar de un 18%.</p>
 */
@Data
public class ApiTrophiesPorEntrenadorDTO {

    private List<EntrenadorConTrofeosDTO> response;

    @Data
    public static class EntrenadorConTrofeosDTO {
        /** Id del entrenador, el mismo que usa API-Football. */
        private Integer id;
        private List<ApiTrophiesResponseDTO.TrophyDTO> trophies;
    }
}
