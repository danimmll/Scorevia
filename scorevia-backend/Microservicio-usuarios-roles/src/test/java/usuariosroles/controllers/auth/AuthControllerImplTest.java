package usuariosroles.controllers.auth;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import usuariosroles.dto.GoogleTokenDTO;
import usuariosroles.dto.ResetPasswordDTO;
import usuariosroles.dto.SolicitarResetPasswordDTO;
import usuariosroles.dto.UsuarioDTO;
import usuariosroles.dto.VerificacionEmailDTO;
import usuariosroles.entities.Rol;
import usuariosroles.entities.Usuario;
import usuariosroles.exception.BadRequestException;
import usuariosroles.exception.DuplicateException;
import usuariosroles.exception.ForbiddenException;
import usuariosroles.exception.NotFoundException;
import usuariosroles.repositories.UsuarioRepository;
import usuariosroles.services.jwt.JwtTokenService;
import usuariosroles.services.usuario.UsuarioServiceImpl;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerImplTest {

    @Mock private UsuarioServiceImpl service;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private JwtTokenService jwtTokenService;
    @Mock private BindingResult bindingResult;

    @InjectMocks
    private AuthControllerImpl controller;

    private Usuario usuario;
    private UsuarioDTO usuarioDTO;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "secureCookie", false);

        Rol rolNormal = new Rol();
        rolNormal.setNombreRol("USER");

        // Añadimos un rol FACTOR para cubrir al 100% el .filter() de extraerAuthorities
        Rol rolFactor = new Rol();
        rolFactor.setNombreRol("FACTOR_SMS");

        usuario = new Usuario();
        usuario.setIdUsuario(1);
        usuario.setCorreoUsuario("test@test.com");
        usuario.setActivo(true);
        usuario.setRoles(new HashSet<>(List.of(rolNormal, rolFactor)));

        usuarioDTO = new UsuarioDTO();
        usuarioDTO.setCorreoUsuario("test@test.com");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==========================================
    // TESTS POST /register
    // ==========================================

    @Test
    void crearUsuario_CuentaDesactivada_Reactiva() {
        when(service.existsUsuarioDesactivado(anyString())).thenReturn(true);
        when(service.activarUsuarioDTO(any())).thenReturn(usuarioDTO);

        ResponseEntity<Object> response = controller.crearUsuario(usuarioDTO, bindingResult);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void crearUsuario_UsuarioYaExiste_LanzaDuplicateException() {
        when(service.existsUsuarioDesactivado(anyString())).thenReturn(false);
        when(service.existsUsuario(anyString())).thenReturn(true);

        assertThrows(DuplicateException.class, () -> controller.crearUsuario(usuarioDTO, bindingResult));
    }

    @Test
    void crearUsuario_ErroresValidacion_LanzaBadRequestYCompruebaSnakeCase() {
        when(bindingResult.hasFieldErrors()).thenReturn(true);

        // Esto fuerza a que se ejecute validation() y toSnakeCase()
        FieldError fieldError = new FieldError("usuarioDTO", "nombreUsuario", "No puede estar vacío");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> controller.crearUsuario(usuarioDTO, bindingResult));
        assertTrue(ex.getMessage().contains("nombre_usuario")); // Verifica que toSnakeCase funcionó
    }

    @Test
    void crearUsuario_Exito() {
        when(service.existsUsuarioDesactivado(anyString())).thenReturn(false);
        when(service.existsUsuario(anyString())).thenReturn(false);
        when(bindingResult.hasFieldErrors()).thenReturn(false);
        when(service.crearUsuario(any())).thenReturn(usuarioDTO);

        ResponseEntity<Object> response = controller.crearUsuario(usuarioDTO, bindingResult);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    // ==========================================
    // TESTS POST /verificar-email
    // ==========================================

    @Test
    void verificarEmail_Exito() {
        VerificacionEmailDTO dto = new VerificacionEmailDTO();
        dto.setCorreoUsuario("test@test.com");
        dto.setCodigo("123456");

        when(usuarioRepository.findByCorreoUsuario(anyString())).thenReturn(Optional.of(usuario));
        when(jwtTokenService.generarAccessToken(any(), any(), any())).thenReturn("access");
        when(jwtTokenService.generarRefreshToken(any(), any())).thenReturn("refresh");

        ResponseEntity<Object> response = controller.verificarEmail(dto);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void verificarEmail_UsuarioNoExiste_LanzaNotFoundException() {
        VerificacionEmailDTO dto = new VerificacionEmailDTO();
        dto.setCorreoUsuario("fantasma@test.com");

        when(usuarioRepository.findByCorreoUsuario(anyString())).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> controller.verificarEmail(dto));
    }

    // ==========================================
    // TESTS POST /solicitar-reset-password y /reset-password
    // ==========================================

    @Test
    void solicitarResetPassword_SiempreDevuelveOkConMensajeGenerico() {
        SolicitarResetPasswordDTO dto = new SolicitarResetPasswordDTO();
        dto.setCorreoUsuario("cualquiera@test.com");

        ResponseEntity<Object> response = controller.solicitarResetPassword(dto);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(service).solicitarResetPassword("cualquiera@test.com");
    }

    @Test
    void resetPassword_Exito_DevuelveOk() {
        ResetPasswordDTO dto = new ResetPasswordDTO();
        dto.setCorreoUsuario("test@test.com");
        dto.setCodigo("123456");
        dto.setNuevaContrasenha("N3wP@ss!!");

        ResponseEntity<Object> response = controller.resetPassword(dto);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(service).resetPassword("test@test.com", "123456", "N3wP@ss!!");
    }

    @Test
    void resetPassword_CodigoInvalido_PropagaLaExcepcionDelService() {
        ResetPasswordDTO dto = new ResetPasswordDTO();
        dto.setCorreoUsuario("test@test.com");
        dto.setCodigo("000000");
        dto.setNuevaContrasenha("N3wP@ss!!");

        doThrow(new BadRequestException("Código de restablecimiento incorrecto"))
                .when(service).resetPassword("test@test.com", "000000", "N3wP@ss!!");

        assertThrows(BadRequestException.class, () -> controller.resetPassword(dto));
    }

    // ==========================================
    // TESTS POST /google
    // ==========================================

    @Test
    void loginConGoogle_Exito() {
        GoogleTokenDTO dto = new GoogleTokenDTO("valid.token.google");
        when(service.procesarLoginGoogle(anyString())).thenReturn(usuario);
        when(jwtTokenService.generarAccessToken(any(), any(), any())).thenReturn("access");

        ResponseEntity<Object> response = controller.loginConGoogle(dto);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // ==========================================
    // TESTS POST /refresh
    // ==========================================

    @Test
    void refrescarToken_TokenNullOBlank_LanzaForbidden() {
        assertThrows(ForbiddenException.class, () -> controller.refrescarToken(null));
        assertThrows(ForbiddenException.class, () -> controller.refrescarToken("   "));
    }

    @Test
    void refrescarToken_TokenInvalido_LanzaForbidden() {
        when(jwtTokenService.validarTokenExtraerClaims(anyString(), anyString())).thenThrow(new RuntimeException());
        assertThrows(ForbiddenException.class, () -> controller.refrescarToken("invalid"));
    }

    @Test
    void refrescarToken_UsuarioInactivo_LanzaForbidden() {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("test@test.com");
        when(jwtTokenService.validarTokenExtraerClaims(anyString(), anyString())).thenReturn(claims);

        usuario.setActivo(false); // Inactivamos al usuario
        when(usuarioRepository.findByCorreoUsuario("test@test.com")).thenReturn(Optional.of(usuario));

        ForbiddenException ex = assertThrows(ForbiddenException.class, () -> controller.refrescarToken("valid_refresh"));
        assertTrue(ex.getMessage().contains("inactivo"));
    }

    @Test
    void refrescarToken_Exito() {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("test@test.com");
        when(jwtTokenService.validarTokenExtraerClaims(anyString(), anyString())).thenReturn(claims);
        when(usuarioRepository.findByCorreoUsuario("test@test.com")).thenReturn(Optional.of(usuario));
        when(jwtTokenService.generarAccessToken(any(), any(), any())).thenReturn("new_access");
        when(jwtTokenService.generarRefreshToken(any(), any())).thenReturn("new_refresh");

        ResponseEntity<Object> response = controller.refrescarToken("valid_refresh");
        assertEquals(HttpStatus.OK, response.getStatusCode());

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assert body != null;
        assertEquals("new_access", body.get("accessToken"));
        assertNotNull(response.getHeaders().get(HttpHeaders.SET_COOKIE));
        verify(jwtTokenService).verificarYRotarRefreshToken(claims);
    }

    @Test
    void refrescarToken_TokenReusado_LanzaForbidden() {
        Claims claims = mock(Claims.class);
        when(jwtTokenService.validarTokenExtraerClaims(anyString(), anyString())).thenReturn(claims);
        doThrow(new ForbiddenException("Se ha detectado un uso sospechoso de tu sesión"))
                .when(jwtTokenService).verificarYRotarRefreshToken(claims);

        assertThrows(ForbiddenException.class, () -> controller.refrescarToken("token_reusado"));
        verifyNoInteractions(usuarioRepository);
    }

    // ==========================================
    // TESTS GET /me
    // ==========================================

    @Test
    void getMe_AuthNull_LanzaBadRequest() {
        SecurityContextHolder.getContext().setAuthentication(null);
        assertThrows(BadRequestException.class, () -> controller.getMe());
    }

    @Test
    void getMe_AuthNoAutenticado_LanzaBadRequest() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThrows(BadRequestException.class, () -> controller.getMe());
    }

    @Test
    void getMe_AuthAnonymous_LanzaBadRequest() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("anonymousUser");
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThrows(BadRequestException.class, () -> controller.getMe());
    }

    @Test
    void getMe_UsuarioNotFound_LanzaNotFound() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("test@test.com");
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(usuarioRepository.findByCorreoUsuario(anyString())).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> controller.getMe());
    }

    @Test
    void getMe_Exito() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("test@test.com");
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_USER"))).when(auth).getAuthorities();
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(usuarioRepository.findByCorreoUsuario("test@test.com")).thenReturn(Optional.of(usuario));

        ResponseEntity<Object> response = controller.getMe();
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // ==========================================
    // TESTS POST /logout
    // ==========================================

    @Test
    void logout_SinCookie_NoIntentaRevocarPeroBorraLaCookie() {
        ResponseEntity<Object> response = controller.logout(null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getHeaders().get(HttpHeaders.SET_COOKIE));
        verifyNoInteractions(jwtTokenService);
    }

    @Test
    void logout_ConCookieValida_RevocaElRefreshTokenPorJti() {
        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn("jti-123");
        when(jwtTokenService.validarTokenExtraerClaims("token_valido", "REFRESH")).thenReturn(claims);

        ResponseEntity<Object> response = controller.logout("token_valido");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(jwtTokenService).revocarRefreshTokenPorJti("jti-123");
    }

    @Test
    void logout_ConCookieInvalida_NoPropagaYBorraLaCookieIgual() {
        when(jwtTokenService.validarTokenExtraerClaims("token_invalido", "REFRESH"))
                .thenThrow(new RuntimeException("token corrupto"));

        ResponseEntity<Object> response = controller.logout("token_invalido");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getHeaders().get(HttpHeaders.SET_COOKIE));
        verify(jwtTokenService, never()).revocarRefreshTokenPorJti(any());
    }
}