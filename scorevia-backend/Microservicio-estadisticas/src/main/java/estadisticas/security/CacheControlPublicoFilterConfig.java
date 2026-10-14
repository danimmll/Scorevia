package estadisticas.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registra {@link CacheControlPublicoFilter}.
 *
 * <p>Con un {@code FilterRegistrationBean} en vez de un {@code @Component} por dos motivos:
 * el orden queda escrito (va el ultimo, despues de la cadena de seguridad, para que su
 * Cache-Control sea el que sobrevive) y el filtro no pasa por el proxy de Spring Cloud, que
 * es lo que impedia arrancar Tomcat.</p>
 */
@Configuration
public class CacheControlPublicoFilterConfig {

    @Bean
    public FilterRegistrationBean<CacheControlPublicoFilter> cacheControlPublico() {
        FilterRegistrationBean<CacheControlPublicoFilter> registro =
                new FilterRegistrationBean<>(new CacheControlPublicoFilter());
        registro.addUrlPatterns("/*");
        // ANTES de la cadena de seguridad: asi la cabecera ya esta puesta cuando pasa
        // CacheControlHeadersWriter, que se salta su escritura si encuentra una.
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registro;
    }
}
