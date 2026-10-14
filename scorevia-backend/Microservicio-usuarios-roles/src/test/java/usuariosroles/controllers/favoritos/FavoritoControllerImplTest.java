package usuariosroles.controllers.favoritos;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import usuariosroles.config.ValidationUtils;
import usuariosroles.dto.FavoritoDTO;
import usuariosroles.entities.Usuario;
import usuariosroles.exception.BadRequestException;
import usuariosroles.exception.ForbiddenException;
import usuariosroles.exception.NotFoundException;
import usuariosroles.repositories.UsuarioRepository;
import usuariosroles.services.favoritos.FavoritoService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FavoritoControllerImplTest {

    @Mock private FavoritoService favoritoService;
    @Mock private UsuarioRepository usuarioRepository;

    @InjectMocks
    private FavoritoControllerImpl controller;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // El uso de lenient() evita el UnnecessaryStubbingException de Mockito
    private void mockAuth(String role, String email) {
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.isAuthenticated()).thenReturn(true);
        lenient().when(auth.getName()).thenReturn(email);
        lenient().doReturn(List.of(new SimpleGrantedAuthority(role))).when(auth).getAuthorities();
        SecurityContext ctx = mock(SecurityContext.class);
        lenient().when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    // ==========================================
    // TESTS DE SEGURIDAD (resolverYValidarPropietario)
    // ==========================================

    @Test
    void authNull_ThrowsBadRequest() {
        SecurityContextHolder.getContext().setAuthentication(null);
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            assertThrows(BadRequestException.class, () -> controller.getEquiposFavoritos("1"));
        }
    }

    @Test
    void authNotAuthenticated_ThrowsBadRequest() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);

        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            assertThrows(BadRequestException.class, () -> controller.getEquiposFavoritos("1"));
        }
    }

    @Test
    void usuarioAuthNoEncontradoEnBd_ThrowsNotFound() {
        mockAuth("ROLE_USER", "fantasma@correo.com");
        when(usuarioRepository.findByCorreoUsuario("fantasma@correo.com")).thenReturn(Optional.empty());

        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            assertThrows(NotFoundException.class, () -> controller.getEquiposFavoritos("1"));
        }
    }

    @Test
    void usuarioAuthIntentandoVerOtroUsuario_ThrowsForbidden() {
        mockAuth("ROLE_USER", "otro@correo.com");
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(99); // El usuario del token es el 99, pero intenta ver el 1

        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            when(usuarioRepository.findByCorreoUsuario("otro@correo.com")).thenReturn(Optional.of(usuario));

            assertThrows(ForbiddenException.class, () -> controller.getEquiposFavoritos("1"));
        }
    }

    // ==========================================
    // TESTS ENDPOINTS EQUIPOS
    // ==========================================

    @Test
    void addEquipoFavorito_Exito() {
        mockAuth("ROLE_ADMIN", "admin@correo.com"); // El admin se salta las validaciones extra
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            val.when(() -> ValidationUtils.validarId("10")).thenReturn(10);

            ResponseEntity<Void> response = controller.addEquipoFavorito("1", "10");

            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            verify(favoritoService).addEquipoFavorito(1, 10);
        }
    }

    @Test
    void removeEquipoFavorito_Exito() {
        mockAuth("ROLE_ADMIN", "admin@correo.com");
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            val.when(() -> ValidationUtils.validarId("10")).thenReturn(10);

            ResponseEntity<Void> response = controller.removeEquipoFavorito("1", "10");

            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
            verify(favoritoService).removeEquipoFavorito(1, 10);
        }
    }

    @Test
    void getEquiposFavoritos_ExitoPropietario() {
        mockAuth("ROLE_USER", "owner@correo.com");
        Usuario owner = new Usuario();
        owner.setIdUsuario(1);

        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            when(usuarioRepository.findByCorreoUsuario("owner@correo.com")).thenReturn(Optional.of(owner));
            when(favoritoService.getEquiposFavoritos(1)).thenReturn(List.of(new FavoritoDTO()));

            ResponseEntity<List<FavoritoDTO>> response = controller.getEquiposFavoritos("1");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assert response.getBody() != null;
            assertFalse(response.getBody().isEmpty());
            verify(favoritoService).getEquiposFavoritos(1);
        }
    }

    // ==========================================
    // TESTS ENDPOINTS LIGAS
    // ==========================================

    @Test
    void addLigaFavorita_Exito() {
        mockAuth("ROLE_ADMIN", "admin@correo.com");
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            val.when(() -> ValidationUtils.validarId("10")).thenReturn(10);

            ResponseEntity<Void> response = controller.addLigaFavorita("1", "10");

            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            verify(favoritoService).addLigaFavorita(1, 10);
        }
    }

    @Test
    void removeLigaFavorita_Exito() {
        mockAuth("ROLE_ADMIN", "admin@correo.com");
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            val.when(() -> ValidationUtils.validarId("10")).thenReturn(10);

            ResponseEntity<Void> response = controller.removeLigaFavorita("1", "10");

            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
            verify(favoritoService).removeLigaFavorita(1, 10);
        }
    }

    @Test
    void getLigasFavoritas_Exito() {
        mockAuth("ROLE_ADMIN", "admin@correo.com");
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            when(favoritoService.getLigasFavoritas(1)).thenReturn(List.of(new FavoritoDTO()));

            ResponseEntity<List<FavoritoDTO>> response = controller.getLigasFavoritas("1");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assert response.getBody() != null;
            assertFalse(response.getBody().isEmpty());
        }
    }

    // ==========================================
    // TESTS ENDPOINTS PARTIDOS
    // ==========================================

    @Test
    void addPartidoFavorito_Exito() {
        mockAuth("ROLE_ADMIN", "admin@correo.com");
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            val.when(() -> ValidationUtils.validarId("10")).thenReturn(10);

            ResponseEntity<Void> response = controller.addPartidoFavorito("1", "10");

            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            verify(favoritoService).addPartidoFavorito(1, 10);
        }
    }

    @Test
    void removePartidoFavorito_Exito() {
        mockAuth("ROLE_ADMIN", "admin@correo.com");
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            val.when(() -> ValidationUtils.validarId("10")).thenReturn(10);

            ResponseEntity<Void> response = controller.removePartidoFavorito("1", "10");

            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
            verify(favoritoService).removePartidoFavorito(1, 10);
        }
    }

    @Test
    void getPartidosFavoritos_Exito() {
        mockAuth("ROLE_ADMIN", "admin@correo.com");
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            when(favoritoService.getPartidosFavoritos(1)).thenReturn(List.of(new FavoritoDTO()));

            ResponseEntity<List<FavoritoDTO>> response = controller.getPartidosFavoritos("1");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assert response.getBody() != null;
            assertFalse(response.getBody().isEmpty());
        }
    }
}