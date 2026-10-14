package estadisticas.entities.arbitros;

import jakarta.persistence.*;
import lombok.*;

/**
 * Arbitro de un partido.
 * <p>A diferencia de Liga/Equipo/Jugador, el id es autogenerado (SERIAL) y no viene
 * de la API externa: API-Football suele dar el arbitro como texto suelto dentro del
 * fixture ("nombre, nacionalidad"), no como una entidad con id propio y estable.
 * En la ingesta habra que normalizar ese texto contra esta tabla por nombre.</p>
 *
 * @author Daniel
 * @version 1.0
 */
@Entity
@Table(name = "arbitros", uniqueConstraints = {@UniqueConstraint(columnNames = {"nombre", "pais"}, name = "arbitro_nombre_pais_UNIQUE")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Arbitro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_arbitro")
    private Integer idArbitro;

    @Column(name = "nombre")
    private String nombre;

    // NOT NULL a propósito (ver PartidoIngestServiceImpl.resolverArbitro): "" cuando la API
    // no da país, nunca null -- Postgres trata cada NULL como distinto en un UNIQUE, así que
    // un null aquí rompería la deduplicación por (nombre, pais).
    @Column(name = "pais", nullable = false)
    private String pais;
}
