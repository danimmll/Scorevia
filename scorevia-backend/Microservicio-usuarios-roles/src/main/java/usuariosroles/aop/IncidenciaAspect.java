package usuariosroles.aop;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.AfterThrowing;
import usuariosroles.config.IncidenciasClient;
import usuariosroles.dto.IncidenciaDTO;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import usuariosroles.exception.BadRequestException;
import usuariosroles.exception.ConflictException;
import usuariosroles.exception.DuplicateException;
import usuariosroles.exception.ForbiddenException;
import usuariosroles.exception.InvalidTokenTypeException;
import usuariosroles.exception.NoContentException;
import usuariosroles.exception.NotFoundException;

@Aspect
@Component
@RequiredArgsConstructor
public class IncidenciaAspect {

    private final IncidenciasClient incidenciasClient;

    private static final Logger logger = LoggerFactory.getLogger(IncidenciaAspect.class);

    // Rechazos de negocio esperables (contraseña incorrecta, nickname duplicado, recurso
    // inexistente...) no son incidencias del sistema. Antes se reportaban igual que un
    // fallo real, inundando la tabla de incidencias de ruido y sumando hasta el timeout
    // completo de Feign (3s) a la respuesta de cada rechazo si el servicio de incidencias
    // estaba lento.
    private static final Set<Class<? extends Throwable>> EXCEPCIONES_DE_NEGOCIO = Set.of(
            BadRequestException.class, ConflictException.class, DuplicateException.class,
            ForbiddenException.class, NotFoundException.class, NoContentException.class,
            InvalidTokenTypeException.class);

    @AfterThrowing(pointcut = "within(usuariosroles.controllers..*) || within(usuariosroles.services..*)", throwing = "ex")
    public void registrarError(JoinPoint joinPoint, Throwable ex) {
        if (EXCEPCIONES_DE_NEGOCIO.contains(ex.getClass())) {
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

        // Fuera del hilo de la petición: antes, un servicio de incidencias lento sumaba
        // hasta su timeout completo de Feign (3s) a la respuesta de CADA error real,
        // encima del propio 500/403 que el cliente ya iba a recibir.
        Thread.startVirtualThread(() -> reportarIncidenciaAsync(incidencia));
    }

    private void reportarIncidenciaAsync(IncidenciaDTO incidencia) {
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