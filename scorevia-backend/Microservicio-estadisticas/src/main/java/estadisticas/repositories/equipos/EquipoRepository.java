package estadisticas.repositories.equipos;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import estadisticas.entities.equipos.Equipo;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
public interface EquipoRepository extends JpaRepository<Equipo, Integer> {

    /**
     * Equipos activos en la temporada a los que todavía no se les ha bajado la plantilla, o
     * cuya plantilla está vieja.
     *
     * <p>Sin la tabla de control, cada relanzamiento de la sincronización empieza por el
     * primer equipo y vuelve a gastar las ~17.000 peticiones enteras. Y se relanza más de lo
     * que uno querría: basta un despliegue a media ingesta para cortarla.</p>
     *
     * <p><b>Pero una marca que no caduca nunca congela la plantilla para siempre.</b> Medido
     * el 2026-09-09: los 218 equipos activos de las competiciones grandes estaban marcados, la
     * sincronización dijo "0 pendientes" y no pidió nada — con lo cual Kroos seguía en el
     * Madrid aunque el código que suelta a los que ya no están llevaba un día desplegado. La
     * marca servía para retomar un backfill, pero se estaba usando también como "ya está para
     * siempre", y el fichaje de enero no iba a entrar jamás.</p>
     *
     * <p>Por eso {@code maxHoras}, con tres modos:</p>
     * <ul>
     *   <li><b>0</b>: solo los que no se han mirado nunca. Es lo que quiere el backfill largo,
     *       y el valor por defecto.</li>
     *   <li><b>&gt; 0</b>: además, los sincronizados hace más de esas horas.</li>
     *   <li><b>&lt; 0</b>: todos, tengan la marca que tengan.</li>
     * </ul>
     *
     * <p>Los tres hacen falta, y el tercero se aprendió a base de tropezar: el 09/09 hubo que
     * relanzar esto dos veces el mismo día (una tras arreglar el filtro, otra tras arreglar la
     * transacción que faltaba), y las marcas recién puestas tenían minutos. Sin un "todos", la
     * única salida era volver a borrar filas a mano en la base — que es justo lo que se estaba
     * intentando dejar de hacer.</p>
     */
    @Query(value = """
            SELECT e.* FROM sch_estadisticas.equipos e
            WHERE EXISTS (
                SELECT 1 FROM sch_estadisticas.partidos p
                WHERE p.temporada = :temporada
                  AND (p.id_equipo_local = e.id_equipo OR p.id_equipo_visitante = e.id_equipo))
              AND NOT EXISTS (
                SELECT 1 FROM sch_estadisticas.plantillas_sincronizadas s
                WHERE s.id_equipo = e.id_equipo AND s.temporada = :temporada
                  AND (:maxHoras = 0
                       OR (:maxHoras > 0 AND s.fecha > now() - make_interval(hours => :maxHoras))))
            ORDER BY e.id_equipo
            LIMIT :limite
            """, nativeQuery = true)
    List<Equipo> findEquiposPendientesDePlantilla(@Param("temporada") Integer temporada,
                                                  @Param("maxHoras") int maxHoras,
                                                  @Param("limite") int limite);

    /**
     * Igual que {@link #findEquiposPendientesDePlantilla}, pero acotado a unos clubes.
     *
     * <p>Lo mismo que se hizo con el refresco de entrenadores: la pasada completa son 11.508
     * equipos y por eso no se lanza nunca, así que el dato se pudre. Acotada a los clubes de
     * las 22 destacadas son unas 400 peticiones y se puede tirar a diario.</p>
     *
     * <p>Recibe CLUBES y no competiciones porque {@code equipos.id_liga} no vale para saber
     * quién juega qué: ver {@code ClubesDestacados}. Filtrando por liga, esta consulta miraba
     * 218 equipos que no eran los grandes y el Real Madrid no entraba nunca.</p>
     */
    @Query(value = """
            SELECT e.* FROM sch_estadisticas.equipos e
            WHERE e.id_equipo IN (:idEquipos)
              AND EXISTS (
                SELECT 1 FROM sch_estadisticas.partidos p
                WHERE p.temporada = :temporada
                  AND (p.id_equipo_local = e.id_equipo OR p.id_equipo_visitante = e.id_equipo))
              AND NOT EXISTS (
                SELECT 1 FROM sch_estadisticas.plantillas_sincronizadas s
                WHERE s.id_equipo = e.id_equipo AND s.temporada = :temporada
                  AND (:maxHoras = 0
                       OR (:maxHoras > 0 AND s.fecha > now() - make_interval(hours => :maxHoras))))
            ORDER BY e.id_equipo
            LIMIT :limite
            """, nativeQuery = true)
    List<Equipo> findEquiposPendientesDePlantillaEnClubes(@Param("temporada") Integer temporada,
                                                          @Param("idEquipos") List<Integer> idEquipos,
                                                          @Param("maxHoras") int maxHoras,
                                                          @Param("limite") int limite);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO sch_estadisticas.plantillas_sincronizadas (id_equipo, temporada)
            VALUES (:idEquipo, :temporada)
            ON CONFLICT (id_equipo, temporada) DO UPDATE SET fecha = now()
            """, nativeQuery = true)
    void marcarPlantillaSincronizada(@Param("idEquipo") Integer idEquipo,
                                     @Param("temporada") Integer temporada);

    Page<Equipo> findByNombreContainingIgnoreCase(String nombre, Pageable pageable);

}
