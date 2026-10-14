package estadisticas.entities.partidos;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import estadisticas.entities.equipos.Equipo;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Estadisticas de un equipo en un partido concreto (posesion, tiros, tarjetas...).
 * Es una de las dos tablas de hechos sobre las que se construyen los filtros
 * cruzados del dashboard premium.
 *
 * @author Daniel
 * @version 1.0
 */
@Entity
@Table(name = "estadisticas_equipo_partido",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"id_partido", "id_equipo"}, name = "estequipopartido_UNIQUE")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EstadisticaEquipoPartido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_est_equipo")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_partido")
    private Partido partido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_equipo")
    private Equipo equipo;

    @Column(name = "posesion")
    private BigDecimal posesion;

    @Column(name = "tiros")
    private Integer tiros;

    @Column(name = "tiros_puerta")
    private Integer tirosPuerta;

    @Column(name = "corners")
    private Integer corners;

    @Column(name = "faltas")
    private Integer faltas;

    @Column(name = "tarjetas_amarillas")
    private Integer tarjetasAmarillas;

    @Column(name = "tarjetas_rojas")
    private Integer tarjetasRojas;

    @Column(name = "goles_esperados")
    private BigDecimal golesEsperados;

    /**
     * Dibujo oficial del equipo en este partido ("4-2-3-1"), tal cual lo publica API-Football
     * en el bloque {@code lineups} del fixture.
     *
     * <p>La columna existía desde el principio y estaba vacía en las 351.430 filas de la
     * tabla: la formación solo se podía sacar de {@code /fixtures/lineups}, que costaba una
     * petición por partido. Desde que los partidos se piden por id viene en la misma
     * respuesta, gratis.</p>
     */
    @Column(name = "sistema_tactico", length = 20)
    private String sistemaTactico;

    /**
     * Entrenador que dirigió a este equipo en este partido, según la alineación oficial.
     *
     * <p>Se guarda el id suelto y no una relación: el entrenador puede no estar todavía en
     * {@code entrenadores} (llega con id, nombre y foto dentro de la alineación, pero darlo de
     * alta es otra ingesta), y una FK tumbaría el guardado del partido entero por eso.</p>
     *
     * <p>Es lo que hace que la ficha de un partido pueda decir quién lo dirigió de verdad:
     * {@code historial_entrenadores} está vacía, así que hasta ahora se enseñaba el técnico
     * actual del club aunque el partido fuera de hace diez años.</p>
     */
    @Column(name = "id_entrenador")
    private Integer idEntrenador;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "estadisticas_extra", columnDefinition = "jsonb")
    private Map<String, String> estadisticasExtra = new HashMap<>();
}
