package estadisticas.dto.external;

import lombok.Data;
import java.util.List;

@Data
public class ApiTrophiesResponseDTO {
    private List<TrophyDTO> response;

    @Data
    public static class TrophyDTO {
        private PlayerInfoDTO player;
        private String league;
        private String country;
        private String season;
        private String place;
    }

    @Data
    public static class PlayerInfoDTO {
        private Integer id;
        private String name;
    }
}