package usuariosroles.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import usuariosroles.entities.UsuarioEquipoFavorito;
import usuariosroles.models.UsuarioEquipoFavoritoId;
import java.util.List;

@Repository
public interface UsuarioEquipoFavoritoRepository
        extends JpaRepository<UsuarioEquipoFavorito, UsuarioEquipoFavoritoId> {

    List<UsuarioEquipoFavorito> findByUsuario_IdUsuario(Integer idUsuario);

    boolean existsByUsuario_IdUsuarioAndId_IdEquipoApi(Integer idUsuario, Integer idEquipoApi);

    @Modifying
    @Transactional
    @Query("DELETE FROM UsuarioEquipoFavorito u WHERE u.usuario.idUsuario = :idUsuario AND u.id.idEquipoApi = :idEquipoApi")
    void deleteFavoritoDirecto(@Param("idUsuario") Integer idUsuario, @Param("idEquipoApi") Integer idEquipoApi);
}