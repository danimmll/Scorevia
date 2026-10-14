package estadisticas.entities.equipos;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "estadios", schema = "sch_estadisticas")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Estadio {
    @Id
    @Column(name = "id_estadio")
    private Integer idEstadio;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "ciudad", length = 100)
    private String ciudad;

    @Column(name = "capacidad")
    private Integer capacidad;

    @Column(name = "superficie", length = 50)
    private String superficie;

    @Column(name = "foto_url", columnDefinition = "TEXT")
    private String fotoUrl;
}