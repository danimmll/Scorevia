package estadisticas.aop;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import estadisticas.config.feign.IncidenciasClient;
import estadisticas.dto.incidencias.IncidenciaDTO;
import estadisticas.exception.BadRequestException;
import estadisticas.exception.ConflictException;
import estadisticas.exception.DuplicateException;
import estadisticas.exception.ForbiddenException;
import estadisticas.exception.NotFoundException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@RequiredArgsConstructor
public class IncidenciaAspect {

    private final IncidenciasClient incidenciasClient;

    private static final Logger log = LoggerFactory.getLogger(IncidenciaAspect.class);

    @AfterThrowing(pointcut = "within(estadisticas.controllers..*) || within(estadisticas.services..*)", throwing = "ex")
    public void registrarError(JoinPoint joinPoint, Throwable ex) {
        // Errores de negocio/validación rutinarios (4xx esperados, ver GlobalExceptionHandler)
        // no son incidencias reales: reportarlos aquí solo añadía latencia síncrona a
        // peticiones que ya iban a fallar en milisegundos y saturaba de ruido el
        // microservicio de incidencias. ApiFootballException/SecurityConfigException SÍ
        // siguen reportándose: son fallos genuinos (API externa caída, mala configuración).
        if (esErrorDeValidacionRutinario(ex)) {
            return;
        }

        String endpoint = obtenerEndpoint();
        String claseCompleta = joinPoint.getSignature().getDeclaringTypeName();
        String clase = claseCompleta.substring(claseCompleta.lastIndexOf('.') + 1);
        String metodo = joinPoint.getSignature().getName();

        Throwable causaReal = ex;
        while (causaReal.getCause() != null && causaReal != causaReal.getCause()) {
            causaReal = causaReal.getCause();
        }

        StringWriter sw = new StringWriter();
        causaReal.printStackTrace(new PrintWriter(sw));
        String trazaCompleta = sw.toString();

        IncidenciaDTO incidencia = new IncidenciaDTO();
        incidencia.setEndpoint(endpoint);
        incidencia.setTipo(causaReal.getClass().getSimpleName());
        incidencia.setClase(clase);
        incidencia.setMetodo(metodo);
        incidencia.setTraza(trazaCompleta);
        incidencia.setFecha(LocalDateTime.now(ZoneId.systemDefault()));
        incidencia.setUsuarioContexto(obtenerUsuarioActual());

        try {
            incidenciasClient.reportarIncidencia(incidencia);
            log.info("Incidencia registrada en el microservicio central de incidencias.");
        } catch (Exception feignException) {
            log.error("CRÍTICO: No se pudo contactar con el Microservicio de Incidencias. Causa: {}"
                    ,feignException.getMessage());
        }
    }

    private boolean esErrorDeValidacionRutinario(Throwable ex) {
        return ex instanceof BadRequestException
                || ex instanceof NotFoundException
                || ex instanceof ConflictException
                || ex instanceof DuplicateException
                || ex instanceof ForbiddenException;
    }

    private String obtenerEndpoint() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            return request.getRequestURI();
        }
        return "Unknown";
    }

    private String obtenerUsuarioActual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof String correo) {
            return correo;
        }
        return "Anónimo";
    }
}