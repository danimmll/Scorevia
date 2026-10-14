package usuariosroles.services.usuariorol;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import usuariosroles.dto.UsuarioRolDTO;
import usuariosroles.entities.Rol;
import usuariosroles.entities.Usuario;
import usuariosroles.entities.UsuarioRol;
import usuariosroles.exception.BadRequestException;
import usuariosroles.exception.DuplicateException;
import usuariosroles.mappers.UsuarioRolMapper;
import usuariosroles.models.UsuarioRolId;
import usuariosroles.repositories.RolRepository;
import usuariosroles.repositories.UsuarioRepository;
import usuariosroles.repositories.UsuarioRolRepository;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioRolServiceImplTest {

    @Mock
    private UsuarioRolRepository repository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private RolRepository rolRepository;
    @Mock
    private UsuarioRolMapper mapper;

    @InjectMocks
    private UsuarioRolServiceImpl service;

    private Usuario usuario;
    private Rol rol;
    private UsuarioRol usuarioRol;
    private UsuarioRolDTO usuarioRolDTO;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setIdUsuario(1);
        usuario.setCorreoUsuario("test@test.com");
        usuario.setRoles(new HashSet<>());

        rol = new Rol();
        rol.setIdRol(1);
        rol.setNombreRol("ADMIN");

        usuarioRol = new UsuarioRol();
        usuarioRol.setId(new UsuarioRolId(1, 1));
        usuarioRol.setUsuario(usuario);
        usuarioRol.setRol(rol);

        usuarioRolDTO = new UsuarioRolDTO();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // --- asignarRolUsuario ---
    @Test
    void asignarRolUsuario_Exito() {
        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.of(usuario));
        when(rolRepository.findByIdRolAndActivo(1, true)).thenReturn(Optional.of(rol));
        when(repository.saveAndFlush(any())).thenReturn(usuarioRol);
        when(mapper.toDtoDeleteGetPost(any())).thenReturn(usuarioRolDTO);

        UsuarioRolDTO result = service.asignarRolUsuario(1, 1);

        assertNotNull(result);
        verify(repository).saveAndFlush(any());
    }

    @Test
    void asignarRolUsuario_PierdeCarreraDeAsignacionConcurrente_LanzaDuplicateException() {
        // Dos asignaciones concurrentes del mismo rol pueden pasar ambas el chequeo en
        // memoria (usuario.getRoles().contains(rol)) antes de que ninguna haga commit; la
        // segunda revienta por la PK compuesta (idUsuario, idRol) al hacer flush.
        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.of(usuario));
        when(rolRepository.findByIdRolAndActivo(1, true)).thenReturn(Optional.of(rol));
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThrows(DuplicateException.class, () -> service.asignarRolUsuario(1, 1));
    }

    @Test
    void asignarRolUsuario_UsuarioNoExiste_RetornaNull() {
        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.empty());
        assertNull(service.asignarRolUsuario(1, 1));
    }

    @Test
    void asignarRolUsuario_RolNoExiste_RetornaNull() {
        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.of(usuario));
        when(rolRepository.findByIdRolAndActivo(1, true)).thenReturn(Optional.empty());
        assertNull(service.asignarRolUsuario(1, 1));
    }

    @Test
    void asignarRolUsuario_YaAsignado_LanzaExcepcion() {
        usuario.getRoles().add(rol);
        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.of(usuario));
        when(rolRepository.findByIdRolAndActivo(1, true)).thenReturn(Optional.of(rol));

        assertThrows(DuplicateException.class, () -> service.asignarRolUsuario(1, 1));
    }

    // --- quitarRolUsuario ---
    @Test
    void quitarRolUsuario_Exito_ComoAdmin() {
        setUpSecurityContext("admin@test.com", "ROLE_ADMIN");
        when(usuarioRepository.findByCorreoUsuario("admin@test.com")).thenReturn(Optional.of(new Usuario()));
        when(repository.findById(any())).thenReturn(Optional.of(usuarioRol));
        when(mapper.toDtoDeleteGetPost(any())).thenReturn(usuarioRolDTO);

        UsuarioRolDTO result = service.quitarRolUsuario(1, 1);

        assertNotNull(result);
        verify(repository).deleteById(any());
    }

    @Test
    void quitarRolUsuario_SinAutenticacion_LanzaExcepcion() {
        assertThrows(BadRequestException.class, () -> service.quitarRolUsuario(1, 1));
    }

    @Test
    void quitarRolUsuario_NoEsAdminNiElMismo_LanzaExcepcion() {
        setUpSecurityContext("otro@test.com", "ROLE_FREE");
        Usuario otroUsuario = new Usuario();
        otroUsuario.setIdUsuario(99);
        when(usuarioRepository.findByCorreoUsuario("otro@test.com")).thenReturn(Optional.of(otroUsuario));

        assertThrows(BadRequestException.class, () -> service.quitarRolUsuario(1, 1));
    }

    @Test
    void quitarRolUsuario_RelacionNoExiste_RetornaNull() {
        setUpSecurityContext("test@test.com", "ROLE_FREE");
        when(usuarioRepository.findByCorreoUsuario("test@test.com")).thenReturn(Optional.of(usuario));
        when(repository.findById(any())).thenReturn(Optional.empty());

        assertNull(service.quitarRolUsuario(1, 1));
    }

    // --- Consultas (GET) ---
    @Test
    void getRolesUsuario_Exito() {
        when(repository.findByUsuarioIdUsuario(1)).thenReturn(List.of(usuarioRol));
        when(mapper.toDtoRolesUsuario(any())).thenReturn(usuarioRolDTO);
        assertFalse(service.getRolesUsuario(1).isEmpty());
    }

    @Test
    void getUsuariosRol_Exito() {
        when(repository.findByRolIdRol(1)).thenReturn(List.of(usuarioRol));
        when(mapper.toDtoUsuariosRol(any())).thenReturn(usuarioRolDTO);
        assertFalse(service.getUsuariosRol(1).isEmpty());
    }

    @Test
    void getAsignaciones_Exito() {
        Pageable pageable = PageRequest.of(0, 20);
        when(repository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(usuarioRol)));
        when(mapper.toDtoDeleteGetPost(any())).thenReturn(usuarioRolDTO);
        assertFalse(service.getAsignaciones(pageable).isEmpty());
    }

    // Método utilitario para simular el login en Mockito
    private void setUpSecurityContext(String username, String role) {
        Authentication auth = mock(Authentication.class);
        SecurityContext context = mock(SecurityContext.class);
        Collection<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));

        doReturn(username).when(auth).getName();
        doReturn(authorities).when(auth).getAuthorities();
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);
    }
}