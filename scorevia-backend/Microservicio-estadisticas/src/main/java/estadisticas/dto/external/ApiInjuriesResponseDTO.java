package estadisticas.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Respuesta de {@code /injuries}: quién se pierde un partido y por qué.
 *
 * <p>No confundir con {@code /sidelined}, que es el historial de lesiones de un jugador a lo
 * largo de su carrera (lo que llena {@code historial_bajas}). Esto es la lista de bajas de un
 * partido concreto, y admite hasta 20 partidos por petición.</p>
 *
 * <p>{@code type} distingue los dos casos que documenta la API: "Missing Fixture" (no juega) y
 * "Questionable" (duda, puede acabar jugando).</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiInjuriesResponseDTO {

    private List<InjuryItemDTO> response;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InjuryItemDTO {
        private InjuryPlayerDTO player;
        private TeamInfoDTO team;
        private FixtureRefDTO fixture;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InjuryPlayerDTO {
        private Integer id;
        private String name;
        private String photo;
        /** "Missing Fixture" o "Questionable". */
        private String type;
        /** "Injury", "Muscle Injury", "Suspended"... */
        private String reason;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FixtureRefDTO {
        private Integer id;
    }
}
