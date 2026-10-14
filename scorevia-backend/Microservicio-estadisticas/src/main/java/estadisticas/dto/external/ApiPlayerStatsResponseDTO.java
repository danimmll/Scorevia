package estadisticas.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiPlayerStatsResponseDTO {
    private Integer results;
    private Object errors;
    private List<TeamPlayersWrapper> response;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TeamPlayersWrapper {
        private TeamInfoDTO team;
        private List<PlayerStatsWrapper> players;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlayerStatsWrapper {
        private PlayerInfoDTO player;
        private List<PlayerDetailStats> statistics;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlayerInfoDTO {
        private Integer id;
        private String name;
        private String photo;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlayerDetailStats {
        private Games games;
        private Goals goals;
        private Cards cards;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Games {
            private Integer minutes;
            private String rating;
            private Boolean substitute;
        }

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Goals {
            private Integer total;
            private Integer assists;
        }

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Cards {
            private Integer yellow;
            private Integer red;
        }
    }
}