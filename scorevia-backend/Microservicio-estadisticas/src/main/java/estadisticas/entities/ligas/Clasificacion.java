package estadisticas.entities.ligas;

import estadisticas.entities.equipos.Equipo;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "clasificaciones")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Clasificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_clasificacion")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_liga", nullable = false)
    private Liga liga;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_equipo", nullable = false)
    private Equipo equipo;

    @Column(name = "temporada")
    private Integer temporada;

    @Column(name = "posicion")
    private Integer posicion;

    @Column(name = "puntos")
    private Integer puntos;

    @Column(name = "partidos_jugados")
    private Integer partidosJugados;

    @Column(name = "partidos_ganados")
    private Integer partidosGanados;

    @Column(name = "partidos_empatados")
    private Integer partidosEmpatados;

    @Column(name = "partidos_perdidos")
    private Integer partidosPerdidos;

    @Column(name = "goles_favor")
    private Integer golesFavor;

    @Column(name = "goles_contra")
    private Integer golesContra;

    @Column(name = "diferencia_goles")
    private Integer diferenciaGoles;

    @Column(name = "estado_competicion", length = 500)
    private String estadoCompeticion;

    /**
     * Grupo o fase dentro de la competición ("Group A", "League Phase"...).
     *
     * <p>Null en una liga de tabla única. Imprescindible en cualquier competición con varios
     * grupos: sin él, la Europa League 2016 son 47 filas con solo 4 posiciones distintas
     * -- doce equipos "primeros" mezclados y ninguna forma de saber de qué grupo es cada
     * uno, así que la tabla no se puede pintar.</p>
     */
    @Column(name = "grupo", length = 100)
    private String grupo;
}