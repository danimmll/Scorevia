package estadisticas.entities.partidos;

import estadisticas.entities.jugadores.Jugador;
import estadisticas.entities.equipos.Equipo;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "eventos_partido", schema = "sch_estadisticas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoPartido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evento")
    private Long idEvento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_partido", nullable = false)
    private Partido partido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_equipo", nullable = false)
    private Equipo equipo;

    @Column(name = "tiempo_minuto", nullable = false)
    private Integer tiempoMinuto;

    @Column(name = "tiempo_extra")
    private Integer tiempoExtra;

    @Column(name = "tipo_evento", nullable = false, length = 50)
    private String tipoEvento; // Goal, Card, subst

    @Column(name = "detalle", length = 100)
    private String detalle; // Normal Goal, Yellow Card

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_jugador_principal")
    private Jugador jugadorPrincipal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_jugador_secundario")
    private Jugador jugadorSecundario;
}