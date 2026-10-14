package estadisticas.services.external;

import estadisticas.entities.partidos.Partido;

/**
 * Servicio encargado de descargar y persistir el timeline de eventos
 * (goles, tarjetas, cambios) de un partido, tanto para partidos ya
 * finalizados como para partidos en vivo.
 *
 * @author Daniel
 * @version 1.0
 */
public interface EventoPartidoIngestService {

    /**
     * Descarga desde API-Football y persiste los eventos de un partido.
     * Es best-effort: si la competición/temporada no tiene cobertura de eventos
     * en la API, no lanza excepción, simplemente no guarda nada.
     * <p>
     * Idempotente: borra y reinserta los eventos existentes del partido en cada
     * llamada, para que un re-sync (o una nueva llamada mientras el partido sigue
     * en vivo) no duplique eventos y refleje correcciones de la API (VAR, etc.).
     * </p>
     *
     * @param partido Partido para el que se descargan los eventos (debe tener
     *                equipoLocal/equipoVisitante ya resueltos).
     */
    void ingestarEventosDePartido(Partido partido);

    /**
     * Guarda un timeline que ya se tiene en la mano, sin pedirle nada a API-Football.
     *
     * <p>Existe porque el timeline llega <b>gratis</b> dentro de otras respuestas que ya se
     * están pidiendo: {@code /fixtures?live=all} incluye los eventos de cada partido en
     * directo, y {@code /fixtures?ids=} los de cada partido pedido. Antes se bajaba otra vez
     * con {@code /fixtures/events}, una petición por partido, teniéndolos ya delante.</p>
     *
     * <p>Misma semántica que {@link #ingestarEventosDePartido}: borra y reinserta, de forma
     * que repetirlo no duplica y recoge las correcciones (VAR, goles anulados).</p>
     *
     * @param partido partido al que pertenecen.
     * @param eventos timeline tal cual viene de la API; vacío o null cuenta como "todavía no
     *                hay eventos" y se anota como intento sin datos.
     */
    void guardarEventos(Partido partido, java.util.List<estadisticas.dto.external.ApiFootballEventsResponseDTO.EventItemDTO> eventos);

    /**
     * Como {@link #guardarEventos}, pero deja el guardado del partido a quien llame.
     *
     * <p>Lo usa la ingesta de un partido completo, que escribe estadísticas y eventos a la vez:
     * {@code Partido} tiene bloqueo optimista, y guardarlo dos veces seguidas estando
     * desasociado hacía que el segundo guardado fuera con la versión vieja y diera la ingesta
     * por fallida justo después de haber ido bien.</p>
     *
     * @return true si había timeline que guardar.
     */
    boolean guardarEventosSinPersistirPartido(Partido partido,
                                              java.util.List<estadisticas.dto.external.ApiFootballEventsResponseDTO.EventItemDTO> eventos);

    /**
     * Busca partidos antiguos que ya tienen stats pero carecen de eventos y los descarga.
     * @return Número de partidos procesados en esta ejecución.
     */
    int ingestarEventosFaltantesHistoricos();
}