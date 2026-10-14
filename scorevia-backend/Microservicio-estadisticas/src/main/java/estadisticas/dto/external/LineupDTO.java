package estadisticas.dto.external;

import lombok.Data;

import java.util.List;

/**
 * La alineación oficial de un equipo en un partido, tal y como la publica API-Football.
 *
 * <p>Llega dentro del propio fixture cuando se pide {@code /fixtures?id=} o
 * {@code /fixtures?ids=}, sin coste añadido. Es mejor dato que el que se puede deducir de las
 * estadísticas de jugador:</p>
 *
 * <ul>
 *   <li>{@code formation} es el dibujo <b>oficial</b> ("4-2-3-1"), no uno contado a mano.</li>
 *   <li>{@code coach} trae el entrenador <b>de ese partido</b>, con su id y su foto. Eso es
 *       justo lo que no se puede sacar de la base hoy: {@code historial_entrenadores} está
 *       vacío, así que la ficha de un partido enseña siempre el técnico actual del club.</li>
 *   <li>Cada jugador trae {@code grid} ("fila:columna"), o sea su sitio real en el campo, en
 *       vez de solo la línea G/D/M/F.</li>
 * </ul>
 *
 * <p>Según la documentación, las alineaciones aparecen entre 20 y 40 minutos antes del
 * partido en las competiciones que las cubren ({@code coverage.fixtures.lineups}); en el
 * resto, si llegan, lo hacen después del partido y con retraso variable.</p>
 */
@Data
public class LineupDTO {

    private TeamInfoDTO team;

    /** Dibujo oficial, "4-2-3-1". Null si la competición no lo publica. */
    private String formation;

    private CoachDTO coach;

    private List<JugadorAlineadoDTO> startXI;

    private List<JugadorAlineadoDTO> substitutes;

    @Data
    public static class CoachDTO {
        private Integer id;
        private String name;
        private String photo;
    }

    /** El envoltorio que usa la API: cada elemento del once es {@code {"player": {...}}}. */
    @Data
    public static class JugadorAlineadoDTO {
        private JugadorEnCampoDTO player;
    }

    @Data
    public static class JugadorEnCampoDTO {
        private Integer id;
        private String name;
        /** Dorsal en ese partido. */
        private Integer number;
        /** G, D, M o F. */
        private String pos;
        /**
         * Sitio en el campo como "fila:columna": la fila 1 es la portería y la columna va de
         * izquierda a derecha. Null en los suplentes.
         */
        private String grid;
    }
}
