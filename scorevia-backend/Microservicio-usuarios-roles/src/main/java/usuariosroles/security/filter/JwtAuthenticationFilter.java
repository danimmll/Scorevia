package usuariosroles.security.filter;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import usuariosroles.dto.IncidenciaDTO;
import usuariosroles.entities.Usuario;
import usuariosroles.repositories.UsuarioRepository;
import usuariosroles.config.IncidenciasClient;
import usuariosroles.services.jwt.JwtTokenService;

public class JwtAuthenticationFilter extends UsernamePasswordAuthenticationFilter {
    private final AuthenticationManager manager;
    private final IncidenciasClient incidenciasClient;
    private final UsuarioRepository usuarioRepository;
    private final ObjectMapper objectMapper;
    private final boolean secureCookie;
    /** Ver app.cookie.same-site: "Lax" salvo despliegue partido (web y API en dominios distintos). */
    private final String sameSiteCookie;
    private final JwtTokenService jwtTokenService; // Añadimos el nuevo servicio
    private final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String KEY_CORREO = "correoIntento";
    private static final int MAX_INTENTOS_LOGIN = 5;
    private static final int MINUTOS_BLOQUEO_LOGIN = 15;
    private static final String CONTENT_TYPE = "application/json";

    public JwtAuthenticationFilter(AuthenticationManager manager,
                                   IncidenciasClient incidenciasClient,
                                   UsuarioRepository usuarioRepository,
                                   ObjectMapper objectMapper,
                                   boolean secureCookie,
                                   String sameSiteCookie,
                                   JwtTokenService jwtTokenService) {
        this.manager = manager;
        this.incidenciasClient = incidenciasClient;
        this.usuarioRepository = usuarioRepository;
        this.objectMapper = objectMapper;
        this.secureCookie = secureCookie;
        this.sameSiteCookie = sameSiteCookie;
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    @NonNull
    public Authentication attemptAuthentication(HttpServletRequest request, @NonNull HttpServletResponse response)
            throws AuthenticationException {
        try {
            Usuario usuario = new ObjectMapper().readValue(request.getInputStream(), Usuario.class);
            String correo = usuario.getCorreoUsuario();
            request.setAttribute(KEY_CORREO, correo);
            String contrasenha = usuario.getContrasenhaUsuario();
            UsernamePasswordAuthenticationToken token =
                    new UsernamePasswordAuthenticationToken(correo, contrasenha);
            return manager.authenticate(token);
        } catch (IOException e) {
            throw new BadCredentialsException("Correo o contraseña inválidos", e);
        }
    }

    @Override
    protected void successfulAuthentication(@NonNull HttpServletRequest request,
                                            @NonNull HttpServletResponse response,
                                            @NonNull FilterChain chain,
                                            Authentication authResult)
            throws IOException {

        Object principal = authResult.getPrincipal();
        if (!(principal instanceof User user)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Principal inválido");
            return;
        }

        String correo = user.getUsername();
        Integer idUsuario;
        Optional<Usuario> usuarioAux = usuarioRepository.findByCorreoUsuario(correo);
        Usuario usuarioEntidad = null;

        if (usuarioAux.isPresent()) {
            usuarioEntidad = usuarioAux.get();
            idUsuario = usuarioEntidad.getIdUsuario();
            if ((usuarioEntidad.getIntentosLogin() != null && usuarioEntidad.getIntentosLogin() > 0)
                    || usuarioEntidad.getBloqueadoLoginHasta() != null) {
                usuarioEntidad.setIntentosLogin(0);
                usuarioEntidad.setBloqueadoLoginHasta(null);
                usuarioRepository.save(usuarioEntidad);
            }
        } else {
            idUsuario = 0;
        }

        Collection<? extends GrantedAuthority> roles = authResult.getAuthorities();
        Collection<String> roleNames = roles.stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        // 1. Generamos ambos tokens con tu nuevo servicio
        String accessToken = jwtTokenService.generarAccessToken(idUsuario, correo, roles);
        String refreshToken = jwtTokenService.generarRefreshToken(idUsuario, correo);

        // 2. Metemos el Refresh Token en una Cookie HttpOnly de 7 días (604800 segundos)
        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(7L * 24 * 3600)
                .sameSite(sameSiteCookie)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        Map<String, Object> body = new HashMap<>();
        body.put("message", "¡Has iniciado sesión con éxito!");
        body.put("accessToken", accessToken); // Mandamos el Access Token en el cuerpo de la respuesta

        if (usuarioEntidad != null) {
            Map<String, Object> usuarioSafe = new HashMap<>();
            usuarioSafe.put("idUsuario", usuarioEntidad.getIdUsuario());
            usuarioSafe.put("correoUsuario", usuarioEntidad.getCorreoUsuario());
            usuarioSafe.put("nombreUsuario", usuarioEntidad.getNombreUsuario());
            usuarioSafe.put("apellidoUsuario", usuarioEntidad.getApellidoUsuario());
            usuarioSafe.put("activo", usuarioEntidad.getActivo());
            usuarioSafe.put("roles", roleNames);
            body.put("usuario", usuarioSafe);
        }

        response.getWriter().write(objectMapper.writeValueAsString(body));
        response.setContentType(CONTENT_TYPE);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request,
                                              @NonNull HttpServletResponse response,
                                              @NonNull AuthenticationException failed)
            throws IOException {
        String correo = (String) request.getAttribute(KEY_CORREO);
        String mensajeError = "Cuenta no existente o datos erróneos";
        if (failed instanceof DisabledException) {
            mensajeError = failed.getMessage();
        } else {
            registrarIntentoFallidoLogin(correo);
        }

        IncidenciaDTO incidenciaDTO = new IncidenciaDTO();
        incidenciaDTO.setEndpoint("/api/v1/auth/login");
        incidenciaDTO.setTipo("Failed login");
        incidenciaDTO.setClase(this.getClass().getSimpleName());
        incidenciaDTO.setMetodo("unsuccessfulAuthentication");
        incidenciaDTO.setTraza(failed.getMessage());
        incidenciaDTO.setFecha(LocalDateTime.now(ZoneId.systemDefault()));
        incidenciaDTO.setUsuarioContexto(correo);

        // Fuera del hilo de la petición: ver la misma justificación en JwtValidationFilter.
        Thread.startVirtualThread(() -> {
            try {
                incidenciasClient.reportarIncidencia(incidenciaDTO);
            } catch (Exception e) {
                log.error("Fallo al comunicar con incidencias: {}", e.getMessage());
            }
        });

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, mensajeError);
        problem.setType(URI.create("unsuccessful-login"));
        problem.setTitle("Login Fallido");
        problem.setProperty("timestamp", LocalDateTime.now(ZoneId.systemDefault()));

        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType(CONTENT_TYPE);
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }

    private void registrarIntentoFallidoLogin(String correo) {
        if (correo == null) return;
        usuarioRepository.findByCorreoUsuario(correo).ifPresent(usuario -> {
            int intentos = (usuario.getIntentosLogin() == null ? 0 : usuario.getIntentosLogin()) + 1;
            usuario.setIntentosLogin(intentos);
            if (intentos >= MAX_INTENTOS_LOGIN) {
                usuario.setBloqueadoLoginHasta(LocalDateTime.now(ZoneId.systemDefault()).plusMinutes(MINUTOS_BLOQUEO_LOGIN));
                usuario.setIntentosLogin(0);
            }
            usuarioRepository.save(usuario);
        });
    }
}