package usuariosroles.entities;

import java.util.HashSet;
import java.util.Set;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

@Entity
@Table(name = "roles", uniqueConstraints = {@UniqueConstraint(columnNames = {"nombre_rol"}, name = "nombre_rol_UNIQUE")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_rol")
    private Integer idRol;

    @Column(name = "nombre_rol")
    @NotEmpty(message = "El nombre del rol no puede estar vacío")
    private String nombreRol;

    @Column(name = "activo")
    private Boolean activo;

    @PrePersist
    public void prePersist() {
        this.activo = true;
    }

    @ManyToMany(mappedBy = "roles", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Usuario> usuarios = new HashSet<>();
}