package usuariosroles.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class InternalServiceAuthFilterTest {

    private InternalServiceAuthFilter filter;
    private final String secreto = "mi-secreto-super-seguro";

    @BeforeEach
    void setUp() {
        filter = new InternalServiceAuthFilter(secreto);
    }

    @Test
    void doFilterInternal_RutaInfraestructura_PasaElFiltro() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_FaltaSecreto_Retorna403() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/usuarios");
        // No añadimos el header
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertEquals(403, response.getStatus());
        assertEquals("application/json", response.getContentType());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void doFilterInternal_SecretoIncorrecto_Retorna403() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/usuarios");
        request.addHeader(InternalServiceAuthFilter.HEADER_SECRETO_INTERNO, "secreto-equivocado");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertEquals(403, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void doFilterInternal_SecretoCorrecto_PasaElFiltro() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/usuarios");
        request.addHeader(InternalServiceAuthFilter.HEADER_SECRETO_INTERNO, secreto);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}