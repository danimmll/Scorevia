package incidencias.aop;

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
 * Aspect for logging method execution times within the incidencias package.
 * This aspect uses an {@code @Around} advice to log both the entry and exit of methods,
 * including the total execution time in milliseconds.
 *
 * <p>Main responsibilities:</p>
 * <ul>
 *   <li>Log the start time and method name when entering a method.</li>
 *   <li>Execute the target method and measure its execution time.</li>
 *   <li>Log the end time, method name, and total execution time when exiting.</li>
 * </ul> 
 */

@Aspect
@Component
public class LoggingAspect {

    /** Logger used to record method entry, exit, and execution time. */
    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);
    
    /**
     * Around advice that logs method entry and exit for all methods in the incidencias package.
     *
     * @param joinPoint the join point providing method signature and execution context
     * @return the result of the method execution
     * @throws Throwable if the target method throws any exception
     */
    
    @Around("execution(* incidencias..*(..))")
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

