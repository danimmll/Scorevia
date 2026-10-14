package usuariosroles.models;

import java.io.Serializable;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

/**
 * Composite key class for the user-role relationship.
 *
 * <p>This class represents the primary key for the {@code UsuarioRol} entity,
 * combining the user ID and role ID.</p>
 *
 * <p>Annotated with {@link Embeddable} to indicate that it can be embedded
 * in an entity as a composite key.</p>
 *
 * <p>Uses Lombok annotations to generate boilerplate code such as getters,
 * setters, constructors, and builder methods.</p>
 *
 * @author Daniel
 * @version 1.0
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsuarioRolId implements Serializable {

    /** Identifier of the user. */
    @Column(name = "id_usuario")
    private Integer idUsuario;

    /** Identifier of the role. */
    @Column(name = "id_rol")
    private Integer idRol;
}
