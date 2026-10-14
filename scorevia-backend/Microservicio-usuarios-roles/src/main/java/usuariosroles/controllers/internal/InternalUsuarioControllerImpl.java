package usuariosroles.controllers.internal;

import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import usuariosroles.exception.NotFoundException;
import usuariosroles.services.usuario.UsuarioService;

@RestController
@RequestMapping("/api/internal/usuarios")
@RequiredArgsConstructor
public class InternalUsuarioControllerImpl implements InternalUsuarioController {

    private final UsuarioService usuarioService;

    @Override
    public Map<Integer, String> obtenerNicknames(List<Integer> idsUsuarios) {
        return usuarioService.obtenerNicknames(idsUsuarios);
    }

    @Override
    public ResponseEntity<Void> existeUsuario(Integer idUsuario) {
        if (!usuarioService.existeUsuario(idUsuario)) {
            throw new NotFoundException("Usuario no encontrado", "usuario-no-encontrado");
        }
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> usuarioActivo(Integer idUsuario) {
        if (!usuarioService.usuarioActivo(idUsuario)) {
            throw new NotFoundException("Usuario no encontrado o no activo");
        }
        return ResponseEntity.ok().build();
    }
}