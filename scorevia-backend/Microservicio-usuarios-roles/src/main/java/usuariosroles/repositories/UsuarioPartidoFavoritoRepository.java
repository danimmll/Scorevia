package usuariosroles.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import usuariosroles.entities.UsuarioPartidoFavorito;
import usuariosroles.models.UsuarioPartidoFavoritoId;
import java.util.List;

@Repository
public interface UsuarioPartidoFavoritoRepository extends JpaRepository<UsuarioPartidoFavorito, UsuarioPartidoFavoritoId> {

    List<UsuarioPartidoFavorito> findByUsuario_IdUsuario(Integer idUsuario);

    boolean existsByUsuario_IdUsuarioAndId_IdPartidoApi(Integer idUsuario, Integer idPartidoApi);

    // FIX: Borrado seguro y eficiente sin traer datos a RAM
    @Modifying
    @Transactional
    @Query("DELETE FROM UsuarioPartidoFavorito u WHERE u.usuario.idUsuario = :idUsuario AND u.id.idPartidoApi = :idPartidoApi")
    void deleteByUsuario_IdUsuarioAndId_IdPartidoApi(@Param("idUsuario") Integer idUsuario, @Param("idPartidoApi") Integer idPartidoApi);
}