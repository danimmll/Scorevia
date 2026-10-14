package usuariosroles.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import usuariosroles.config.IncidenciasClient;
import usuariosroles.entities.Usuario;
import usuariosroles.repositories.UsuarioRepository;
import usuariosroles.services.jwt.JwtTokenService;

import java.io.IOException;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private AuthenticationManager manager;
    @Mock private IncidenciasClient incidenciasClient;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private JwtTokenService jwtTokenService;

    private ObjectMapper objectMapper;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        filter = new JwtAuthenticationFilter(manager, incidenciasClient, usuarioRepository, objectMapper,
                false, "Lax", jwtTokenService);
    }

    @Test
    void attemptAuthentication_Valido_RetornaAutenticacion() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        Usuario creds = new Usuario();
        creds.setCorreoUsuario("test@test.com");
        creds.setContrasenhaUsuario("1234");
        request.setContent(objectMapper.writeValueAsBytes(creds));
        MockHttpServletResponse response = new MockHttpServletResponse();

        Authentication mockAuth = mock(Authentication.class);
        when(manager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(mockAuth);

        Authentication result = filter.attemptAuthentication(request, response);

        assertNotNull(result);
        assertEquals("test@test.com", request.getAttribute("correoIntento"));
    }

    @Test
    void attemptAuthentication_JsonInvalido_LanzaExcepcion() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent("bad-json".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(BadCredentialsException.class, () -> filter.attemptAuthentication(request, response));
    }

    @Test
    void successfulAuthentication_PrincipalInvalido_Retorna401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        Authentication authResult = mock(Authentication.class);
        when(authResult.getPrincipal()).thenReturn("PrincipalStringInvalido");

        filter.successfulAuthentication(request, response, chain, authResult);

        assertEquals(401, response.getStatus());
    }

    @Test
    void successfulAuthentication_UsuarioValido_GeneraTokensYReseteaIntentos() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        User userDetails = new User("test@test.com", "pass", Collections.emptyList());
        Authentication authResult = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        Usuario mockUsuario = new Usuario();
        mockUsuario.setIdUsuario(1);
        mockUsuario.setCorreoUsuario("test@test.com");
        mockUsuario.setIntentosLogin(3); // Tiene intentos previos que deben limpiarse

        when(usuarioRepository.findByCorreoUsuario("test@test.com")).thenReturn(Optional.of(mockUsuario));
        when(jwtTokenService.generarAccessToken(eq(1), eq("test@test.com"), any())).thenReturn("mock-access-token");
        when(jwtTokenService.generarRefreshToken(1, "test@test.com")).thenReturn("mock-refresh-token");

        filter.successfulAuthentication(request, response, chain, authResult);

        assertEquals(200, response.getStatus());
        assertEquals(0, mockUsuario.getIntentosLogin());
        assertNull(mockUsuario.getBloqueadoLoginHasta());
        verify(usuarioRepository).save(mockUsuario);

        String responseBody = response.getContentAsString();
        assertTrue(responseBody.contains("mock-access-token"));
        assertNotNull(response.getHeader("Set-Cookie"));
        assertTrue(Objects.requireNonNull(response.getHeader("Set-Cookie")).contains("refreshToken=mock-refresh-token"));
    }

    @Test
    void unsuccessfulAuthentication_UsuarioInvalido_IncrementaIntentosYRegistraIncidencia() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("correoIntento", "test@test.com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Usuario mockUsuario = new Usuario();
        mockUsuario.setCorreoUsuario("test@test.com");
        mockUsuario.setIntentosLogin(4); // Al sumar 1, llegará al máximo (5) y se bloqueará

        when(usuarioRepository.findByCorreoUsuario("test@test.com")).thenReturn(Optional.of(mockUsuario));

        filter.unsuccessfulAuthentication(request, response, new BadCredentialsException("Bad creds"));

        assertEquals(400, response.getStatus());
        verify(usuarioRepository).save(mockUsuario);
        assertEquals(0, mockUsuario.getIntentosLogin()); // Se reinicia tras el bloqueo
        assertNotNull(mockUsuario.getBloqueadoLoginHasta()); // Se ha bloqueado
        // timeout(): el reporte a incidencias ahora corre en un hilo virtual aparte.
        verify(incidenciasClient, timeout(1000)).reportarIncidencia(any());
    }

    @Test
    void unsuccessfulAuthentication_UsuarioDeshabilitado_NoSumaIntentos() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("correoIntento", "test@test.com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.unsuccessfulAuthentication(request, response, new DisabledException("Cuenta deshabilitada"));

        assertEquals(400, response.getStatus());
        assertTrue(response.getContentAsString().contains("Cuenta deshabilitada"));
        // Comprobamos que no se ha sumado ningún intento
        verify(usuarioRepository, never()).findByCorreoUsuario(anyString());
    }
}