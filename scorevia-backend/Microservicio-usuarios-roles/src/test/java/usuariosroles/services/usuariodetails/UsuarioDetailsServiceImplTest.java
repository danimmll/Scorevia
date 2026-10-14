package usuariosroles.services.usuariodetails;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import usuariosroles.entities.Rol;
import usuariosroles.entities.Usuario;
import usuariosroles.repositories.UsuarioRepository;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioDetailsServiceImplTest {

    @Mock
    private UsuarioRepository repository;

    @InjectMocks
    private UsuarioDetailsServiceImpl service;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setCorreoUsuario("test@test.com");
        usuario.setContrasenhaUsuario("password");
        usuario.setActivo(true);
        usuario.setEmailVerificado(true);

        Rol rol = new Rol();
        rol.setNombreRol("FREE");
        usuario.setRoles(Set.of(rol));
    }

    @Test
    void loadUserByUsername_Exito() {
        when(repository.findByCorreoUsuario("test@test.com")).thenReturn(Optional.of(usuario));

        UserDetails result = service.loadUserByUsername("test@test.com");

        assertNotNull(result);
        assertEquals("test@test.com", result.getUsername());
        assertTrue(result.getAuthorities().stream().anyMatch(a -> Objects.equals(a.getAuthority(), "ROLE_FREE")));
    }

    @Test
    void loadUserByUsername_NoExiste_LanzaUsernameNotFound() {
        when(repository.findByCorreoUsuario("no@test.com")).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("no@test.com"));
    }

    @Test
    void loadUserByUsername_CuentaInactiva_LanzaDisabled() {
        usuario.setActivo(false);
        when(repository.findByCorreoUsuario("test@test.com")).thenReturn(Optional.of(usuario));
        assertThrows(DisabledException.class, () -> service.loadUserByUsername("test@test.com"));
    }

    @Test
    void loadUserByUsername_EmailNoVerificado_LanzaDisabled() {
        usuario.setEmailVerificado(false);
        when(repository.findByCorreoUsuario("test@test.com")).thenReturn(Optional.of(usuario));
        assertThrows(DisabledException.class, () -> service.loadUserByUsername("test@test.com"));
    }

    @Test
    void loadUserByUsername_BloqueadoPorFuerzaBruta_LanzaDisabled() {
        usuario.setBloqueadoLoginHasta(LocalDateTime.of(2099, Month.JANUARY, 1, 0, 0));
        when(repository.findByCorreoUsuario("test@test.com")).thenReturn(Optional.of(usuario));

        assertThrows(DisabledException.class, () -> service.loadUserByUsername("test@test.com"));
    }

    @Test
    void loadUserByUsername_BloqueoExpirado_PermiteAcceso() {
        usuario.setBloqueadoLoginHasta(LocalDateTime.of(2000, Month.JANUARY, 1, 0, 0));
        when(repository.findByCorreoUsuario("test@test.com")).thenReturn(Optional.of(usuario));

        UserDetails result = service.loadUserByUsername("test@test.com");

        assertNotNull(result);
    }
}