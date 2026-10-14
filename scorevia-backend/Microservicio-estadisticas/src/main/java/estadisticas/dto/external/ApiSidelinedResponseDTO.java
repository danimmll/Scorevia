package estadisticas.dto.external;

import lombok.Data;
import java.util.List;

@Data
public class ApiSidelinedResponseDTO {
    private List<SidelinedDTO> response;

    @Data
    public static class SidelinedDTO {
        private ApiTrophiesResponseDTO.PlayerInfoDTO player;
        private String type;
        private String start;
        private String end;
    }
}