package estadisticas.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TeamsInfoDTO {

    private TeamInfoDTO home;
    private TeamInfoDTO away;
}
