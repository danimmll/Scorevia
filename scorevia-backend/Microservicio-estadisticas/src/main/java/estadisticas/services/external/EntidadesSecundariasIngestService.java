package estadisticas.services.external;

public interface EntidadesSecundariasIngestService {

    /**
     * Ingesta Trofeos (jugadores y entrenadores) y Bajas (/sidelined, solo jugadores)
     * de todos los jugadores y entrenadores en BD.
     * <p>
     * Nota: /trophies y /sidelined exigen un único ID por petición (no admiten lotes),
     * así que se hace una petición por jugador/entrenador, no de 20 en 20.
     * </p>
     * Idempotente: los trofeos y bajas ya existentes no se vuelven a insertar en cada
     * re-sync (las bajas se actualizan si su fecha de fin ha cambiado).
     */
    void ingestarTrofeosYBajasMasivo();

    /**
     * Ingesta traspasos consultando por equipo para maximizar la eficiencia de la API.
     * Idempotente: el mismo traspaso puede aparecer al consultar el equipo de origen y
     * el de destino (o en un re-sync posterior) y no se duplica.
     */
    void ingestarTraspasosPorEquipo();

    /**
     * Los trofeos de los entrenadores, y solo los suyos.
     *
     * <p>Existe porque {@code ingestarTrofeosYBajasMasivo} recorre <b>primero</b> los 510.032
     * jugadores y los entrenadores despues. A una peticion por jugador, esa cola se come la
     * cuota diaria mucho antes de llegar al final, asi que el bloque de entrenadores no se
     * ejecuto nunca: la tabla {@code trofeos} tiene 560.212 filas de jugador y <b>cero</b> de
     * entrenador, y el palmares de cualquier tecnico sale vacio.</p>
     *
     * <p>Son 27.123 entrenadores, una peticion cada uno: un 18% de la cuota diaria, y de una
     * sola vez. Se puede cortar y retomar, porque solo pide los que todavia no tienen ningun
     * trofeo guardado.</p>
     *
     * @return cuantos entrenadores se procesaron
     */
    int ingestarTrofeosDeEntrenadores();
}
