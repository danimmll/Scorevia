package estadisticas.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Decide cuanto se puede cachear cada respuesta.
 *
 * <h2>Que problema resuelve</h2>
 * Spring Security pone por defecto {@code Cache-Control: no-cache, no-store, max-age=0,
 * must-revalidate} en TODAS las respuestas. Para un endpoint con datos de un usuario es lo
 * correcto; para el catalogo de ligas o el resultado de un partido de 2015 es tirar trabajo.
 *
 * <p>Y ademas rompia algo que no se ve a simple vista: <b>Angular repetia en el navegador
 * todas las peticiones que ya habia hecho el servidor</b>. El render de servidor guarda sus
 * respuestas HTTP en el estado que viaja al navegador para que este no las repita, pero
 * {@code transferCacheInterceptorFn} descarta cualquier respuesta cuyo {@code Cache-Control}
 * diga {@code no-store} (se ve en {@code @angular/common/fesm2022/http.mjs}). Medido: el
 * bloque de estado transferido de /partidos pesaba 2,6 kB y contenia <b>solo</b> las
 * anotaciones de hidratacion, cero respuestas. O sea que cada pantalla pedia sus datos dos
 * veces y el usuario esperaba a la segunda tanda.
 *
 * <h2>Por que es seguro aqui</h2>
 * Solo se marcan como publicas las rutas de datos que ya son {@code permitAll} en
 * {@link SpringSecurityConfig}, y <b>ningun controlador de este microservicio lee el usuario
 * autenticado</b> (ni {@code Authentication}, ni {@code @AuthenticationPrincipal}, ni
 * {@code SecurityContext}): la respuesta es identica para todo el mundo, asi que no hay nada
 * personal que se pueda quedar en una cache intermedia.
 *
 * <p>Todo lo demas conserva exactamente las cabeceras de antes. La lista es explicita a
 * proposito: si manana un endpoint de estos pasa a depender del usuario, hay que sacarlo de
 * aqui, y para eso tiene que estar escrito y no deducido.
 *
 * <p><b>Implementa {@link Filter} a pelo y no extiende OncePerRequestFilter.</b> Como
 * {@code @Component}, Spring Cloud lo envolvia en un proxy CGLIB, y ahi
 * {@code GenericFilterBean.init()} es final: el proxy no puede reemplazarlo, el {@code logger}
 * del padre se quedaba a null y Tomcat no arrancaba ({@code NullPointerException: Cannot
 * invoke "Log.isDebugEnabled()"}). Sin clase base de Spring no hay nada que proxiar mal. Se
 * registra en {@link CacheControlPublicoFilterConfig}.
 */
public class CacheControlPublicoFilter implements Filter {

    /**
     * Cuanto vive cada familia de datos, en segundos. El orden importa: gana la primera
     * entrada cuyo prefijo encaje, asi que lo especifico va antes que lo general.
     */
    private static final Map<String, Integer> SEGUNDOS_POR_RUTA = new LinkedHashMap<>();

    static {
        // El directo cambia cada tick del scheduler; 10s evita repetir la peticion dentro de
        // una misma navegacion sin que el marcador se quede atras.
        SEGUNDOS_POR_RUTA.put("/api/v1/partidos/en-vivo", 10);
        SEGUNDOS_POR_RUTA.put("/api/v1/partidos/hoy", 30);
        // Un partido concreto o un listado por liga: la mayoria son historicos e inmutables.
        SEGUNDOS_POR_RUTA.put("/api/v1/partidos", 60);
        SEGUNDOS_POR_RUTA.put("/api/v1/clasificaciones", 120);
        // Fichas y catalogos: cambian cuando corre un relleno, no dentro de una visita.
        SEGUNDOS_POR_RUTA.put("/api/v1/ligas", 300);
        SEGUNDOS_POR_RUTA.put("/api/v1/equipos", 300);
        SEGUNDOS_POR_RUTA.put("/api/v1/jugadores", 300);
        SEGUNDOS_POR_RUTA.put("/api/v1/entrenadores", 300);
        SEGUNDOS_POR_RUTA.put("/api/v1/arbitros", 300);
    }

    /**
     * Rutas que caen bajo un prefijo publico pero NO son publicas.
     *
     * <p>{@link SpringSecurityConfig} recorta a mano algunos GET dentro de un prefijo que por
     * lo demas es {@code permitAll}. El caso real: {@code /api/v1/partidos/estado-ingesta}
     * (cuota restante, cola de partidos) pide rol de admin, pero empieza por
     * {@code /api/v1/partidos} y este filtro lo marcaba como {@code public, max-age=60}.</p>
     *
     * <p><b>Si se anade un recorte de esos en la config de seguridad, hay que anadirlo aqui.</b>
     * Estan separados porque son dos preguntas distintas (quien puede entrar / que se puede
     * guardar en una cache), pero la lista de excepciones tiene que ser la misma.</p>
     */
    private static final Set<String> NUNCA_PUBLICAS = Set.of(
            "/api/v1/partidos/estado-ingesta"
    );


    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain cadena)
            throws ServletException, IOException {
        if (!(req instanceof HttpServletRequest peticion)
                || !(res instanceof HttpServletResponse respuesta)) {
            cadena.doFilter(req, res);
            return;
        }
        Integer segundos = segundosPara(peticion);
        if (segundos != null) {
            respuesta.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=" + segundos);
            // Pragma y Expires se ponen vacias a proposito, por dos motivos. Uno: una
            // "Pragma: no-cache" antigua le gana al Cache-Control en algunos intermediarios.
            // Dos: CacheControlHeadersWriter de Spring Security no escribe NADA si ya existe
            // cualquiera de las tres, asi que dejarlas puestas es justo lo que hace que su
            // no-store no pise este max-age.
            respuesta.setHeader(HttpHeaders.PRAGMA, "");
            respuesta.setHeader(HttpHeaders.EXPIRES, "");
        }
        // Si no es una ruta publica no se toca nada: Spring Security pone su
        // no-cache/no-store de siempre, que sigue siendo el valor por defecto para todo lo
        // que no este en la lista de arriba, incluidos los 401 y 403 donde este filtro ni
        // llega a ejecutarse.
        cadena.doFilter(peticion, respuesta);
    }

    /** Segundos de cache para esta peticion, o null si no se puede cachear. */
    private Integer segundosPara(HttpServletRequest peticion) {
        if (!HttpMethod.GET.matches(peticion.getMethod())) {
            return null;
        }
        // Una peticion con credenciales podria traer una respuesta distinta el dia que algun
        // endpoint mire el usuario. Hoy ninguno lo hace, pero cachear como "public" algo que
        // llego con Authorization es la clase de error que no se nota hasta que se nota.
        if (peticion.getHeader(HttpHeaders.AUTHORIZATION) != null) {
            return null;
        }
        String ruta = peticion.getRequestURI();
        if (NUNCA_PUBLICAS.contains(ruta)) {
            return null;
        }
        for (Map.Entry<String, Integer> entrada : SEGUNDOS_POR_RUTA.entrySet()) {
            if (ruta.equals(entrada.getKey()) || ruta.startsWith(entrada.getKey() + "/")) {
                return entrada.getValue();
            }
        }
        return null;
    }
}
