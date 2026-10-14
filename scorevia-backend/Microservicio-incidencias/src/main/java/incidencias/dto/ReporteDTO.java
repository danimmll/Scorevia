package incidencias.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ReporteDTO {
    private Integer idReporte;
    private Integer idDenunciante;

    @NotNull(message = "El usuario denunciado es obligatorio")
    private Integer idDenunciado;

    // Columna es VARCHAR(255) -- sin este límite, Postgres lanza una excepción cruda (no
    // trunca) si se excede, y eso caía en el catch-all genérico como un 500 en vez de un 400
    // limpio con mensaje de validación.
    @NotBlank(message = "El motivo del reporte es obligatorio")
    @Size(max = 255, message = "El motivo no puede superar los 255 caracteres")
    private String motivo;

    private String estado;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fechaReporte;
    private String resolucion;
    private Integer idAdminResolutor;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fechaResolucion;
}