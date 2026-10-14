package estadisticas.dto.external;

import lombok.Data;
import java.util.List;

@Data
public class ApiFootballEventsResponseDTO {
    private List<EventItemDTO> response;

    @Data
    public static class EventItemDTO {
        private EventTimeDTO time;
        private TeamInfoDTO team;
        private ApiTrophiesResponseDTO.PlayerInfoDTO player;
        private ApiTrophiesResponseDTO.PlayerInfoDTO assist;
        private String type;
        private String detail;
        private String comments;
    }

    @Data
    public static class EventTimeDTO {
        private Integer elapsed;
        private Integer extra;
    }
}