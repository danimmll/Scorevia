package usuariosroles.services.favoritos;

import usuariosroles.dto.FavoritoDTO;
import java.util.List;

public interface FavoritoService {
    void addEquipoFavorito(Integer idUsuario, Integer idEquipoApi);
    void removeEquipoFavorito(Integer idUsuario, Integer idEquipoApi);
    List<FavoritoDTO> getEquiposFavoritos(Integer idUsuario);

    void addLigaFavorita(Integer idUsuario, Integer idLigaApi);
    void removeLigaFavorita(Integer idUsuario, Integer idLigaApi);
    List<FavoritoDTO> getLigasFavoritas(Integer idUsuario);

    void addPartidoFavorito(Integer idUsuario, Integer idPartidoApi);
    void removePartidoFavorito(Integer idUsuario, Integer idPartidoApi);
    List<FavoritoDTO> getPartidosFavoritos(Integer idUsuario);
}