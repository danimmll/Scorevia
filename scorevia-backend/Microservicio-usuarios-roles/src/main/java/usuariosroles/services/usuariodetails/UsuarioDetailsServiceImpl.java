package usuariosroles.services.usuariodetails;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.DisabledException; // <-- Nuevo import
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import usuariosroles.entities.Usuario;
import usuariosroles.repositories.UsuarioRepository;

@Service
@RequiredArgsConstructor
public class UsuarioDetailsServiceImpl implements UserDetailsService {

    private final UsuarioRepository repository;

    @Transactional(readOnly = true)
    @Override
    @NonNull
    public UserDetails loadUserByUsername(@NonNull String correo) {
        Optional<Usuario> optional = repository.findByCorreoUsuario(correo);

        if (optional.isEmpty()) {
            throw new UsernameNotFoundException("Este correo no existe en la base de datos");
        }

        Usuario usuario = getUsuario(optional);

        // Construir lista de permisos a partir de los roles del usuario
        List<GrantedAuthority> permisos = usuario.getRoles().stream()
                .map(rol -> new SimpleGrantedAuthority("ROLE_" + rol.getNombreRol()))
                .collect(Collectors.toList());

        // Crear objeto UserDetails con credenciales y permisos
        return new User(
                usuario.getCorreoUsuario(),
                usuario.getContrasenhaUsuario(),
                true, // Lo forzamos a true porque las validaciones de habilitado las hacemos nosotros arriba con DisabledException
                true, // cuenta no expirada
                true, // credenciales no expiradas
                true, // cuenta no bloqueada
                permisos
        );
    }

    private static @NonNull Usuario getUsuario(Optional<Usuario> optional) {
        Usuario usuario = optional.orElseThrow();

        // 1. Comprobar si la cuenta está dada de baja (activo = false)
        if (usuario.getActivo() != null && !usuario.getActivo()) {
            throw new DisabledException("La cuenta está desactivada. Ponte en contacto con soporte.");
        }

        // 2. Comprobar si ha verificado el email
        if (usuario.getEmailVerificado() == null || !usuario.getEmailVerificado()) {
            throw new DisabledException("Cuenta no verificada. Por favor, introduce el código de verificación que hemos enviado a tu correo.");
        }

        // 3. Antifuerza bruta: si superó el máximo de intentos fallidos, bloqueado temporalmente
        if (usuario.getBloqueadoLoginHasta() != null
                && usuario.getBloqueadoLoginHasta().isAfter(java.time.LocalDateTime.now(java.time.ZoneId.systemDefault()))) {
            throw new DisabledException("Demasiados intentos fallidos. Vuelve a intentarlo más tarde.");
        }

        return usuario;
    }

}