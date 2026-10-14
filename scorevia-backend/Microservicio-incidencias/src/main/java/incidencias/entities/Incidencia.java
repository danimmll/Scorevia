package incidencias.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "incidencias")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Incidencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_incidencia")
    private Integer idIncidencia;

    @Column(name = "endpoint")
    private String endpoint;

    @Column(name = "tipo")
    private String tipo;

    @Column(name = "clase")
    private String clase;

    @Column(name = "metodo")
    private String metodo;

    @Column(name = "traza", columnDefinition = "TEXT")
    private String traza;

    @Column(name = "fecha")
    private LocalDateTime fecha;

    @Column(name = "usuario_contexto")
    private String usuarioContexto;

    @PrePersist
    public void prePersist() {
        this.fecha = LocalDateTime.now(ZoneId.systemDefault());
    }
}