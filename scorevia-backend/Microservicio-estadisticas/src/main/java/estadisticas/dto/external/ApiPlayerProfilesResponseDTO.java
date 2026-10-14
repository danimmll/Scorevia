package estadisticas.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiPlayerProfilesResponseDTO {
    private PagingDTO paging;
    private List<PlayerProfileDTO> response;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PagingDTO {
        private int current;
        private int total;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlayerProfileDTO {
        private PlayerData player;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlayerData {
        private Integer id;
        private String name;
        private String firstname;
        private String lastname;
        private String nationality;
        private String height;
        private String weight;
        private Birth birth;
        private String photo;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Birth {
        private String date;
    }
}