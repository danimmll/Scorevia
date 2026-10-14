package estadisticas.entities.jugadores;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "historial_bajas", schema = "sch_estadisticas")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class HistorialBaja {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_baja")
    private Long idBaja;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_jugador", nullable = false)
    private Jugador jugador;

    @Column(name = "tipo", nullable = false, length = 50)
    private String tipo;

    @Column(name = "causa")
    private String causa;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    @Column(name = "activo")
    private Boolean activo;
}