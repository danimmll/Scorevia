package estadisticas.entities.equipos;

import estadisticas.entities.jugadores.Jugador;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "traspasos", schema = "sch_estadisticas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Traspaso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_traspaso")
    private Long idTraspaso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_jugador", nullable = false)
    private Jugador jugador;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_equipo_origen")
    private Equipo equipoOrigen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_equipo_destino")
    private Equipo equipoDestino;

    @Column(name = "tipo", length = 100)
    private String tipo;

    @Column(name = "coste", length = 100)
    private String coste;
}