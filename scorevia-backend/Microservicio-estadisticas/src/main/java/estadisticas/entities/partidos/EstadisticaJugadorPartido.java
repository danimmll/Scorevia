package estadisticas.entities.partidos;

import java.math.BigDecimal;

import estadisticas.entities.jugadores.Jugador;
import estadisticas.entities.equipos.Equipo;
import jakarta.persistence.*;
import lombok.*;

/**
 * Estadisticas de un jugador en un partido concreto (goles, asistencias, minutos...).
 * Junto con EstadisticaEquipoPartido, es la otra tabla de hechos clave del proyecto.
 *
 * @author Daniel
 * @version 1.0
 */
@Entity
@Table(name = "estadisticas_jugador_partido",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"id_partido", "id_jugador"}, name = "estjugadorpartido_UNIQUE")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EstadisticaJugadorPartido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_partido")
    private Partido partido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_jugador")
    private Jugador jugador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_equipo")
    private Equipo equipo;

    @Column(name = "minutos_jugados")
    private Integer minutosJugados;

    @Column(name = "goles")
    private Integer goles;

    @Column(name = "asistencias")
    private Integer asistencias;

    @Column(name = "tarjetas_amarillas")
    private Integer tarjetasAmarillas;

    @Column(name = "tarjetas_rojas")
    private Integer tarjetasRojas;

    @Column(name = "pases_completados")
    private Integer pasesCompletados;

    @Column(name = "pases_totales")
    private Integer pasesTotales;

    @Column(name = "valoracion")
    private BigDecimal valoracion;

    @Column(name = "titular")
    private Boolean titular;

    @Column(name = "posicion_campo", length = 50)
    private String posicionCampo;

    /**
     * Sitio en el campo dentro de la alineación oficial, como "fila:columna": la fila 1 es la
     * portería y la columna va de izquierda a derecha dentro de esa línea.
     *
     * <p>Con esto el once se puede dibujar tal y como salió, en vez de repartir a ojo a los
     * jugadores de cada línea. Null en los suplentes y en los partidos cuya competición no
     * publica alineaciones.</p>
     */
    @Column(name = "posicion_grid", length = 10)
    private String posicionGrid;
}
