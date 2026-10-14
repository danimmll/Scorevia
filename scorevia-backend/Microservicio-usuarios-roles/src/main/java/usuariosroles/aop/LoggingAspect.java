package usuariosroles.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Aspect for logging method execution times and entry/exit points.
 *
 * <p>This aspect intercepts all method calls within the package
 * {@code scorevia.usuariosroles} and logs:</p>
 * <ul>
 *   <li>Timestamp when the method starts</li>
 *   <li>Timestamp when the method ends</li>
 *   <li>Total execution time in milliseconds</li>
 * </ul>
 *
 * <p>Useful for performance monitoring and debugging.</p>
 *
 * @author Daniel
 * @version 1.0
 */
@Aspect
@Component
public class LoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);

    /**
     * Logs method entry, exit, and execution time.
     *
     * @param joinPoint The join point providing method details.
     * @return The result of the method execution.
     * @throws Throwable if the intercepted method throws an exception.
     */
    @Around("execution(* usuariosroles.controllers..*(..)) || execution(* usuariosroles.services..*(..))")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        String metodo = joinPoint.getSignature().getName();
        String fechaInicio = LocalDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy / HH:mm:ss"));
    // A DEBUG y no a INFO. Estas dos lineas se escriben en CADA metodo del paquete, asi
    // que con el nivel del paquete en INFO son practicamente todo el log: medido el
    // 2026-09-10 sobre la maquina de desarrollo, sin un solo usuario delante, estadisticas
    // habia escrito 1.314 MB en 119 minutos (16 GB al dia) y 19.998 de cada 20.000 lineas
    // salian de aqui. Sigue estando para cuando haga falta: basta con poner el paquete en
    // DEBUG (<SERVICIO>_LOG_LEVEL=DEBUG) y vuelven todas.
        logger.debug("{} Entro en el método {}()", fechaInicio, metodo);

        long start = System.currentTimeMillis();
        Object proceed = joinPoint.proceed();
        long totalTime = System.currentTimeMillis() - start;

        String fechaFin = LocalDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy / HH:mm:ss"));
        logger.debug("{} Salgo del método {}(). Tiempo total: {} ms", fechaFin, metodo, totalTime);

        return proceed;
    }
}
