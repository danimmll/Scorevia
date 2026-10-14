package usuariosroles.controllers.auth;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import usuariosroles.dto.GoogleTokenDTO;
import usuariosroles.dto.ResetPasswordDTO;
import usuariosroles.dto.SolicitarResetPasswordDTO;
import usuariosroles.dto.UsuarioDTO;
import usuariosroles.dto.VerificacionEmailDTO;
import usuariosroles.entities.Usuario;
import usuariosroles.exception.BadRequestException;
import usuariosroles.exception.DuplicateException;
import usuariosroles.exception.ForbiddenException;
import usuariosroles.exception.NotFoundException;
import usuariosroles.repositories.UsuarioRepository;
import usuariosroles.services.jwt.JwtTokenService;
import usuariosroles.services.usuario.UsuarioServiceImpl;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthControllerImpl implements AuthController {

    private final UsuarioServiceImpl service;
    private final UsuarioRepository usuarioRepository;
    private final JwtTokenService jwtTokenService;

    private static final String STRING_MESSAGE = "message";
    private static final String STRING_USUARIO = "usuario";
    private static final String KEY_ID_USUARIO = "idUsuario";
    private static final String KEY_NICKNAME = "nickname";
    private static final String KEY_CORREO_USUARIO = "correoUsuario";
    private static final String KEY_NOMBRE_USUARIO = "nombreUsuario";
    private static final String KEY_APELLIDO_USUARIO = "apellidoUsuario";
    private static final String KEY_ACTIVO = "activo";
    private static final String KEY_ROLES = "roles";
    private static final String KEY_ACCESS_TOKEN = "accessToken";
    private static final String STRING_USUARIO_NO_ENCONTRADO = "Usuario no encontrado";
    private static final String TOKEN_NAME = "refreshToken";

    // Fail-closed: ver el mismo fallback en application.yml (app.cookie.secure).
    @Value("${app.cookie.secure:true}")
    private boolean secureCookie;

    /** Ver app.cookie.same-site en application.yml: "Lax" salvo despliegue partido. */
    @Value("${app.cookie.same-site:Lax}")
    private String sameSiteCookie;

    @PostMapping("/register")
    public ResponseEntity<Object> crearUsuario(
            @Valid @RequestBody UsuarioDTO usuarioDTO,
            BindingResult result
    ) {

        if (result.hasFieldErrors()) {
            throw new BadRequestException(validation(result));
        }

        if (service.existsUsuarioDesactivado(usuarioDTO.getCorreoUsuario())) {
            UsuarioDTO usuarioAux = service.activarUsuarioDTO(usuarioDTO);

            Map<String, Object> respuesta = new HashMap<>();
            respuesta.put(STRING_USUARIO, usuarioAux);
            respuesta.put(
                    STRING_MESSAGE,
                    "Tu cuenta estaba desactivada y la hemos recuperado. Por favor, verifica tu correo."
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
        }

        if (service.existsUsuario(usuarioDTO.getCorreoUsuario())) {
            throw new DuplicateException("El correo ya está registrado.", "correo-en-uso");
        }

        if (service.existsNickname(usuarioDTO.getNickname())) {
            throw new DuplicateException("Ese nickname ya está en uso.", "nickname-en-uso");
        }

        UsuarioDTO usuarioAux = service.crearUsuario(usuarioDTO);

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put(STRING_USUARIO, usuarioAux);
        respuesta.put(
                STRING_MESSAGE,
                "Registro completado. Por favor, verifica tu correo con el código de 6 dígitos."
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @PostMapping("/verificar-email")
    public ResponseEntity<Object> verificarEmail(@Valid @RequestBody VerificacionEmailDTO dto) {

        service.verificarEmail(dto.getCorreoUsuario(), dto.getCodigo());

        Usuario usuario = usuarioRepository
                .findByCorreoUsuario(dto.getCorreoUsuario())
                .orElseThrow(() -> new NotFoundException(STRING_USUARIO_NO_ENCONTRADO));

        return crearRespuestaAutenticacion(
                usuario,
                "¡Correo verificado con éxito! Entrando al sistema..."
        );
    }

    @PostMapping("/solicitar-reset-password")
    public ResponseEntity<Object> solicitarResetPassword(@Valid @RequestBody SolicitarResetPasswordDTO dto) {

        service.solicitarResetPassword(dto.getCorreoUsuario());

        Map<String, Object> respuesta = new HashMap<>();
        // Mensaje idéntico exista o no la cuenta: el service ya no distingue los dos casos
        // desde fuera (ver solicitarResetPassword), así que el controller tampoco puede.
        respuesta.put(STRING_MESSAGE, "Si ese correo está registrado, hemos enviado un código para restablecer la contraseña.");

        return ResponseEntity.ok(respuesta);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Object> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {

        service.resetPassword(dto.getCorreoUsuario(), dto.getCodigo(), dto.getNuevaContrasenha());

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put(STRING_MESSAGE, "Contraseña restablecida con éxito. Ya puedes iniciar sesión con tu nueva contraseña.");

        return ResponseEntity.ok(respuesta);
    }

    @PostMapping("/google")
    public ResponseEntity<Object> loginConGoogle(
            @Valid @RequestBody GoogleTokenDTO dto
    ) {

        Usuario usuario = service.procesarLoginGoogle(dto.token());

        return crearRespuestaAutenticacion(
                usuario,
                "¡Has iniciado sesión con Google con éxito!"
        );
    }

    @PostMapping("/refresh")
    public ResponseEntity<Object> refrescarToken(
            @CookieValue(name = TOKEN_NAME, required = false) String refreshToken
    ) {

        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ForbiddenException(
                    "No se proporcionó refresh token de sesión."
            );
        }

        Claims claims;
        try {
            claims = jwtTokenService.validarTokenExtraerClaims(
                    refreshToken,
                    "REFRESH"
            );
        } catch (Exception e) {
            throw new ForbiddenException(
                    "Refresh token inválido o expirado. Inicie sesión nuevamente."
            );
        }

        // Rotación: este refresh token queda consumido aquí (o lanza ForbiddenException si ya
        // se había usado antes, revocando de paso todas las sesiones del usuario por precaución).
        jwtTokenService.verificarYRotarRefreshToken(claims);

        String correo = claims.getSubject();

        Usuario usuario = usuarioRepository
                .findByCorreoUsuario(correo)
                .orElseThrow(() -> new NotFoundException(STRING_USUARIO_NO_ENCONTRADO));

        if (Boolean.FALSE.equals(usuario.getActivo())) {
            throw new ForbiddenException("El usuario está inactivo.", "usuario-inactivo");
        }

        List<GrantedAuthority> authorities = extraerAuthorities(usuario);

        String nuevoAccessToken = jwtTokenService.generarAccessToken(
                usuario.getIdUsuario(),
                correo,
                authorities
        );

        // El refresh token también se rota: el cliente recibe uno nuevo y el consumido ya
        // quedó marcado revocado arriba (ver verificarYRotarRefreshToken).
        String nuevoRefreshToken = jwtTokenService.generarRefreshToken(
                usuario.getIdUsuario(),
                correo
        );
        ResponseCookie cookie = generarCookieRefresh(nuevoRefreshToken, 7L * 24 * 3600);

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put(KEY_ACCESS_TOKEN, nuevoAccessToken);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(respuesta);
    }

    @GetMapping("/me")
    public ResponseEntity<Object> getMe() {

        Authentication auth =
                SecurityContextHolder.getContext().getAuthentication();

        if (auth == null
                || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) {

            throw new BadRequestException("No estás autenticado", "no-autenticado");
        }

        String correo = (String) auth.getPrincipal();

        Usuario usuario = usuarioRepository
                .findByCorreoUsuario(correo)
                .orElseThrow(() -> new NotFoundException(STRING_USUARIO_NO_ENCONTRADO));

        List<String> roleNames = auth.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put(
                STRING_USUARIO,
                construirUsuarioSafe(usuario, roleNames)
        );

        return ResponseEntity.ok(respuesta);
    }

    @PostMapping("/logout")
    public ResponseEntity<Object> logout(
            @CookieValue(name = TOKEN_NAME, required = false) String refreshToken
    ) {

        // Revocación server-side además de borrar la cookie: sin esto, el refresh token seguía
        // siendo válido hasta sus 7 días de vida aunque el usuario "cerrara sesión" (la cookie
        // solo protege al propio navegador, no invalida el token si alguien más lo tuviera).
        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                Claims claims = jwtTokenService.validarTokenExtraerClaims(refreshToken, "REFRESH");
                jwtTokenService.revocarRefreshTokenPorJti(claims.getId());
            } catch (Exception e) {
                // Token ya inválido/expirado/malformado: no hay nada que revocar server-side,
                // pero el logout sigue su curso normal (borrar la cookie igualmente).
                log.debug("No se pudo revocar el refresh token en logout (probablemente ya inválido): {}", e.getMessage());
            }
        }

        ResponseCookie cookie = generarCookieRefresh("", 0);

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put(STRING_MESSAGE, "Sesión cerrada con éxito");

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(respuesta);
    }

    private ResponseEntity<Object> crearRespuestaAutenticacion(
            Usuario usuario,
            String mensaje
    ) {

        List<GrantedAuthority> authorities = extraerAuthorities(usuario);

        String accessToken = jwtTokenService.generarAccessToken(
                usuario.getIdUsuario(),
                usuario.getCorreoUsuario(),
                authorities
        );

        String refreshToken = jwtTokenService.generarRefreshToken(
                usuario.getIdUsuario(),
                usuario.getCorreoUsuario()
        );

        ResponseCookie cookie =
                generarCookieRefresh(refreshToken, 7L * 24 * 3600);

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put(STRING_MESSAGE, mensaje);
        respuesta.put(KEY_ACCESS_TOKEN, accessToken);
        respuesta.put(
                STRING_USUARIO,
                construirUsuarioSafe(
                        usuario,
                        extraerRolesString(authorities)
                )
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(respuesta);
    }

    private List<GrantedAuthority> extraerAuthorities(Usuario usuario) {

        return usuario.getRoles()
                .stream()
                .map(rol -> "ROLE_" + rol.getNombreRol())
                .filter(rol -> !rol.startsWith("FACTOR_"))
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    private List<String> extraerRolesString(
            List<GrantedAuthority> authorities
    ) {

        return authorities
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    private ResponseCookie generarCookieRefresh(
            String token,
            long maxAge
    ) {

        return ResponseCookie.from(TOKEN_NAME, token)
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(maxAge)
                .sameSite(sameSiteCookie)
                .build();
    }

    private Map<String, Object> construirUsuarioSafe(
            Usuario usuario,
            List<String> roles
    ) {

        Map<String, Object> usuarioSafe = new HashMap<>();

        usuarioSafe.put(KEY_ID_USUARIO, usuario.getIdUsuario());
        usuarioSafe.put(KEY_NICKNAME, usuario.getNickname());
        usuarioSafe.put(KEY_CORREO_USUARIO, usuario.getCorreoUsuario());
        usuarioSafe.put(KEY_NOMBRE_USUARIO, usuario.getNombreUsuario());
        usuarioSafe.put(KEY_APELLIDO_USUARIO, usuario.getApellidoUsuario());
        usuarioSafe.put(KEY_ACTIVO, usuario.getActivo());
        usuarioSafe.put(KEY_ROLES, roles);

        return usuarioSafe;
    }

    private String validation(BindingResult result) {

        StringBuilder sb =
                new StringBuilder("Errores de validación: ");

        result.getFieldErrors().forEach(error ->
                sb.append(toSnakeCase(error.getField()))
                        .append(": ")
                        .append(error.getDefaultMessage())
                        .append("; ")
        );

        return sb.toString();
    }

    private String toSnakeCase(String field) {

        return field
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .toLowerCase();
    }
}
