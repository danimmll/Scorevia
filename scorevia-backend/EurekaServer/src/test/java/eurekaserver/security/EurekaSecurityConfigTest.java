package eurekaserver.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Verifica el comportamiento real de {@link EurekaSecurityConfig}: {@code /actuator/health}
 * accesible sin credenciales (lo consulta el healthcheck de docker-compose), y cualquier
 * otra ruta exige HTTP Basic. Único test file existente en el módulo EurekaServer hasta ahora.
 * <p>
 * Usa {@link RestTemplate} en vez de {@code TestRestTemplate} (no disponible en este
 * classpath de test) para hacer peticiones HTTP reales contra el puerto aleatorio.
 * </p>
 * <p>
 * Regresión real detectada en Jenkins (2026-08-31): el test de credenciales válidas
 * hardcodeaba el literal "eureka"/"eureka", asumiendo que EUREKA_USERNAME/EUREKA_PASSWORD
 * no estaban en el entorno. En Jenkins SÍ está fijado {@code EUREKA_USERNAME=scorevia_eureka}
 * a nivel de pipeline (ver Jenkinsfile), así que el usuario real configurado no era "eureka"
 * y el test fallaba con 401 aun siendo la app correcta. Ahora lee las credenciales
 * REALMENTE configuradas vía {@code @Value}, igual que hace la propia app en
 * application.yml -- así el test es válido tanto en local (cae al default) como en Jenkins
 * (usa lo que de verdad esté fijado), en vez de asumir un valor concreto.
 * </p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EurekaSecurityConfigTest {

    @LocalServerPort
    private int port;

    @Value("${spring.security.user.name}")
    private String usuarioConfigurado;

    @Value("${spring.security.user.password}")
    private String passwordConfigurada;

    private final RestTemplate restTemplate = new RestTemplate();

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpStatus estadoDe(String path, HttpHeaders headers) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return (HttpStatus) response.getStatusCode();
        } catch (HttpStatusCodeException e) {
            return (HttpStatus) e.getStatusCode();
        }
    }

    @Test
    void actuatorHealth_AccesibleSinCredenciales() {
        assertEquals(HttpStatus.OK, estadoDe("/actuator/health", new HttpHeaders()));
    }

    @Test
    void rutaEureka_SinCredenciales_Devuelve401() {
        assertEquals(HttpStatus.UNAUTHORIZED, estadoDe("/eureka/apps", new HttpHeaders()));
    }

    @Test
    void rutaEureka_ConCredencialesConfiguradas_NoDevuelve401() {
        // Las credenciales que la app realmente usa (inyectadas arriba desde
        // spring.security.user.name/password), no un literal -- caen a "eureka"/"eureka" en
        // local si EUREKA_USERNAME/EUREKA_PASSWORD no están en el entorno, pero usan lo que
        // sea que el entorno real (Jenkins, docker-compose) haya fijado.
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(usuarioConfigurado, passwordConfigurada);

        assertNotEquals(HttpStatus.UNAUTHORIZED, estadoDe("/eureka/apps", headers));
    }
}
