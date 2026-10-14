package estadisticas.entities.equipos;

import estadisticas.entities.ligas.Liga;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Equipo de futbol. El id coincide con el id de la API externa, igual que en Liga.
 *
 * @author Daniel
 * @version 1.0
 */
@Entity
@Table(name = "equipos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Equipo {

    @Id
    @Column(name = "id_equipo")
    private Integer idEquipo;

    @Column(name = "nombre")
    private String nombre;

    @Column(name = "pais")
    private String pais;

    @Column(name = "escudo_url")
    private String escudoUrl;

    @Column(name = "codigo", length = 10)
    private String codigo;

    @Column(name = "fundacion")
    private Integer fundacion;

    @Column(name = "ultima_sync_traspasos")
    private LocalDateTime ultimaSyncTraspasos;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_liga")
    private Liga liga;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_estadio")
    private Estadio estadio;
}