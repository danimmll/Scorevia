package usuariosroles.services.favoritos;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import usuariosroles.dto.FavoritoDTO;
import usuariosroles.entities.Usuario;
import usuariosroles.entities.UsuarioEquipoFavorito;
import usuariosroles.entities.UsuarioLigaFavorita;
import usuariosroles.entities.UsuarioPartidoFavorito;
import usuariosroles.exception.DuplicateException;
import usuariosroles.exception.NotFoundException;
import usuariosroles.mappers.FavoritoMapper;
import usuariosroles.models.UsuarioEquipoFavoritoId;
import usuariosroles.models.UsuarioLigaFavoritaId;
import usuariosroles.models.UsuarioPartidoFavoritoId;
import usuariosroles.repositories.UsuarioEquipoFavoritoRepository;
import usuariosroles.repositories.UsuarioLigaFavoritaRepository;
import usuariosroles.repositories.UsuarioPartidoFavoritoRepository;
import usuariosroles.repositories.UsuarioRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FavoritoServiceImpl implements FavoritoService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioEquipoFavoritoRepository equipoRepo;
    private final UsuarioLigaFavoritaRepository ligaRepo;
    private final UsuarioPartidoFavoritoRepository partidoRepo;
    private final FavoritoMapper favoritoMapper;

    private static final boolean ACTIVO = true;

    // findByIdUsuarioAndActivo, no findById: mismo patrón que UsuarioServiceImpl. Un usuario
    // borrado lógicamente (activo=false) no debería poder seguir gestionando favoritos aunque
    // algo bypasee el JwtValidationFilter (que ya rechaza inactivos), p. ej. una llamada
    // interna futura.
    private Usuario getUsuarioOrThrow(Integer idUsuario) {
        return usuarioRepository.findByIdUsuarioAndActivo(idUsuario, ACTIVO)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado con ID: " + idUsuario));
    }

    // --- EQUIPOS ---
    @Override
    @Transactional
    public void addEquipoFavorito(Integer idUsuario, Integer idEquipoApi) {
        Usuario usuario = getUsuarioOrThrow(idUsuario);
        if (equipoRepo.existsByUsuario_IdUsuarioAndId_IdEquipoApi(idUsuario, idEquipoApi)) {
            log.warn("El usuario {} ya tiene el equipo {} en favoritos", idUsuario, idEquipoApi);
            throw new DuplicateException("El equipo ya está en tu lista de favoritos.", "ya-en-favoritos");
        }
        UsuarioEquipoFavoritoId id = new UsuarioEquipoFavoritoId(idUsuario, idEquipoApi);
        equipoRepo.save(UsuarioEquipoFavorito.builder().id(id).usuario(usuario).build());
        log.info("Equipo {} añadido a favoritos del usuario {}", idEquipoApi, idUsuario);
    }

    @Override
    @Transactional
    public void removeEquipoFavorito(Integer idUsuario, Integer idEquipoApi) {
        getUsuarioOrThrow(idUsuario);
        equipoRepo.deleteFavoritoDirecto(idUsuario, idEquipoApi);
        log.info("Equipo {} eliminado de favoritos del usuario {}", idEquipoApi, idUsuario);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FavoritoDTO> getEquiposFavoritos(Integer idUsuario) {
        getUsuarioOrThrow(idUsuario);
        return equipoRepo.findByUsuario_IdUsuario(idUsuario).stream()
                .map(favoritoMapper::toDto).toList();
    }

    // --- LIGAS ---
    @Override
    @Transactional
    public void addLigaFavorita(Integer idUsuario, Integer idLigaApi) {
        Usuario usuario = getUsuarioOrThrow(idUsuario);
        if (ligaRepo.existsByUsuario_IdUsuarioAndId_IdLigaApi(idUsuario, idLigaApi)) {
            throw new DuplicateException("La liga ya está en tu lista de favoritos.", "ya-en-favoritos");
        }
        UsuarioLigaFavoritaId id = new UsuarioLigaFavoritaId(idUsuario, idLigaApi);
        ligaRepo.save(UsuarioLigaFavorita.builder().id(id).usuario(usuario).build());
    }

    @Override
    @Transactional
    public void removeLigaFavorita(Integer idUsuario, Integer idLigaApi) {
        getUsuarioOrThrow(idUsuario);
        ligaRepo.deleteByUsuario_IdUsuarioAndId_IdLigaApi(idUsuario, idLigaApi);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FavoritoDTO> getLigasFavoritas(Integer idUsuario) {
        getUsuarioOrThrow(idUsuario);
        return ligaRepo.findByUsuario_IdUsuario(idUsuario).stream()
                .map(favoritoMapper::toDto).toList();
    }

    // --- PARTIDOS ---
    @Override
    @Transactional
    public void addPartidoFavorito(Integer idUsuario, Integer idPartidoApi) {
        Usuario usuario = getUsuarioOrThrow(idUsuario);
        if (partidoRepo.existsByUsuario_IdUsuarioAndId_IdPartidoApi(idUsuario, idPartidoApi)) {
            throw new DuplicateException("El partido ya está en tu lista de favoritos.", "ya-en-favoritos");
        }
        UsuarioPartidoFavoritoId id = new UsuarioPartidoFavoritoId(idUsuario, idPartidoApi);
        partidoRepo.save(UsuarioPartidoFavorito.builder().id(id).usuario(usuario).build());
    }

    @Override
    @Transactional
    public void removePartidoFavorito(Integer idUsuario, Integer idPartidoApi) {
        getUsuarioOrThrow(idUsuario);
        partidoRepo.deleteByUsuario_IdUsuarioAndId_IdPartidoApi(idUsuario, idPartidoApi);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FavoritoDTO> getPartidosFavoritos(Integer idUsuario) {
        getUsuarioOrThrow(idUsuario);
        return partidoRepo.findByUsuario_IdUsuario(idUsuario).stream()
                .map(favoritoMapper::toDto).toList();
    }
}