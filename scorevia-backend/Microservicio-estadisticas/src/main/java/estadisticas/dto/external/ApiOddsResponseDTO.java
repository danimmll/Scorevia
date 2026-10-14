package estadisticas.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Respuesta de {@code /odds} (cuotas previas al partido).
 *
 * <p>La forma es: una entrada por partido, con una lista de casas de apuestas, cada una con
 * sus mercados ({@code bets}) y cada mercado con sus opciones ({@code values}). O sea que en
 * una sola petición vienen todas las casas y todos los mercados de ese partido.</p>
 *
 * <p>Según la documentación, las cuotas están disponibles entre 14 días y 1 día antes del
 * partido, se refrescan cada 3 horas y se guardan 7 días de histórico. Pasado eso, la
 * respuesta viene vacía: por eso no tiene sentido preguntar por un partido antiguo.</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiOddsResponseDTO {

    private List<OddsFixtureDTO> response;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OddsFixtureDTO {
        private FixtureRefDTO fixture;
        /** Cuándo se actualizaron por última vez, en ISO-8601. */
        private String update;
        private List<BookmakerDTO> bookmakers;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FixtureRefDTO {
        private Integer id;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BookmakerDTO {
        private Integer id;
        private String name;
        private List<BetDTO> bets;
    }

    /** Un mercado: "Match Winner", "Goals Over/Under", "Both Teams Score"... */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BetDTO {
        private Integer id;
        private String name;
        private List<BetValueDTO> values;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BetValueDTO {
        /** "Home", "Draw", "Away", "Over 2.5", "Yes"... */
        private String value;
        /** La cuota, como texto ("1.975"). */
        private String odd;
    }
}
