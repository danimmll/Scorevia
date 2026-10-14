package estadisticas.dto.external;

import lombok.Data;
import java.util.List;

@Data
public class ApiTransfersResponseDTO {
    private List<TransferWrapperDTO> response;

    @Data
    public static class TransferWrapperDTO {
        private PlayerInfoDTO player;
        private String update;
        private List<TransferDetailDTO> transfers;
    }

    @Data
    public static class PlayerInfoDTO {
        private Integer id;
        private String name;
    }

    @Data
    public static class TransferDetailDTO {
        private String date;
        private String type;
        private TeamTransferDTO teams;
    }

    @Data
    public static class TeamTransferDTO {
        private TeamInfoDTO in;
        private TeamInfoDTO out;
    }
}