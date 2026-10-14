package estadisticas.dto.equipos;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * La temporada entera de un equipo: quien la jugo, quien la dirigio y como acabo.
 *
 * <p>Todo sale de datos ya ingeridos —partidos, clasificaciones e historial de entrenadores—,
 * asi que la pantalla <b>no cuesta ninguna peticion</b> a API-Football.</p>
 *
 * <p>La plantilla se saca de los partidos que se jugaron de verdad y no del historial de
 * dorsales: asi entra cualquiera que llegase a jugar, aunque no figure en la plantilla
 * registrada, que es justo lo que pasa con los canteranos que suben sueltos.</p>
 *
 * @author Daniel
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resumen completo de la temporada de un equipo")
public class TemporadaEquipoDTO {

    private Integer idEquipo;
    private String equipo;
    private String escudo;
    private Integer temporada;

    /** Puede haber mas de uno: en una temporada con destitucion aparecen los dos. */
    @Schema(description = "Entrenadores que dirigieron al equipo esa temporada")
    private List<EntrenadorTemporadaDTO> entrenadores;

    @Schema(description = "Competiciones que jugo, con su puesto final")
    private List<CompeticionTemporadaDTO> competiciones;

    @Schema(description = "Competiciones que gano (puesto 1 en la clasificacion final)")
    private List<CompeticionTemporadaDTO> titulos;

    @Schema(description = "Los jugadores que participaron, de mas a menos minutos")
    private List<JugadorTemporadaDTO> plantilla;

    @Schema(description = "Sumas de toda la plantilla en la temporada")
    private TotalesDTO totales;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EntrenadorTemporadaDTO {
        private Integer idEntrenador;
        private String nombre;
        private String foto;
        /**
         * La temporada del registro del que sale.
         *
         * <p>No siempre coincide con la que se esta mirando: el historial guarda la temporada
         * en que <b>empieza</b> cada etapa, no todas las que cubre, asi que al pedir 2024 la
         * respuesta puede venir de la etapa que arranco en 2021. Se manda para que la interfaz
         * pueda decir "desde 2021" en vez de dar a entender que consta esa temporada exacta.</p>
         */
        private Integer temporadaRegistro;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompeticionTemporadaDTO {
        private Integer idLiga;
        private String nombre;
        private String logo;
        private String bandera;
        private Integer posicion;
        private Integer puntos;
        private Integer partidosJugados;
        /** El grupo, en las competiciones que los tienen. Null en una liga normal. */
        private String grupo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JugadorTemporadaDTO {
        private Integer idJugador;
        private String nombre;
        private String foto;
        private String posicion;
        private Integer partidos;
        private Integer titularidades;
        private Integer minutos;
        private Integer goles;
        private Integer asistencias;
        private Integer amarillas;
        private Integer rojas;
        private Double valoracionMedia;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TotalesDTO {
        private Integer jugadoresUtilizados;
        private Integer goles;
        private Integer asistencias;
        private Integer amarillas;
        private Integer rojas;
    }
}
