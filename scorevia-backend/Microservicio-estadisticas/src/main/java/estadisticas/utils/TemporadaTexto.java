package estadisticas.utils;

import java.util.Comparator;

/**
 * Ordena temporadas que llegan como texto, que es como las manda API-Football.
 *
 * <p>En {@code trofeos.temporada} conviven dos formatos y un hueco:</p>
 * <ul>
 *   <li>{@code "2023/2024"} — ligas europeas, que cruzan el año natural</li>
 *   <li>{@code "2023"} — ligas de calendario natural y torneos de seleccion</li>
 *   <li>{@code null} o cadena vacia — <b>108.788 de 560.212 filas</b>, medido sobre la base</li>
 * </ul>
 *
 * <p>Que casi una de cada cinco no tenga temporada es justo lo que obliga a tener esto: con un
 * {@code ORDER BY temporada DESC} a secas, Postgres pone los nulos <b>primero</b>, asi que una
 * linea del tiempo de titulos empezaria por los que no se sabe cuando fueron. Aqui van al
 * final, que es donde no estorban.</p>
 *
 * @author Daniel
 * @version 1.0
 */
public final class TemporadaTexto {

    /** Las que no tienen temporada se van al final, no al principio. */
    private static final int SIN_TEMPORADA = Integer.MIN_VALUE;

    private TemporadaTexto() {
        // Utilidad, no se instancia.
    }

    /**
     * El ano en que empieza la temporada, para poder compararlas.
     *
     * <p>Se queda con los cuatro primeros digitos: "2023/2024" y "2023" dan los dos 2023, que
     * es lo correcto —son la misma temporada escrita de dos formas segun la competicion—.</p>
     *
     * @return el ano, o {@link #SIN_TEMPORADA} si no hay forma de sacarlo.
     */
    public static int anioDeInicio(String temporada) {
        if (temporada == null || temporada.length() < 4) {
            return SIN_TEMPORADA;
        }
        String prefijo = temporada.substring(0, 4);
        for (int i = 0; i < 4; i++) {
            if (!Character.isDigit(prefijo.charAt(i))) {
                return SIN_TEMPORADA;
            }
        }
        return Integer.parseInt(prefijo);
    }

    /** De la mas reciente a la mas antigua, con las de temporada desconocida al final. */
    public static <T> Comparator<T> masRecientePrimero(java.util.function.Function<T, String> temporadaDe) {
        return Comparator.comparingInt((T item) -> anioDeInicio(temporadaDe.apply(item))).reversed();
    }
}
