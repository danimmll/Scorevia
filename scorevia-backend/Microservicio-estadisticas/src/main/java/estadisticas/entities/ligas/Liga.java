package estadisticas.entities.ligas;

import jakarta.persistence.*;
import lombok.*;

/**
 * Liga soportada por la plataforma (ej. La Liga, Premier League).
 * El id coincide con el id que usa la API externa (API-Football), no es autogenerado:
 * se asigna en el momento de la ingesta para no tener que mapear ids entre sistemas.
 *
 * @author Daniel
 * @version 1.0
 */
@Entity
@Table(name = "ligas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Liga {

    @Id
    @Column(name = "id_liga")
    private Integer idLiga;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "pais")
    private String pais;

    @Column(name = "temporada_actual")
    private Integer temporadaActual;

    @Builder.Default
    @Column(name = "activa")
    private Boolean activa = true;

    @Column(name = "logo", columnDefinition = "TEXT")
    private String logo;

    @Column(name = "bandera", columnDefinition = "TEXT")
    private String bandera;

    // ------------------------------------------------------------------ cobertura ----
    //
    // Qué publica API-Football de esta competición. Viene gratis en /leagues (dentro de
    // seasons[].coverage) y decide si merece la pena gastar peticiones en un partido suyo:
    // de los 52.280 partidos de 2026 acabados en la base, 46.311 no tienen estadísticas
    // porque su competición sencillamente no las da (amistosos de clubes, filiales, ligas
    // regionales...). Sin este dato, la ingesta automática pediría stats de todos ellos y
    // se comería la cuota del día para guardar cero filas.
    //
    // null = no se sabe todavía; en ese caso se intenta igual y el resultado se aprende
    // por la vía dura (stats_not_found del partido).

    @Column(name = "cobertura_stats_partido")
    private Boolean coberturaStatsPartido;

    @Column(name = "cobertura_stats_jugadores")
    private Boolean coberturaStatsJugadores;

    @Column(name = "cobertura_eventos")
    private Boolean coberturaEventos;

    @Column(name = "cobertura_alineaciones")
    private Boolean coberturaAlineaciones;

    /**
     * Si la competición publica clasificación.
     *
     * <p>Llega en la misma respuesta de {@code /leagues} que las otras cuatro, un nivel más
     * arriba ({@code coverage.standings} en vez de {@code coverage.fixtures.*}), así que
     * saberlo no cuesta ni una petición más. Sirve para que una tabla vacía diga "esta
     * competición no publica clasificación" en vez de dejar al que mira pensando que se ha
     * roto algo: de las 1.244 competiciones de la base, la mayoría son copas y amistosos que
     * no tienen tabla que publicar.</p>
     */
    @Column(name = "cobertura_clasificacion")
    private Boolean coberturaClasificacion;

    /** Cuándo se leyó por última vez la cobertura de /leagues. Null si nunca. */
    @Column(name = "cobertura_actualizada")
    private java.time.LocalDateTime coberturaActualizada;

    @PrePersist
    public void prePersist() {
        if (activa == null) {
            activa = true;
        }
    }
}
