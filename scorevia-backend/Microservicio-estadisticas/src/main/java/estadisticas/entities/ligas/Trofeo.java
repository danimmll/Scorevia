package estadisticas.entities.ligas;

import estadisticas.entities.entrenadores.Entrenador;
import estadisticas.entities.jugadores.Jugador;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "trofeos", schema = "sch_estadisticas")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Trofeo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_trofeo")
    private Long idTrofeo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_jugador")
    private Jugador jugador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_entrenador")
    private Entrenador entrenador;

    @Column(name = "competicion", nullable = false, length = 150)
    private String competicion;

    @Column(name = "pais", length = 50)
    private String pais;

    @Column(name = "temporada", length = 20)
    private String temporada;

    @Column(name = "resultado", nullable = false, length = 50)
    private String resultado;
}