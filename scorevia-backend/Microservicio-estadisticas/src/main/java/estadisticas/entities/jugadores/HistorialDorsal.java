package estadisticas.entities.jugadores;

import estadisticas.entities.equipos.Equipo;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "historial_dorsales", schema = "sch_estadisticas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistorialDorsal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historial")
    private Long idHistorial;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_jugador", nullable = false)
    private Jugador jugador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_equipo", nullable = false)
    private Equipo equipo;

    @Column(name = "temporada", nullable = false)
    private Integer temporada;

    @Column(name = "dorsal")
    private Integer dorsal;

    @Column(name = "posicion", length = 30)
    private String posicion;

    @Column(name = "id_partido")
    private Integer idPartido;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;
}