package predicciones.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistorialBajaDTO {
    private Long idBaja;
    private Integer idJugador;
    private String tipo;
    private String causa;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private Boolean activo;
}