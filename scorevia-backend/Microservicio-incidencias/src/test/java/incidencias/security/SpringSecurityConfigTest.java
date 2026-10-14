package incidencias.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import incidencias.services.incidencias.IncidenciaServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

// Prueba directa de los handlers de excepciones de seguridad (extraídos a métodos con
// nombre precisamente para poder testearlos así): este módulo no tiene datasource de
// test configurado, así que un @SpringBootTest de integración no arrancaría aquí.
@ExtendWith(MockitoExtension.class)
class SpringSecurityConfigTest {

    @Mock
    private AuthenticationConfiguration configuration;

    @Mock
    private IncidenciaServiceImpl incidenciaService;

    private SpringSecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        securityConfig = new SpringSecurityConfig(configuration, objectMapper, incidenciaService);
    }

    @Test
    void manejarAutenticacionFallida_Devuelve401YReportaIncidencia() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/incidencias");
        MockHttpServletResponse response = new MockHttpServletResponse();

        securityConfig.manejarAutenticacionFallida(request, response, new BadCredentialsException("sin token"));

        assertEquals(401, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertTrue(response.getContentAsString().contains("Autenticación requerida"));
        verify(incidenciaService).crearIncidenciaDTO(org.mockito.ArgumentMatchers.argThat(dto ->
                "Acceso no autorizado".equals(dto.getTipo())
                        && "/api/v1/incidencias".equals(dto.getEndpoint())
                        && "authenticationEntryPoint".equals(dto.getMetodo())));
    }

    @Test
    void manejarAccesoDenegado_Devuelve403YReportaIncidenciaConMensajeOriginal() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/incidencias/5");
        MockHttpServletResponse response = new MockHttpServletResponse();

        securityConfig.manejarAccesoDenegado(request, response, new AccessDeniedException("Rol insuficiente"));

        assertEquals(403, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertTrue(response.getContentAsString().contains("Acceso denegado"));
        verify(incidenciaService).crearIncidenciaDTO(org.mockito.ArgumentMatchers.argThat(dto ->
                "Acceso denegado".equals(dto.getTipo())
                        && "/api/v1/incidencias/5".equals(dto.getEndpoint())
                        && "Rol insuficiente".equals(dto.getTraza())));
    }
}
