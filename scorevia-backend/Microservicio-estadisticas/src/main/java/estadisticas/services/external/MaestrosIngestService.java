package estadisticas.services.external;

/**
 * Servicio encargado de la ingesta y enriquecimiento de las entidades maestras
 * de la base de datos (Estadios, Equipos, Entrenadores, Ligas y Jugadores)
 * consumiendo la API-Football de forma controlada y robusta.
 *
 * @author Daniel
 * @version 1.0
 */
public interface MaestrosIngestService {

    void ingestarLigasMasivas(Integer temporada);

    void ingestarEquiposYEstadiosDeLiga(Integer idLiga, Integer temporada);

    void ingestarEntrenadoresPorEquipo();

    /**
     * Refresca los entrenadores de las competiciones destacadas.
     *
     * @return cuantos equipos se han mirado
     */
    int refrescarEntrenadoresDestacados();

    void ingestarClasificaciones(Integer idLiga, Integer temporada);

    void ingestarJugadoresCompletosPorEquipo(Integer temporada);

    /**
     * Baja las plantillas de la temporada, pudiendo acotar el alcance y refrescar lo viejo.
     *
     * @param soloDestacadas solo las 22 competiciones grandes (218 equipos) en vez de las
     *                       11.508 de toda la base
     * @param maxHoras       0 = solo los que no se han mirado nunca; &gt; 0 = además los
     *                       sincronizados hace más de esas horas; &lt; 0 = todos
     */
    void ingestarJugadoresCompletosPorEquipo(Integer temporada, boolean soloDestacadas, int maxHoras);

    void ingestarPerfilesPendientes();

    /**
     * Rellena los nombres sin abreviar recorriendo el catalogo de jugadores por paginas.
     *
     * <p>Medido sobre la base: 267.689 de 510.032 jugadores tienen el nombre abreviado
     * ("D. Barrow"), porque el campo {@code name} de API-Football llega asi, y solo 29.000
     * tienen guardado el completo. Pedirlos uno a uno serian 481.000 peticiones; el catalogo
     * paginado da <b>250 fichas por peticion</b>, o sea unas 2.000 en total.</p>
     *
     * <p>Se puede cortar y retomar: {@code desdePagina} dice por donde seguir, y el log deja
     * la ultima pagina procesada.</p>
     *
     * @param desdePagina primera pagina a pedir (la API empieza en 1)
     * @param maxPaginas  tope de paginas de esta tanda, para no vaciar la cuota de golpe
     * @return cuantos jugadores se actualizaron
     */
    int ingestarNombresCompletos(int desdePagina, int maxPaginas);
}