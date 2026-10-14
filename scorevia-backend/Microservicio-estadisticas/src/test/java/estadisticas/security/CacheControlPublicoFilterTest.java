package estadisticas.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Que se puede cachear y que no.
 *
 * <p>Esto toca cabeceras de seguridad, asi que el test no comprueba solo que lo publico se
 * cachee: comprueba sobre todo que <b>todo lo demas siga con el mismo {@code no-store} de
 * antes</b>. Aflojar la cache de una respuesta con datos de usuario es el error que este
 * fichero existe para que no ocurra en silencio.</p>
 */
class CacheControlPublicoFilterTest {

    private final CacheControlPublicoFilter filtro = new CacheControlPublicoFilter();

    private MockHttpServletResponse pasar(String metodo, String ruta, String autorizacion)
            throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest(metodo, ruta);
        peticion.setRequestURI(ruta);
        if (autorizacion != null) {
            peticion.addHeader("Authorization", autorizacion);
        }
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        FilterChain cadena = mock(FilterChain.class);
        filtro.doFilter(peticion, respuesta, cadena);
        verify(cadena).doFilter(peticion, respuesta);
        return respuesta;
    }

    // -------------------------------------------------------------------------------------
    // Lo publico se cachea
    // -------------------------------------------------------------------------------------
    @Test
    @DisplayName("Los datos publicos salen cacheables, con su tiempo segun lo que cambian")
    void datosPublicosSeCachean() throws Exception {
        assertEquals("public, max-age=10",
                pasar("GET", "/api/v1/partidos/en-vivo", null).getHeader("Cache-Control"));
        assertEquals("public, max-age=30",
                pasar("GET", "/api/v1/partidos/hoy", null).getHeader("Cache-Control"));
        assertEquals("public, max-age=60",
                pasar("GET", "/api/v1/partidos/123", null).getHeader("Cache-Control"));
        assertEquals("public, max-age=300",
                pasar("GET", "/api/v1/ligas", null).getHeader("Cache-Control"));
        assertEquals("public, max-age=300",
                pasar("GET", "/api/v1/jugadores/9/perfil", null).getHeader("Cache-Control"));
    }

    @Test
    @DisplayName("El directo caduca antes que un catalogo: lo especifico gana al prefijo")
    void loEspecificoGanaAlPrefijo() throws Exception {
        // /api/v1/partidos/en-vivo empieza por /api/v1/partidos, que son 60s. Si el orden del
        // mapa se rompiera, el directo se quedaria cacheado un minuto y el marcador iria tarde.
        assertEquals("public, max-age=10",
                pasar("GET", "/api/v1/partidos/en-vivo", null).getHeader("Cache-Control"));
    }

    @Test
    @DisplayName("Se limpian Pragma y Expires, que en algunos intermediarios ganan al max-age")
    void limpiaPragmaYExpires() throws Exception {
        MockHttpServletResponse r = pasar("GET", "/api/v1/ligas", null);
        assertEquals("", r.getHeader("Pragma"));
        assertEquals("", r.getHeader("Expires"));
    }

    // -------------------------------------------------------------------------------------
    // Todo lo demas sigue sin cachearse
    // -------------------------------------------------------------------------------------
    // Lo que NO es publico se deja SIN TOCAR a proposito: el filtro corre antes que la
    // cadena de seguridad, y CacheControlHeadersWriter de Spring Security escribe su
    // no-cache/no-store siempre que no encuentre ya una de las tres cabeceras. Asi el valor
    // por defecto sigue siendo el seguro y este filtro es solo la excepcion explicita.
    //
    // Poner aqui un no-store a mano parecia mas claro y era peor: el filtro no llega a
    // ejecutarse en los 401 y 403 (seguridad corta antes), asi que esas respuestas se
    // quedaban SIN NINGUNA cabecera de cache. Medido en el contenedor: un 403 salia pelado.

    @Test
    @DisplayName("Una ruta que no esta en la lista se deja para Spring Security")
    void rutaNoPublicaNoSeToca() throws Exception {
        assertNull(pasar("GET", "/api/v1/partidos-privados", null).getHeader("Cache-Control"));
        assertNull(pasar("GET", "/actuator/health", null).getHeader("Cache-Control"));
        assertNull(pasar("GET", "/internal/algo", null).getHeader("Cache-Control"));
    }

    @Test
    @DisplayName("Solo GET: un POST a una ruta publica no se marca cacheable")
    void soloGet() throws Exception {
        assertNull(pasar("POST", "/api/v1/partidos/sync", null).getHeader("Cache-Control"));
        assertNull(pasar("DELETE", "/api/v1/equipos/1", null).getHeader("Cache-Control"));
    }

    @Test
    @DisplayName("Con Authorization NO se marca cacheable, aunque la ruta sea publica")
    void conCredencialesNoSeCachea() throws Exception {
        // Hoy ningun controlador de este servicio mira el usuario, asi que la respuesta seria
        // la misma. Pero marcar como "public" algo que llego con credenciales es la clase de
        // error que no se nota hasta que se nota.
        assertNull(pasar("GET", "/api/v1/ligas", "Bearer eyJhb").getHeader("Cache-Control"));
    }

    @Test
    @DisplayName("Un prefijo parecido no cuela: /api/v1/ligasfalsas no es /api/v1/ligas")
    void prefijoParecidoNoCuela() throws Exception {
        assertNull(pasar("GET", "/api/v1/ligasfalsas", null).getHeader("Cache-Control"),
                "solo debe encajar la ruta exacta o con / detras, no lo que empiece igual");
    }

    @Test
    @DisplayName("Un GET de admin dentro de un prefijo publico NO se marca cacheable")
    void recorteDeAdminNoSeCachea() throws Exception {
        // /api/v1/partidos/estado-ingesta empieza por /api/v1/partidos, que si es publico,
        // pero SpringSecurityConfig lo recorta a rol admin. Sin la lista de excepciones se
        // marcaba como public, max-age=60. Medido en el contenedor: un 403 salia cacheable.
        assertNull(pasar("GET", "/api/v1/partidos/estado-ingesta", null).getHeader("Cache-Control"));
    }
}
