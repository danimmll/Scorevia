package estadisticas.security.filter;

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

import javax.crypto.SecretKey;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

import static estadisticas.security.filter.TokenJwtConfig.SECRET_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtValidationFilterTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private FilterChain filterChain;

    private ObjectMapper objectMapper;
    private JwtValidationFilter filter;

    private final SecretKey wrongSecret = Jwts.SIG.HS256.key().build();

    @SuppressWarnings("unused")
    abstract static class SimpleGrantedAuthorityMixin {
        @JsonCreator
        public SimpleGrantedAuthorityMixin(@JsonProperty("authority") String role) {}
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.addMixIn(SimpleGrantedAuthority.class, SimpleGrantedAuthorityMixin.class);

        filter = new JwtValidationFilter(authenticationManager, objectMapper);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_RutaPublica_PasaElFiltroSinValidar() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/swagger-ui/index.html");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilterInternal_SinToken_PasaElFiltroSinAutenticar() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/estadisticas");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilterInternal_TokenSinBearer_PasaElFiltroSinAutenticar() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/estadisticas");
        request.addHeader(HttpHeaders.AUTHORIZATION, "TokenInvalido");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilterInternal_TokenValidoRolesCollection_AutenticaCorrectamente() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/estadisticas");

        List<Object> roles = Arrays.asList("USER", "{authority=ADMIN}", "ROLE_SUPER");
        String token = generarToken("test@test.com", 1, roles, false, SECRET_KEY);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("test@test.com", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        assertEquals(1, request.getAttribute("idUsuario"));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_TokenValidoRolesString_AutenticaCorrectamente() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/estadisticas");

        String rolesString = "[{\"authority\":\"ROLE_USER\"}]";
        String token = generarToken("test@test.com", 2, rolesString, false, SECRET_KEY);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> Objects.equals(a.getAuthority(), "ROLE_USER")));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_TokenValidoRolesNullODefault_AutenticaCorrectamenteSinRoles() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/estadisticas");

        String token = generarToken("test@test.com", 3, null, false, SECRET_KEY);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().isEmpty());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_TokenExpirado_Retorna401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/estadisticas");
        String token = generarToken("test@test.com", 1, null, true, SECRET_KEY);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
    }

    @Test
    void doFilterInternal_TokenMalFormado_Retorna400() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/estadisticas");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token.basura.invalido");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(400, response.getStatus());
    }

    @Test
    void doFilterInternal_FirmaInvalida_Retorna401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/estadisticas");
        String token = generarToken("test@test.com", 1, null, false, wrongSecret);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
    }

    @Test
    void doFilterInternal_TokenTipoRefresh_Retorna401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/estadisticas");
        String token = generarToken("test@test.com", 1, null, false, SECRET_KEY, "REFRESH");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilterInternal_ErrorCriticoOJSONInvalido_Retorna403() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/estadisticas");

        String token = generarToken("test@test.com", 1, "json_invalido", false, SECRET_KEY);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(403, response.getStatus());
    }

    private String generarToken(String correo, Integer idUsuario, Object roles, boolean expirado, SecretKey key) {
        return generarToken(correo, idUsuario, roles, expirado, key, "ACCESS");
    }

    private String generarToken(String correo, Integer idUsuario, Object roles, boolean expirado, SecretKey key, String tipoToken) {
        Instant fixedInstant = expirado ? Instant.parse("2000-01-01T00:00:00Z") : Instant.parse("2100-01-01T00:00:00Z");

        return Jwts.builder()
                .claim("correo", correo)
                .claim("idUsuario", idUsuario)
                .claim("authorities", roles)
                .claim("token_type", tipoToken)
                .issuedAt(Date.from(fixedInstant.minusSeconds(3600)))
                .expiration(Date.from(fixedInstant))
                .signWith(key)
                .compact();
    }
}