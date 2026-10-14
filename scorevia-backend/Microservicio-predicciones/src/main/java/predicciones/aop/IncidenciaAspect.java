package predicciones.aop;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import predicciones.config.IncidenciasClient;
import predicciones.dto.IncidenciaDTO;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Aspect
@Component
@RequiredArgsConstructor
public class IncidenciaAspect {

    private final IncidenciasClient incidenciasClient;

    private static final Logger logger = LoggerFactory.getLogger(IncidenciaAspect.class);

    @AfterThrowing(pointcut = "within(predicciones.controllers..*) || within(predicciones.services..*)", throwing = "ex")
    public void registrarError(JoinPoint joinPoint, Throwable ex) {
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
            logger.info("Incidencia registrada en el microservicio central de incidencias.");
        } catch (Exception feignException) {
            logger.error("CRÍTICO: No se pudo contactar con el Microservicio de Incidencias. Causa: {}"
                    ,feignException.getMessage());
        }
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