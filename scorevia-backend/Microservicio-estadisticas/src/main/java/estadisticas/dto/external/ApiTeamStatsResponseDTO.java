package estadisticas.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiTeamStatsResponseDTO {
    private Integer results;
    private Object errors;
    private List<TeamStatsWrapper> response;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TeamStatsWrapper {
        private TeamInfoDTO team;
        private List<StatisticItem> statistics;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StatisticItem {
        private String type;
        private Object value;
    }
}