package usuariosroles.entities;

import jakarta.persistence.*;
import lombok.*;
import usuariosroles.models.UsuarioEquipoFavoritoId;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios_equipos_favoritos", schema = "sch_usuarios")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsuarioEquipoFavorito {

    @EmbeddedId
    private UsuarioEquipoFavoritoId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("idUsuario")
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @Column(name = "fecha_agregado", insertable = false, updatable = false)
    private LocalDateTime fechaAgregado;
}