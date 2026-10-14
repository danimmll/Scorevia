package gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * Rate limiting en memoria para /api/v1/auth/** (login y registro).
 * <p>
 * Spring Cloud Gateway solo trae de serie un {@code RequestRateLimiter} respaldado por
 * Redis, y este proyecto no tiene Redis desplegado. Como defensa mínima contra fuerza
 * bruta distribuida (probar contraseñas contra muchas cuentas a la vez) y spam de
 * registro -- el bloqueo de cuenta tras 5 intentos fallidos en usuarios-roles solo frena
 * el ataque vertical, contra una cuenta concreta --, este filtro limita peticiones por IP
 * a un ritmo generoso pero real. Más estricto que {@link GeneralRateLimiterFilter}, que
 * cubre el resto de rutas públicas.
 * </p>
 */
@Component
public class AuthRateLimiterFilter extends RateLimiterPorIpFilter {

    private static final String RUTA_PROTEGIDA = "/api/v1/auth";
    private static final int LIMITE_PETICIONES_POR_VENTANA = 20;
    private static final long DURACION_VENTANA_SEGUNDOS = 60;

    public AuthRateLimiterFilter() {
        super(LIMITE_PETICIONES_POR_VENTANA, DURACION_VENTANA_SEGUNDOS);
    }

    @Override
    protected boolean aplicaA(String path) {
        return bajoPrefijo(path, RUTA_PROTEGIDA);
    }

    @Override
    protected String nombreDelLimite() {
        return "de /auth";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
