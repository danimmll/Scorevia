package estadisticas.entities.partidos;

import java.time.LocalDateTime;

import estadisticas.entities.arbitros.Arbitro;
import estadisticas.entities.equipos.Equipo;
import estadisticas.entities.ligas.Liga;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;
import lombok.*;

/**
 * Partido de futbol. Es la entidad central del proyecto: de aqui cuelgan las
 * estadisticas de equipo y de jugador, los eventos y las alineaciones.
 *
 * <p>Las columnas normalizadas (liga, equipos, arbitro, resultado) son las que se
 * usan para filtrar y cruzar datos de forma eficiente. {@code datosApi} guarda la
 * respuesta cruda de la API externa como JSONB, como caché de lo que aun no
 * se ha modelado en columnas propias, sin bloquear la ingesta por campos nuevos.</p>
 *
 * @author Daniel
 * @version 1.0
 */
@Entity
@Table(name = "partidos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Partido {

    @Id
    @Column(name = "id_partido")
    private Integer idPartido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_liga")
    private Liga liga;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_equipo_local")
    private Equipo equipoLocal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_equipo_visitante")
    private Equipo equipoVisitante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_arbitro")
    private Arbitro arbitro;

    @Column(name = "fecha")
    private LocalDateTime fecha;

    @Column(name = "jornada")
    private Integer jornada;

    @Column(name = "resultado_local")
    private Integer resultadoLocal;

    @Column(name = "resultado_visitante")
    private Integer resultadoVisitante;

    @Column(name = "estado")
    private String estado;

    /**
     * El codigo corto de API-Football tal cual: NS, 1H, HT, 2H, ET, BT, P, FT, AET, PEN...
     *
     * <p>{@link #estado} agrupa esos codigos en cuatro cajones (PROGRAMADO, EN_JUEGO,
     * FINALIZADO, APLAZADO) porque es lo que necesita el resto de la aplicacion para
     * decidir. Pero al agrupar se pierde justo lo que un marcador en directo tiene que
     * ensenar: un partido en el descanso y otro en el minuto 70 son los dos EN_JUEGO, y no
     * se pueden pintar igual.</p>
     */
    @Column(name = "estado_corto", length = 10)
    private String estadoCorto;

    /**
     * Minuto de juego, del {@code status.elapsed} de API-Football. Null si no esta en juego.
     *
     * <p>Se pone a null al terminar el partido a proposito: un "90'" congelado junto a un
     * resultado final es enganoso, parece que el partido sigue.</p>
     */
    @Column(name = "minuto")
    private Integer minuto;

    @Builder.Default
    @Column(name = "stats_not_found")
    private Boolean statsNotFound = false;

    @Builder.Default
    @Column(name = "intentos_ingesta")
    private Integer intentosIngesta = 0;

    @Builder.Default
    @Column(name = "eventos_not_found")
    private Boolean eventosNotFound = false;

    @Builder.Default
    @Column(name = "intentos_eventos")
    private Integer intentosEventos = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos_api", columnDefinition = "jsonb")
    private String datosApi;

    @Column(name = "temporada")
    private Integer temporada;

    /**
     * La fase, tal y como la manda API-Football: "Regular Season - 12", "Quarter-finals",
     * "Group Stage - A", "Final"...
     *
     * <p>Existe porque {@code jornada} solo puede guardar el número, y en una copa no lo hay:
     * de "Quarter-finals" no sale ninguno y el texto se perdía. Se guarda en crudo y es el
     * cliente quien decide cómo enseñarlo, que es donde se sabe en qué idioma.</p>
     */
    @Column(name = "ronda", length = 100)
    private String ronda;

    // ------------------------------------------------------- marcador desglosado ----
    //
    // El fixture trae, además del marcador corriente, cómo se llegó al descanso y la tanda de
    // penaltis. Sin esto una eliminatoria resuelta en los penaltis queda guardada como un
    // empate, que es justamente lo que no pasó.

    @Column(name = "resultado_local_descanso")
    private Integer resultadoLocalDescanso;

    @Column(name = "resultado_visitante_descanso")
    private Integer resultadoVisitanteDescanso;

    @Column(name = "penaltis_local")
    private Integer penaltisLocal;

    @Column(name = "penaltis_visitante")
    private Integer penaltisVisitante;

    // Lock optimista: la sincronización manual y el scheduler en vivo pueden solapar
    // dos escrituras del mismo partido casi a la vez; sin esto, la segunda pisaba a la
    // primera en silencio (ninguna corrupción de datos, pero sí llamadas a la API
    // desperdiciadas sin que nadie se enterase). Requiere la columna "version" en BD
    // (ddl-auto: validate no la crea sola).
    @Version
    @Column(name = "version")
    private Long version;
}
