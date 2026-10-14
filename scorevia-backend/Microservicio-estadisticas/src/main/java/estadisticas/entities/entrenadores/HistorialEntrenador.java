package estadisticas.entities.entrenadores;

import estadisticas.entities.equipos.Equipo;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "historial_entrenadores", schema = "sch_estadisticas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistorialEntrenador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historial")
    private Long idHistorial;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_entrenador", nullable = false)
    private Entrenador entrenador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_equipo", nullable = false)
    private Equipo equipo;

    @Column(name = "temporada", nullable = false)
    private Integer temporada;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;
}
