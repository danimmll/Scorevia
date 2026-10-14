package estadisticas.services.external;

public interface EstadisticasIngestService {
    /**
     * Busca los partidos finalizados sin estadísticas y las descarga de la API.
     * @return El número de partidos procesados exitosamente.
     */
    int ingestarEstadisticasPendientes();

    /**
     * Descarga las estadísticas (equipo + jugadores) y el timeline de UN partido.
     *
     * <p>Es la pieza que usa la ingesta automática en cuanto un partido acaba: el resto de
     * la clase trabaja por lotes sobre lo que encuentra pendiente en la base, y para reaccionar
     * al pitido final hace falta poder pedir un partido concreto.</p>
     *
     * <p>Respeta la cobertura de la competición: si {@code ligas.cobertura_stats_partido} dice
     * que ahí no hay estadísticas, no gasta ni una petición.</p>
     *
     * @param idPartido id del partido (el mismo que usa API-Football).
     * @return cómo acabó el intento, para que quien lo pidió sepa si reintentar.
     */
    ResultadoIngestaPartido ingestarEstadisticasDePartido(Integer idPartido);

    /**
     * Lo mismo que {@link #ingestarEstadisticasDePartido}, pero para varios partidos a la vez.
     *
     * <p>Es la forma barata de hacerlo: pedir fixtures por id admite <b>20 de golpe</b> y cada
     * uno viene con sus eventos, sus alineaciones y sus estadísticas de equipo y de jugador
     * dentro. Una petición por cada veinte partidos, en vez de tres por partido.</p>
     *
     * <p>La lista puede ser más larga de 20: se parte en tandas por dentro.</p>
     *
     * @return qué ha pasado con cada partido pedido, por su id.
     */
    java.util.Map<Integer, ResultadoIngestaPartido> ingestarLoteDePartidos(java.util.List<Integer> idsPartidos);

    /**
     * Red de seguridad del día: partidos acabados hace poco que siguen sin estadísticas.
     *
     * <p>La vía normal es el aviso en vivo (el partido cambia a FT y se encola), pero eso vive
     * en memoria: un reinicio del servicio, un partido que la API deja de listar en
     * {@code live=all} antes de darlo por acabado o un pico que desborde la cola dejarían
     * huecos. Esto los recoge.</p>
     *
     * <p>A diferencia de {@link #ingestarEstadisticasPendientes()}, que barre TODA la historia
     * (más de 42.000 partidos a día de hoy, la mayoría de competiciones sin cobertura), esto
     * mira solo una ventana reciente, salta las competiciones sin cobertura y tiene tope de
     * partidos: está pensado para correr solo, todos los días, sin vigilancia.</p>
     *
     * @param horas        cuánto hacia atrás mirar desde ahora.
     * @param maxPartidos  tope de partidos a procesar en esta pasada.
     * @return número de partidos con estadísticas guardadas.
     */
    int ingestarEstadisticasRecientes(int horas, int maxPartidos);

    /**
     * Trae el once de los partidos que se estan jugando y todavia no lo tienen.
     *
     * @return cuantos partidos han quedado con alineacion
     */
    /**
     * @param horasAntes cuanto se adelanta a los partidos que aun no han empezado
     */
    int guardarAlineacionesPendientes(int maxPartidos, int horasAntes);
}
