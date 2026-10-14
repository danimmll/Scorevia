package incidencias.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import incidencias.services.incidencias.IncidenciaServiceImpl;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtValidationFilterTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private ObjectMapper objectMapper;
    @Mock private IncidenciaServiceImpl incidenciaService;
    @Mock private FilterChain filterChain;

    private JwtValidationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtValidationFilter(authenticationManager, objectMapper, incidenciaService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_EsRutaPublica_PasaFiltro() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v3/api-docs/incidencias");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void doFilterInternal_SinToken_PasaFiltroSinAutenticar() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/protegida");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void doFilterInternal_TokenValidoConColeccionRoles_Exito() throws ServletException, IOException {
        // Generamos un token real firmado con la clave secreta de test
        String token = Jwts.builder()
                .subject("test@test.com")
                .claim("correo", "test@test.com")
                .claim("idUsuario", 1)
                .claim("authorities", List.of("ROLE_ADMIN"))
                .claim("token_type", "ACCESS")
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(TokenJwtConfig.SECRET_KEY)
                .compact();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/protegida");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("test@test.com", SecurityContextHolder.getContext().getAuthentication().getName());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void doFilterInternal_TokenTipoRefresh_Retorna401SinAutenticar() throws ServletException, IOException {
        // Los REFRESH token no llevan 'authorities' y solo deben usarse para pedir un nuevo
        // ACCESS token en usuarios-roles: este filtro debe rechazarlos como Bearer.
        String token = Jwts.builder()
                .subject("test@test.com")
                .claim("correo", "test@test.com")
                .claim("idUsuario", 1)
                .claim("token_type", "REFRESH")
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(TokenJwtConfig.SECRET_KEY)
                .compact();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/protegida");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(401, response.getStatus());
        verify(incidenciaService, times(1)).crearIncidenciaDTO(any());
    }

    @Test
    void doFilterInternal_TokenFalso_AtrapaExcepcionYPasaFiltro_ConIncidencia() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/protegida");
        request.addHeader("Authorization", "Bearer token.invalido.falso");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(incidenciaService, times(1)).crearIncidenciaDTO(any());
        verify(objectMapper, times(1)).writeValue(any(java.io.OutputStream.class), any());
    }
}