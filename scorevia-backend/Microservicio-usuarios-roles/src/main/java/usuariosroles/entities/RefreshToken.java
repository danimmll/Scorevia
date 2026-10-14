package usuariosroles.entities;

import java.time.LocalDateTime;
import java.time.ZoneId;

import jakarta.persistence.*;
import lombok.*;

/**
 * Rastro server-side de cada refresh token emitido, para poder rotarlos y revocarlos.
 * Sin esta tabla los refresh tokens son JWTs puramente stateless: un token robado seguiría
 * siendo válido hasta sus 7 días de vida aunque el usuario cierre sesión. Con ella, cada
 * refresh usado se marca revocado y se emite uno nuevo (rotación); si un token ya revocado
 * se intenta reusar, se interpreta como robo y se revocan todas las sesiones del usuario.
 *
 * @author Daniel
 * @version 1.0
 */
@Entity
@Table(name = "refresh_tokens", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"jti"}, name = "jti_UNIQUE")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_refresh_token")
    private Long idRefreshToken;

    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    @Column(name = "jti", nullable = false, length = 36)
    private String jti;

    @Column(name = "fecha_expiracion", nullable = false)
    private LocalDateTime fechaExpiracion;

    @Column(name = "revocado", nullable = false)
    @Builder.Default
    private Boolean revocado = false;

    @Column(name = "fecha_creacion", nullable = false)
    @Builder.Default
    private LocalDateTime fechaCreacion = LocalDateTime.now(ZoneId.systemDefault());
}
