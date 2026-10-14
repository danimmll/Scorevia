package usuariosroles.controllers.favoritos;

import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import usuariosroles.config.ValidationUtils;
import usuariosroles.dto.FavoritoDTO;
import usuariosroles.entities.Usuario;
import usuariosroles.exception.BadRequestException;
import usuariosroles.exception.ForbiddenException;
import usuariosroles.exception.NotFoundException;
import usuariosroles.repositories.UsuarioRepository;
import usuariosroles.services.favoritos.FavoritoService;

/**
 * REST controller for managing a user's favorite teams, leagues and matches.
 *
 * <p>All operations verify that the authenticated user is either the owner of the
 * {@code id_usuario} path segment or has the ADMIN role, to prevent IDOR-style access
 * to another user's watchlist.</p>
 *
 * @author Daniel
 * @version 1.0
 */
@RestController
@RequestMapping("/api/v1/usuarios/{id_usuario}/favoritos")
@RequiredArgsConstructor
@Tag(name = "Favoritos", description = "Operaciones sobre equipos, ligas y partidos favoritos")
public class FavoritoControllerImpl implements FavoritoController {

    private final FavoritoService favoritoService;
    private final UsuarioRepository usuarioRepository;

    private Integer resolverYValidarPropietario(String idUsuarioString) {
        Integer idUsuario = ValidationUtils.validarId(idUsuarioString);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BadRequestException("No hay usuario autenticado.", "no-autenticado");
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).filter(Objects::nonNull)
                .anyMatch(rol -> rol.equals("ROLE_ADMIN"));

        if (isAdmin) {
            return idUsuario;
        }

        String correoAuth = authentication.getName();
        Usuario usuarioAuth = usuarioRepository.findByCorreoUsuario(correoAuth)
                .orElseThrow(() -> new NotFoundException("Usuario autenticado no encontrado"));

        if (!usuarioAuth.getIdUsuario().equals(idUsuario)) {
            throw new ForbiddenException("No puedes gestionar los favoritos de otro usuario.");
        }
        return idUsuario;
    }

    // --- EQUIPOS ---

    @PostMapping("/equipos/{id_equipo_api}")
    @Override
    public ResponseEntity<Void> addEquipoFavorito(@PathVariable("id_usuario") String idUsuarioString,
                                                  @PathVariable("id_equipo_api") String idEquipoApiString) {
        Integer idUsuario = resolverYValidarPropietario(idUsuarioString);
        Integer idEquipoApi = ValidationUtils.validarId(idEquipoApiString);
        favoritoService.addEquipoFavorito(idUsuario, idEquipoApi);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/equipos/{id_equipo_api}")
    @Override
    public ResponseEntity<Void> removeEquipoFavorito(@PathVariable("id_usuario") String idUsuarioString,
                                                     @PathVariable("id_equipo_api") String idEquipoApiString) {
        Integer idUsuario = resolverYValidarPropietario(idUsuarioString);
        Integer idEquipoApi = ValidationUtils.validarId(idEquipoApiString);
        favoritoService.removeEquipoFavorito(idUsuario, idEquipoApi);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/equipos")
    @Override
    public ResponseEntity<List<FavoritoDTO>> getEquiposFavoritos(@PathVariable("id_usuario") String idUsuarioString) {
        Integer idUsuario = resolverYValidarPropietario(idUsuarioString);
        return ResponseEntity.ok(favoritoService.getEquiposFavoritos(idUsuario));
    }

    // --- LIGAS ---

    @PostMapping("/ligas/{id_liga_api}")
    @Override
    public ResponseEntity<Void> addLigaFavorita(@PathVariable("id_usuario") String idUsuarioString,
                                                @PathVariable("id_liga_api") String idLigaApiString) {
        Integer idUsuario = resolverYValidarPropietario(idUsuarioString);
        Integer idLigaApi = ValidationUtils.validarId(idLigaApiString);
        favoritoService.addLigaFavorita(idUsuario, idLigaApi);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/ligas/{id_liga_api}")
    @Override
    public ResponseEntity<Void> removeLigaFavorita(@PathVariable("id_usuario") String idUsuarioString,
                                                   @PathVariable("id_liga_api") String idLigaApiString) {
        Integer idUsuario = resolverYValidarPropietario(idUsuarioString);
        Integer idLigaApi = ValidationUtils.validarId(idLigaApiString);
        favoritoService.removeLigaFavorita(idUsuario, idLigaApi);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/ligas")
    @Override
    public ResponseEntity<List<FavoritoDTO>> getLigasFavoritas(@PathVariable("id_usuario") String idUsuarioString) {
        Integer idUsuario = resolverYValidarPropietario(idUsuarioString);
        return ResponseEntity.ok(favoritoService.getLigasFavoritas(idUsuario));
    }

    // --- PARTIDOS ---

    @PostMapping("/partidos/{id_partido_api}")
    @Override
    public ResponseEntity<Void> addPartidoFavorito(@PathVariable("id_usuario") String idUsuarioString,
                                                   @PathVariable("id_partido_api") String idPartidoApiString) {
        Integer idUsuario = resolverYValidarPropietario(idUsuarioString);
        Integer idPartidoApi = ValidationUtils.validarId(idPartidoApiString);
        favoritoService.addPartidoFavorito(idUsuario, idPartidoApi);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/partidos/{id_partido_api}")
    @Override
    public ResponseEntity<Void> removePartidoFavorito(@PathVariable("id_usuario") String idUsuarioString,
                                                      @PathVariable("id_partido_api") String idPartidoApiString) {
        Integer idUsuario = resolverYValidarPropietario(idUsuarioString);
        Integer idPartidoApi = ValidationUtils.validarId(idPartidoApiString);
        favoritoService.removePartidoFavorito(idUsuario, idPartidoApi);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/partidos")
    @Override
    public ResponseEntity<List<FavoritoDTO>> getPartidosFavoritos(@PathVariable("id_usuario") String idUsuarioString) {
        Integer idUsuario = resolverYValidarPropietario(idUsuarioString);
        return ResponseEntity.ok(favoritoService.getPartidosFavoritos(idUsuario));
    }
}