package usuariosroles.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import usuariosroles.entities.UsuarioLigaFavorita;
import usuariosroles.models.UsuarioLigaFavoritaId;
import java.util.List;

@Repository
public interface UsuarioLigaFavoritaRepository extends JpaRepository<UsuarioLigaFavorita, UsuarioLigaFavoritaId> {

    List<UsuarioLigaFavorita> findByUsuario_IdUsuario(Integer idUsuario);

    @Modifying
    @Transactional
    @Query("DELETE FROM UsuarioLigaFavorita u WHERE u.usuario.idUsuario = :idUsuario AND u.id.idLigaApi = :idLigaApi")
    void deleteByUsuario_IdUsuarioAndId_IdLigaApi(@Param("idUsuario") Integer idUsuario, @Param("idLigaApi") Integer idLigaApi);

    boolean existsByUsuario_IdUsuarioAndId_IdLigaApi(Integer idUsuario, Integer idLigaApi);
}