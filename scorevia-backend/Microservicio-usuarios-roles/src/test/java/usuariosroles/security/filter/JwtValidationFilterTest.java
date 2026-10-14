package usuariosroles.security.filter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import usuariosroles.config.IncidenciasClient;
import usuariosroles.dto.IncidenciaDTO;
import usuariosroles.entities.Usuario;
import usuariosroles.repositories.UsuarioRepository;
import usuariosroles.services.jwt.JwtTokenService;
import usuariosroles.services.jwt.JwtTokenServiceImpl;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static usuariosroles.security.filter.TokenJwtConfig.SECRET_KEY;

@ExtendWith(MockitoExtension.class)
class JwtValidationFilterTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private IncidenciasClient incidenciasClient;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private FilterChain filterChain;

    private ObjectMapper objectMapper;
    private JwtValidationFilter filter;
    // null: este test solo ejercita validarTokenExtraerClaims(), que no toca refreshTokenRepository.
    private final JwtTokenService jwtTokenService = new JwtTokenServiceImpl(null);

    // Llave alternativa para probar SignatureException
    private final SecretKey wrongSecret = Jwts.SIG.HS256.key().build();

    // MixIn para enseñar a Jackson cómo instanciar SimpleGrantedAuthority en el test
    abstract static class SimpleGrantedAuthorityMixin {
        @JsonCreator
        public SimpleGrantedAuthorityMixin(@JsonProperty("authority") String role) {}
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // 1. Añadimos el módulo de fechas para que no explote con Instant.now()
        objectMapper.registerModule(new JavaTimeModule());
        // 2. Añadimos el MixIn para que sepa deserializar los roles del token
        objectMapper.addMixIn(SimpleGrantedAuthority.class, SimpleGrantedAuthorityMixin.class);

        filter = new JwtValidationFilter(authenticationManager, objectMapper, incidenciasClient, usuarioRepository, jwtTokenService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ===================================================================================
    // TESTS DE RUTAS PÚBLICAS Y TOKEN AUSENTE
    // ===================================================================================

    @Test
    void doFilterInternal_RutaPublica_PasaElFiltroSinValidar() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/login"); // Ruta pública
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilterInternal_ExcepcionRutasPublicas_AuthMe_RequiereToken() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/me"); // Aunque empieza por /api/v1/auth, está excluida en esRutaPublica
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response); // Pasa pero sin autenticar porque no hay token
    }

    @Test
    void doFilterInternal_SinToken_PasaElFiltroSinAutenticar() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/protegido");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilterInternal_TokenSinBearer_PasaElFiltroSinAutenticar() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/protegido");
        request.addHeader(HttpHeaders.AUTHORIZATION, "TokenInvalido");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    // ===================================================================================
    // TESTS DE AUTENTICACIÓN EXITOSA (Y EXTRACCIÓN DE ROLES)
    // ===================================================================================

    @Test
    void doFilterInternal_TokenValidoRolesCollection_AutenticaCorrectamente() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/protegido");

        List<Object> roles = Arrays.asList("USER", "{authority=ADMIN}", "ROLE_SUPER");
        String token = generarToken("test@test.com", 1, roles, false, SECRET_KEY);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        MockHttpServletResponse response = new MockHttpServletResponse();

        Usuario mockUsuario = new Usuario();
        mockUsuario.setActivo(true);
        when(usuarioRepository.findByCorreoUsuario("test@test.com")).thenReturn(Optional.of(mockUsuario));

        filter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("test@test.com", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        assertEquals(1, request.getAttribute("idUsuario"));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_TokenValidoRolesString_AutenticaCorrectamente() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/protegido");

        // Roles como JSON String (escenario switch case String)
        String rolesString = "[{\"authority\":\"ROLE_USER\"}]";
        String token = generarToken("test@test.com", 2, rolesString, false, SECRET_KEY);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        MockHttpServletResponse response = new MockHttpServletResponse();
        Usuario mockUsuario = new Usuario();
        mockUsuario.setActivo(true);
        when(usuarioRepository.findByCorreoUsuario("test@test.com")).thenReturn(Optional.of(mockUsuario));

        filter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> Objects.equals(a.getAuthority(), "ROLE_USER")));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_TokenValidoRolesNullODefault_AutenticaCorrectamenteSinRoles() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/protegido");

        // Roles = null (escenario switch case null/default)
        String token = generarToken("test@test.com", 3, null, false, SECRET_KEY);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        MockHttpServletResponse response = new MockHttpServletResponse();
        Usuario mockUsuario = new Usuario();
        mockUsuario.setActivo(true);
        when(usuarioRepository.findByCorreoUsuario("test@test.com")).thenReturn(Optional.of(mockUsuario));

        filter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().isEmpty());
    }

    // ===================================================================================
    // TESTS DE EXCEPCIONES Y ERRORES JWT
    // ===================================================================================

    @Test
    void doFilterInternal_RefreshTokenUsadoComoAccessToken_Retorna401YNoAutentica() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/protegido");
        // Un refresh token (token_type=REFRESH) no debe servir para autenticar peticiones normales
        String token = generarToken("test@test.com", 1, null, false, SECRET_KEY, "REFRESH");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        // timeout(): el reporte a incidencias ahora corre en un hilo virtual aparte
        // (fire-and-forget), así que puede no haber terminado todavía justo aquí.
        verify(incidenciasClient, timeout(1000)).reportarIncidencia(any(IncidenciaDTO.class));
    }

    @Test
    void doFilterInternal_UsuarioNoExisteOInactivo_Retorna401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/protegido");
        String token = generarToken("inactivo@test.com", 1, null, false, SECRET_KEY);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Simulamos usuario inactivo
        Usuario mockUsuario = new Usuario();
        mockUsuario.setActivo(false);
        when(usuarioRepository.findByCorreoUsuario("inactivo@test.com")).thenReturn(Optional.of(mockUsuario));

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        // timeout(): el reporte a incidencias ahora corre en un hilo virtual aparte
        // (fire-and-forget), así que puede no haber terminado todavía justo aquí.
        verify(incidenciasClient, timeout(1000)).reportarIncidencia(any(IncidenciaDTO.class));
    }

    @Test
    void doFilterInternal_TokenExpirado_Retorna401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/protegido");
        // Token con expiración en el pasado
        String token = generarToken("test@test.com", 1, null, true, SECRET_KEY);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        // timeout(): el reporte a incidencias ahora corre en un hilo virtual aparte
        // (fire-and-forget), así que puede no haber terminado todavía justo aquí.
        verify(incidenciasClient, timeout(1000)).reportarIncidencia(any(IncidenciaDTO.class));
    }

    @Test
    void doFilterInternal_TokenMalFormado_Retorna400() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/protegido");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token.basura.invalido");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(400, response.getStatus());
        // timeout(): el reporte a incidencias ahora corre en un hilo virtual aparte
        // (fire-and-forget), así que puede no haber terminado todavía justo aquí.
        verify(incidenciasClient, timeout(1000)).reportarIncidencia(any(IncidenciaDTO.class));
    }

    @Test
    void doFilterInternal_FirmaInvalida_Retorna401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/protegido");
        // Firmamos con otra clave diferente
        String token = generarToken("test@test.com", 1, null, false, wrongSecret);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        // timeout(): el reporte a incidencias ahora corre en un hilo virtual aparte
        // (fire-and-forget), así que puede no haber terminado todavía justo aquí.
        verify(incidenciasClient, timeout(1000)).reportarIncidencia(any(IncidenciaDTO.class));
    }

    @Test
    void doFilterInternal_ErrorCriticoOJSONInvalido_Retorna403() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/protegido");

        // Metemos un JSON inválido en roles para provocar JsonProcessingException en extraerRoles -> IllegalArgumentException
        String token = generarToken("test@test.com", 1, "json_invalido", false, SECRET_KEY);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        Usuario mockUsuario = new Usuario();
        mockUsuario.setActivo(true);
        when(usuarioRepository.findByCorreoUsuario("test@test.com")).thenReturn(Optional.of(mockUsuario));

        MockHttpServletResponse response = new MockHttpServletResponse();

        // Simulamos que el micro de incidencias falla para cubrir también ese catch
        doThrow(new RuntimeException("Micro incidencias caído")).when(incidenciasClient).reportarIncidencia(any());

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(403, response.getStatus());
        // Comprobamos que el flujo no peta aunque incidencias haya fallado
    }

    // ===================================================================================
    // HELPER PARA GENERAR TOKENS (Sin usar reloj del sistema)
    // ===================================================================================

    private String generarToken(String correo, Integer idUsuario, Object roles, boolean expirado, SecretKey key) {
        return generarToken(correo, idUsuario, roles, expirado, key, "ACCESS");
    }

    private String generarToken(String correo, Integer idUsuario, Object roles, boolean expirado, SecretKey key, String tokenType) {
        // Fechas fijas absolutas para evitar dependencias del reloj del sistema (Evita Flaky Tests)
        // Año 2000 para tokens expirados, Año 2100 para tokens válidos
        Instant fixedInstant = expirado ? Instant.parse("2000-01-01T00:00:00Z") : Instant.parse("2100-01-01T00:00:00Z");

        return Jwts.builder()
                .claim("correo", correo)
                .claim("idUsuario", idUsuario)
                .claim("authorities", roles)
                .claim("token_type", tokenType)
                .issuedAt(Date.from(fixedInstant.minusSeconds(3600))) // Emitido una hora antes de su timestamp fijado
                .expiration(Date.from(fixedInstant))
                .signWith(key)
                .compact();
    }
}