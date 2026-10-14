package estadisticas.services.external;

/**
 * Motor de ingesta de partidos: llama a API-Football y vuelca fixtures en el
 * esquema normalizado (ligas, equipos, arbitros, partidos).
 *
 * @author Daniel
 * @version 1.0
 */
public interface PartidoIngestService {

    /**
     * @param fecha formato yyyy-MM-dd
     * @return numero de partidos creados o actualizados
     */
    int ingestarPorFecha(String fecha);

    /**
     * Sincroniza los fixtures que API-Football marca como live.
     *
     * @return numero de partidos en vivo creados o actualizados
     */
    int ingestarEnVivo();
}