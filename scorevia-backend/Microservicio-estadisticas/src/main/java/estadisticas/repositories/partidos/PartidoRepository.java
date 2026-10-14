// estadisticas/repositories/PartidoRepository.java
package estadisticas.repositories.partidos;

import java.time.LocalDateTime;
import estadisticas.entities.partidos.Partido;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PartidoRepository extends JpaRepository<Partido, Integer> {

    @EntityGraph(attributePaths = {"liga", "equipoLocal", "equipoVisitante", "arbitro"})
    @NonNull
    Page<Partido> findAll(@NonNull Pageable pageable);

    /**
     * Los partidos que se estan jugando <b>ahora</b>, del que empezo antes al que empezo despues.
     *
     * <p>La ventana de fechas no sobra, es lo que hace que la lista sea de fiar. Un partido se
     * queda en EN_JUEGO para siempre si la ingesta en vivo no llega a verlo terminar (el
     * scheduler apagado un rato, la API que deja de devolverlo, un partido que se suspende y
     * no vuelve). Comprobado sobre la base: habia 21 partidos del dia anterior clavados en
     * EN_JUEGO, y salian en el marcador en directo como si se estuvieran jugando.</p>
     *
     * <p>Y es una ventana en vez de un "solo los de hoy" porque un partido que empieza a las
     * 23:30 sigue jugandose despues de medianoche: filtrar por dia natural lo tiraria de la
     * lista justo mientras se juega. Con [ahora - unas horas, ahora] eso no pasa y los
     * colgados de ayer se caen igual.</p>
     *
     * <p>Se apoya en el indice parcial {@code idx_partidos_en_juego_fecha}.</p>
     */
    @EntityGraph(attributePaths = {"liga", "equipoLocal", "equipoVisitante", "arbitro"})
    List<Partido> findByEstadoAndFechaBetweenOrderByFechaAsc(
            String estado, java.time.LocalDateTime desde, java.time.LocalDateTime hasta);

    @EntityGraph(attributePaths = {"liga", "equipoLocal", "equipoVisitante", "arbitro"})
    Page<Partido> findByLiga_IdLiga(Integer idLiga, Pageable pageable);

    /**
     * Partidos de un día concreto.
     *
     * <p>Hace falta desde que el calendario está ingerido hasta mayo de 2027: el listado
     * general ordena por fecha descendente, así que su primera página son partidos de 2027
     * y la sección "Partidos de hoy" —que filtraba en el cliente sobre esa página— se
     * quedaba SIEMPRE vacía. Filtrar por rango de fechas en la consulta es lo único que
     * sirve cuando hay 1,5 millones de partidos y 76.561 de ellos son futuros.</p>
     */
    @EntityGraph(attributePaths = {"liga", "equipoLocal", "equipoVisitante", "arbitro"})
    Page<Partido> findByFechaBetween(java.time.LocalDateTime desde, java.time.LocalDateTime hasta, Pageable pageable);

    @EntityGraph(attributePaths = {"liga", "equipoLocal", "equipoVisitante", "arbitro"})
    Page<Partido> findByLiga_IdLigaAndTemporada(Integer idLiga, Integer temporada, Pageable pageable);

    // Partidos dirigidos por un árbitro, para su ficha. Va por el índice idx_partidos_arbitro
    // que ya existe; con ~20.800 árbitros y 541.000 partidos arbitrados salen unos 26 de media
    // por árbitro, así que no hace falta nada más elaborado.
    @EntityGraph(attributePaths = {"liga", "equipoLocal", "equipoVisitante", "arbitro"})
    Page<Partido> findByArbitro_IdArbitro(Integer idArbitro, Pageable pageable);

    long countByArbitro_IdArbitro(Integer idArbitro);

    /**
     * Temporadas de una liga con cuántos partidos se han jugado ya en cada una.
     *
     * <p>Antes esto salía de Clasificacion.partidosJugados, y por eso el selector marcaba
     * "2026/27 (sin jugar)" en La Liga y la Premier en pleno septiembre: los partidos sí
     * estaban ingeridos, pero la tabla de clasificaciones no se había vuelto a sincronizar,
     * así que la suma daba cero. Contar partidos con marcador no depende de que ninguna otra
     * ingesta esté al día -- si hay resultados, la temporada se está jugando.</p>
     */
    @Query("SELECT p.temporada, SUM(CASE WHEN p.resultadoLocal IS NOT NULL THEN 1 ELSE 0 END) "
            + "FROM Partido p WHERE p.liga.idLiga = :idLiga AND p.temporada IS NOT NULL "
            + "GROUP BY p.temporada ORDER BY p.temporada DESC")
    List<Object[]> resumenTemporadasPorLiga(@Param("idLiga") Integer idLiga);

    @EntityGraph(attributePaths = {"liga", "equipoLocal", "equipoVisitante", "arbitro"})
    @Query("SELECT p FROM Partido p WHERE p.equipoLocal.idEquipo = :idEquipo OR p.equipoVisitante.idEquipo = :idEquipo")
    Page<Partido> findByEquipo(@Param("idEquipo") Integer idEquipo, Pageable pageable);

    // La ficha de equipo separa "jugados" y "programados" en dos listas paginadas aparte, y
    // cada una necesita su propio total de páginas -- por eso son dos consultas y no un
    // filtro en memoria sobre findByEquipo.
    //
    // El corte es "¿tiene resultado?" y no el campo estado a propósito: estado llega de
    // API-Football con varios valores para lo mismo (FINALIZADO, FT, AET, PEN) y alguno más
    // para partidos suspendidos, así que filtrar por texto se queda corto o de más según el
    // caso. Un partido con marcador es un partido jugado, punto.
    @EntityGraph(attributePaths = {"liga", "equipoLocal", "equipoVisitante", "arbitro"})
    @Query("SELECT p FROM Partido p WHERE (p.equipoLocal.idEquipo = :idEquipo OR p.equipoVisitante.idEquipo = :idEquipo) "
            + "AND p.resultadoLocal IS NOT NULL")
    Page<Partido> findJugadosByEquipo(@Param("idEquipo") Integer idEquipo, Pageable pageable);

    @EntityGraph(attributePaths = {"liga", "equipoLocal", "equipoVisitante", "arbitro"})
    @Query("SELECT p FROM Partido p WHERE (p.equipoLocal.idEquipo = :idEquipo OR p.equipoVisitante.idEquipo = :idEquipo) "
            + "AND p.resultadoLocal IS NULL")
    Page<Partido> findProgramadosByEquipo(@Param("idEquipo") Integer idEquipo, Pageable pageable);

    // Variante "solo FINALIZADO" de findByEquipo/findByEquipoLocal_IdEquipo/
    // findByEquipoVisitante_IdEquipo/findEnfrentamientosDirectos, para el contexto que arma
    // InternalDataServiceImpl para el motor de predicciones (racha, H2H, fatiga). Las de
    // arriba las sigue usando PartidoServiceImpl para los listados públicos
    // (GET /api/v1/partidos/equipo/{id}, /enfrentamientos), donde SÍ hace falta ver también
    // los partidos futuros -- no se puede reutilizar la misma query para ambos casos. Sin este
    // filtro, "los últimos 5 partidos" de un equipo al calcular una predicción incluía
    // partidos programados sin jugar (con resultado null) -- y, en particular, el propio
    // partido que se está prediciendo, que por ser el más próximo en el tiempo termina siendo
    // el primer elemento de la lista ordenada por fecha descendente. Los modificadores de
    // racha/H2H ya descartaban esos partidos por tener resultado null, pero igual ocupaban uno
    // de los 5 huecos (reduciendo en silencio la muestra real); el de fatiga no tenía ese
    // filtro y calculaba los días de descanso comparando el partido consigo mismo (diferencia
    // 0 días), aplicando casi siempre la penalización de fatiga sin relación con el descanso
    // real del equipo.
    @EntityGraph(attributePaths = {"liga", "equipoLocal", "equipoVisitante", "arbitro"})
    @Query("SELECT p FROM Partido p WHERE (p.equipoLocal.idEquipo = :idEquipo OR p.equipoVisitante.idEquipo = :idEquipo) "
            + "AND p.estado = 'FINALIZADO'")
    Page<Partido> findUltimosFinalizadosByEquipo(@Param("idEquipo") Integer idEquipo, Pageable pageable);

    @EntityGraph(attributePaths = {"liga", "equipoLocal", "equipoVisitante", "arbitro"})
    Page<Partido> findByEquipoLocal_IdEquipoAndEstado(Integer idEquipoLocal, String estado, Pageable pageable);

    @EntityGraph(attributePaths = {"liga", "equipoLocal", "equipoVisitante", "arbitro"})
    Page<Partido> findByEquipoVisitante_IdEquipoAndEstado(Integer idEquipoVisitante, String estado, Pageable pageable);

    @EntityGraph(attributePaths = {"liga", "equipoLocal", "equipoVisitante", "arbitro"})
    @Query("SELECT p FROM Partido p WHERE "
            + "(p.equipoLocal.idEquipo = :idEquipoA AND p.equipoVisitante.idEquipo = :idEquipoB) OR "
            + "(p.equipoLocal.idEquipo = :idEquipoB AND p.equipoVisitante.idEquipo = :idEquipoA)")
    Page<Partido> findEnfrentamientosDirectos(@Param("idEquipoA") Integer idEquipoA, @Param("idEquipoB") Integer idEquipoB, Pageable pageable);

    @EntityGraph(attributePaths = {"liga", "equipoLocal", "equipoVisitante", "arbitro"})
    @Query("SELECT p FROM Partido p WHERE "
            + "((p.equipoLocal.idEquipo = :idEquipoA AND p.equipoVisitante.idEquipo = :idEquipoB) OR "
            + "(p.equipoLocal.idEquipo = :idEquipoB AND p.equipoVisitante.idEquipo = :idEquipoA)) "
            + "AND p.estado = 'FINALIZADO'")
    Page<Partido> findEnfrentamientosDirectosFinalizados(@Param("idEquipoA") Integer idEquipoA, @Param("idEquipoB") Integer idEquipoB, Pageable pageable);

    /**
     * Partidos acabados que siguen sin estadísticas <b>y cuya competición las publica</b>.
     *
     * <p>El filtro de cobertura es lo que separa el trabajo de verdad del imposible. Medido el
     * 2026-09-09: hay <b>1.130.057</b> partidos finalizados sin estadísticas de jugador, pero
     * solo <b>31.910</b> son de competiciones que las publican. Los otro 1,1M son amistosos de
     * club, filiales, juveniles y ligas regionales: API-Football no tiene esos datos y no los
     * va a tener, así que pedirlos es tirar dos peticiones por partido para guardar cero filas
     * — más de setenta días de cuota entera.</p>
     *
     * <p>{@code IS NULL} entra a propósito: significa "todavía no se sabe" (la cobertura se
     * rellena al sincronizar las ligas maestras) y ahí sí merece la pena intentarlo. Solo se
     * descarta lo que la API dice explícitamente que no cubre. Es el mismo criterio que ya
     * usaban la recogida del día y el refresco de alineaciones.</p>
     *
     * <p><b>Devuelve List y no Page a propósito.</b> Con {@code Page}, Spring Data lanza
     * además un {@code count(*)} de la misma consulta en cada vuelta del bucle, y sobre 1,4M
     * de partidos con dos EXISTS dentro eso tarda segundos. Medido el 2026-09-09: el relleno
     * avanzaba a 10 peticiones por minuto teniendo presupuesto para 720, o sea que el freno no
     * era la API sino contar una y otra vez un total que el bucle ni siquiera usa.</p>
     */
    @Query("SELECT p FROM Partido p " +
            "JOIN FETCH p.equipoLocal " +
            "JOIN FETCH p.equipoVisitante " +
            "LEFT JOIN p.liga l " +
            "WHERE p.estado = 'FINALIZADO' " +
            "AND p.fecha <= :limiteGracia " +
            "AND (l.coberturaStatsPartido IS NULL OR l.coberturaStatsPartido = true) " +
            "AND (p.statsNotFound = false OR p.statsNotFound IS NULL) " +
            "AND (p.intentosIngesta IS NULL OR p.intentosIngesta < 3) " +
            "AND NOT (EXISTS (SELECT 1 FROM EstadisticaEquipoPartido eep WHERE eep.partido = p) " +
            "AND EXISTS (SELECT 1 FROM EstadisticaJugadorPartido ejp WHERE ejp.partido = p))")
    List<Partido> findPartidosPendientesDeStats(@Param("limiteGracia") java.time.LocalDateTime limiteGracia, Pageable pageable);

    /**
     * Las parejas (liga, temporada) que han tenido algún partido acabado desde una fecha.
     *
     * <p>Es lo que necesita el refresco automático de clasificaciones: la tabla de posiciones
     * solo cambia donde se ha jugado, así que pedirla para las 1.244 competiciones de la base
     * sería tirar más de mil peticiones al día para reescribir lo mismo. Un sábado cargado
     * salen unas 330 parejas; un martes cualquiera, ocho.</p>
     *
     * @return filas de dos posiciones: {@code [0]} id de liga, {@code [1]} temporada.
     */
    @Query("SELECT DISTINCT p.liga.idLiga, p.temporada FROM Partido p " +
            "WHERE p.estado = 'FINALIZADO' AND p.fecha >= :desde AND p.temporada IS NOT NULL")
    List<Object[]> findLigasYTemporadasConPartidosDesde(@Param("desde") java.time.LocalDateTime desde);

    /**
     * Varios partidos con su liga ya cargada, para la ingesta por tandas.
     *
     * <p>Mismo motivo que {@link #findByIdConLiga}: {@code Partido.liga} es LAZY y la ingesta
     * corre fuera de sesión, así que leer la cobertura de un proxy sin inicializar saltaría
     * con LazyInitializationException.</p>
     */
    @Query("SELECT p FROM Partido p LEFT JOIN FETCH p.liga WHERE p.idPartido IN :ids")
    List<Partido> findByIdsConLiga(@Param("ids") java.util.Collection<Integer> ids);

    /**
     * Partidos acabados dentro de una ventana reciente a los que todavía les faltan
     * estadísticas.
     *
     * <p>Es la consulta de la recogida automática diaria, y se diferencia de
     * {@link #findPartidosPendientesDeStats} en dos cosas que la hacen apta para correr sola:
     * mira solo una ventana de horas (y no los más de 42.000 partidos pendientes de toda la
     * historia) y salta las competiciones que la propia API dice no cubrir. Sin ese filtro,
     * una pasada automática se comería la cuota del día preguntando por amistosos y filiales
     * para guardar cero filas.</p>
     *
     * <p>{@code coberturaStatsPartido} nula es "todavía no se sabe" y entra igual.</p>
     */
    @Query("SELECT p FROM Partido p " +
            "WHERE p.estado = 'FINALIZADO' " +
            "AND p.fecha BETWEEN :desde AND :hasta " +
            "AND (p.statsNotFound = false OR p.statsNotFound IS NULL) " +
            "AND (p.intentosIngesta IS NULL OR p.intentosIngesta < 3) " +
            "AND (p.liga.coberturaStatsPartido IS NULL OR p.liga.coberturaStatsPartido = true) " +
            "AND NOT (EXISTS (SELECT 1 FROM EstadisticaEquipoPartido eep WHERE eep.partido = p) " +
            "AND EXISTS (SELECT 1 FROM EstadisticaJugadorPartido ejp WHERE ejp.partido = p))")
    Page<Partido> findPartidosRecientesPendientesDeStats(@Param("desde") java.time.LocalDateTime desde,
                                                        @Param("hasta") java.time.LocalDateTime hasta,
                                                        Pageable pageable);

    @Query("SELECT p FROM Partido p " +
            "WHERE p.estado = 'FINALIZADO' " +
            "AND p.statsNotFound = false " +
            "AND (p.eventosNotFound = false OR p.eventosNotFound IS NULL) " + // <--- AQUÍ LA MAGIA
            "AND EXISTS (SELECT 1 FROM EstadisticaEquipoPartido eep WHERE eep.partido = p) " +
            "AND NOT EXISTS (SELECT 1 FROM EventoPartido ep WHERE ep.partido = p)")
    Page<Partido> findPartidosConStatsSinEventos(Pageable pageable);

    /**
     * Partidos ya terminados de una ventana de fechas a los que les falta el timeline.
     *
     * <p>Hermana de {@link #findPartidosRecientesPendientesDeStats}, pero mirando los eventos
     * y no las estadisticas, y con su propia bandera de cobertura: hay competiciones que la
     * API cubre para eventos y no para estadisticas, y al reves. Gatearlas con la bandera
     * equivocada significa o pedir lo que no existe (peticiones tiradas a la basura) o no
     * pedir lo que si estaba.</p>
     *
     * <p>La usa la reconciliacion diaria para recuperar los partidos de ayer que se quedaron
     * sin timeline porque la ingesta en vivo no llego a verlos terminar.</p>
     */
    @Query("SELECT p FROM Partido p " +
            "WHERE p.estado = 'FINALIZADO' " +
            "AND p.fecha BETWEEN :desde AND :hasta " +
            "AND (p.eventosNotFound = false OR p.eventosNotFound IS NULL) " +
            "AND (p.intentosEventos IS NULL OR p.intentosEventos < 3) " +
            "AND (p.liga.coberturaEventos IS NULL OR p.liga.coberturaEventos = true) " +
            "AND NOT EXISTS (SELECT 1 FROM EventoPartido ep WHERE ep.partido = p)")
    Page<Partido> findPartidosRecientesSinEventos(@Param("desde") java.time.LocalDateTime desde,
                                                  @Param("hasta") java.time.LocalDateTime hasta,
                                                  Pageable pageable);

    /**
     * Partidos que dicen estar jugandose pero cuya hora de inicio quedo muy atras.
     *
     * <p>Un partido se queda clavado en EN_JUEGO cuando la ingesta en vivo no llega a verlo
     * terminar: el scheduler apagado un rato, la API que deja de devolverlo en {@code
     * live=all}, un partido suspendido que no vuelve. Se comprobo sobre la base y habia 21
     * del dia anterior en ese estado.</p>
     *
     * <p>El listado en directo ya los esconde filtrando por ventana, pero seguian mal en la
     * base: cuentan como "en juego" para cualquier otra consulta y nunca se les pidieron ni
     * las estadisticas ni los eventos, porque eso solo pasa al verlos pasar a FINALIZADO.</p>
     */
    @Query("SELECT p FROM Partido p LEFT JOIN FETCH p.liga " +
            "WHERE p.estado = 'EN_JUEGO' AND p.fecha < :limite ORDER BY p.fecha ASC")
    List<Partido> findEnJuegoColgados(@Param("limite") java.time.LocalDateTime limite);

    /**
     * Los resultados de una competicion y temporada, del mas antiguo al mas reciente.
     *
     * <p>Sirve para calcular de una vez la racha de todos los equipos de una clasificacion.
     * Antes se hacia con una consulta <b>por equipo</b> ({@code findUltimosFinalizadosByEquipo}),
     * y cada una tenia dos problemas: el {@code OR} entre local y visitante obliga a leer los
     * partidos del equipo <b>enteros</b> —875 en el caso medido, toda su historia— para
     * quedarse con cinco, y ademas no filtraba por temporada, asi que la clasificacion de
     * 2010 acababa ensenando la racha de 2026. Medido: 108 ms por equipo, 11,4 segundos la
     * tabla entera.</p>
     *
     * <p>Devuelve columnas sueltas y no entidades a proposito: aqui solo hacen falta cuatro
     * numeros por partido, y montar el objeto {@code Partido} completo con su liga, sus dos
     * equipos y su arbitro era la mitad del coste.</p>
     *
     * @return filas de {@code [idEquipoLocal, idEquipoVisitante, golesLocal, golesVisitante]}
     */
    @Query("SELECT p.equipoLocal.idEquipo, p.equipoVisitante.idEquipo, p.resultadoLocal, p.resultadoVisitante "
            + "FROM Partido p "
            + "WHERE p.liga.idLiga = :idLiga AND p.temporada = :temporada "
            + "AND p.estado = 'FINALIZADO' "
            + "AND p.resultadoLocal IS NOT NULL AND p.resultadoVisitante IS NOT NULL "
            + "ORDER BY p.fecha ASC")
    List<Object[]> findResultadosDeLigaYTemporada(@Param("idLiga") Integer idLiga,
                                                  @Param("temporada") Integer temporada);

    /**
     * Las temporadas en las que un equipo llego a jugar, de la mas reciente a la mas antigua.
     *
     * <p>Sale de los partidos y no de las clasificaciones porque hay competiciones sin tabla
     * (copas a partido unico, amistosos) en las que el equipo jugo igual, y esas temporadas
     * tambien tienen que poder abrirse.</p>
     */
    @Query("SELECT DISTINCT p.temporada FROM Partido p "
            + "WHERE (p.equipoLocal.idEquipo = :idEquipo OR p.equipoVisitante.idEquipo = :idEquipo) "
            + "AND p.temporada IS NOT NULL ORDER BY p.temporada DESC")
    List<Integer> temporadasDeEquipo(@Param("idEquipo") Integer idEquipo);

    /**
     * Partidos sin once: los que se estan jugando y los que estan a punto de empezar.
     *
     * <p>El feed del directo ({@code fixtures?live=all}) <b>no trae alineaciones</b>:
     * comprobado, sus claves son fixture, league, teams, goals, score y events. Hay que
     * pedirlas aparte, y por eso solo se piden las que faltan: una vez por partido y no en
     * cada tick.</p>
     *
     * <p><b>Y se saltan las competiciones que no publican alineaciones.</b> Sin este filtro
     * se preguntaba en cada tick por partidos cuya respuesta nunca va a traer un once
     * (Regionalliga, Reserve League, Iraqi League...): comprobado sobre diez partidos reales
     * en juego, la API devolvio {@code lineups: 0} en los diez. Eso es cuota tirada cada
     * treinta segundos y para siempre, porque el partido nunca sale de esta consulta.</p>
     *
     * <p>El {@code IS NULL} entra a proposito: significa "no se sabe", y ahi merece la pena
     * intentarlo. Solo se descarta lo que la API dice explicitamente que no cubre.</p>
     *
     * <p><b>Entran tambien los que estan a punto de empezar.</b> El once se publica entre 20
     * y 60 minutos antes del pitido inicial, que es justo cuando la gente lo quiere ver; si
     * solo se miraran los partidos ya en juego, la alineacion aparecia cuando ya no servia
     * para nada. La ventana la decide quien llama.</p>
     */
    @Query("SELECT p FROM Partido p LEFT JOIN p.liga l "
            + "WHERE (p.estado = 'EN_JUEGO' "
            + "       OR (p.estado = 'PROGRAMADO' AND p.fecha BETWEEN :desde AND :hasta)) "
            + "AND NOT EXISTS (SELECT 1 FROM EstadisticaJugadorPartido e WHERE e.partido = p) "
            + "AND (l.coberturaAlineaciones IS NULL OR l.coberturaAlineaciones = true) "
            + "ORDER BY p.fecha ASC")
    List<Partido> findSinAlineacion(@Param("desde") LocalDateTime desde,
                                    @Param("hasta") LocalDateTime hasta,
                                    Pageable pageable);

    /**
     * Los dias recientes en los que quedaron partidos sin cerrar.
     *
     * <p>Un partido que ya se jugo y sigue en PROGRAMADO o en EN_JUEGO significa que nadie
     * llego a releer esa fecha: el contenedor apagado esa noche, la maquina de desarrollo sin
     * encender el fin de semana, un rato largo sin cuota. Medido el 2026-09-10 sobre la base
     * viva: 233 de los 336 partidos del dia anterior seguian en PROGRAMADO, con su resultado
     * publicado en la API desde hacia horas.</p>
     *
     * <p>Devuelve <b>fechas</b> y no partidos a proposito: el arreglo es releer el dia entero
     * con {@code fixtures?date=}, que cuesta UNA peticion y trae el estado real de los
     * cientos de partidos de esa jornada. Pedirlos de uno en uno seria el mismo dato a
     * doscientas veces el precio.</p>
     *
     * <p>El limite superior lo pone quien llama: un partido de hace dos horas puede estar
     * en juego de verdad y no hay nada que recuperar ahi.</p>
     *
     * <p>Las devuelve en texto ISO y no como fecha a proposito: en una consulta nativa, el
     * tipo Java que sale de un DATE lo decide Hibernate (java.sql.Date o LocalDate segun la
     * version), y equivocarse ahi no se ve al compilar -- revienta en ejecucion, dentro de un
     * trabajo programado, sin nadie delante. Ademas es la forma exacta que pide
     * {@code ingestarPorFecha(String)}, y en ISO el orden alfabetico ya es el cronologico.</p>
     */
    @Query(value = """
            SELECT DISTINCT TO_CHAR(p.fecha, 'YYYY-MM-DD') AS dia
            FROM sch_estadisticas.partidos p
            WHERE p.estado IN ('PROGRAMADO', 'EN_JUEGO')
              AND p.fecha >= :desde
              AND p.fecha < :limite
            ORDER BY dia
            """, nativeQuery = true)
    List<String> findFechasConPartidosSinCerrar(@Param("desde") LocalDateTime desde,
                                                @Param("limite") LocalDateTime limite);
}
