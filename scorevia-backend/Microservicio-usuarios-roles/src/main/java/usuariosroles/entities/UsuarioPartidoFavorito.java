package usuariosroles.entities;

import jakarta.persistence.*;
import lombok.*;
import usuariosroles.models.UsuarioPartidoFavoritoId;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios_partidos_favoritos", schema = "sch_usuarios")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsuarioPartidoFavorito {

    @EmbeddedId
    private UsuarioPartidoFavoritoId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("idUsuario")
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @Column(name = "fecha_agregado", insertable = false, updatable = false)
    private LocalDateTime fechaAgregado;
}