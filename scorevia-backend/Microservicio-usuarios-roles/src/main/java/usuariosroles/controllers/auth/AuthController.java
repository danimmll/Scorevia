package usuariosroles.controllers.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import usuariosroles.dto.GoogleTokenDTO;
import usuariosroles.dto.ResetPasswordDTO;
import usuariosroles.dto.SolicitarResetPasswordDTO;
import usuariosroles.dto.UsuarioDTO;
import usuariosroles.dto.VerificacionEmailDTO;

/**
 * Interface for the authentication controller.
 * Defines endpoints related to user registration and authentication.
 *
 * <p>Uses Swagger annotations to document API operations and responses.</p>
 *
 * @author Daniel
 * @version 2.0
 */
@Tag(name = "Autenticación", description = "API para el registro y autenticación de usuarios")
public interface AuthController {

    @Operation(
            summary = "Registrar o reactivar usuario",
            description = "Registra un nuevo usuario o reactiva uno existente que esté desactivado. Envía un código de verificación por email; el usuario no puede iniciar sesión hasta verificarlo."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Usuario registrado o reactivado correctamente, código enviado por email"),
            @ApiResponse(responseCode = "400", description = "Error de validación en los datos proporcionados"),
            @ApiResponse(responseCode = "409", description = "Conflicto: el usuario ya existe y está activo")
    })
    ResponseEntity<Object> crearUsuario(@Valid @RequestBody UsuarioDTO usuarioDTO, BindingResult result);

    @Operation(
            summary = "Verificar código de email",
            description = "Comprueba el código de 6 dígitos enviado al correo durante el registro y marca la cuenta como verificada."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Correo verificado correctamente. Devuelve Access Token y Refresh Token."),
            @ApiResponse(responseCode = "400", description = "Código incorrecto o caducado"),
            @ApiResponse(responseCode = "404", description = "No existe ningún usuario con ese correo")
    })
    ResponseEntity<Object> verificarEmail(@Valid @RequestBody VerificacionEmailDTO dto);

    @Operation(
            summary = "Solicitar reset de contraseña",
            description = "Envía un código de 6 dígitos al correo indicado para restablecer la contraseña. Responde igual exista o no ese correo (anti-enumeración)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Si el correo existe, se ha enviado un código. La respuesta es la misma en ambos casos.")
    })
    ResponseEntity<Object> solicitarResetPassword(@Valid @RequestBody SolicitarResetPasswordDTO dto);

    @Operation(
            summary = "Restablecer contraseña",
            description = "Comprueba el código de 6 dígitos y aplica la nueva contraseña. Revoca todas las sesiones activas del usuario."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Contraseña restablecida correctamente"),
            @ApiResponse(responseCode = "400", description = "Código incorrecto o caducado"),
            @ApiResponse(responseCode = "403", description = "Demasiados intentos fallidos, cuenta bloqueada temporalmente"),
            @ApiResponse(responseCode = "404", description = "No existe ningún usuario con ese correo")
    })
    ResponseEntity<Object> resetPassword(@Valid @RequestBody ResetPasswordDTO dto);

    @Operation(
            summary = "Login con Google",
            description = "Verifica el token de Google, registra al usuario si es nuevo y devuelve la sesión."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login exitoso. Devuelve Access Token y Refresh Token en Cookie.")
    })
    ResponseEntity<Object> loginConGoogle(@Valid @RequestBody GoogleTokenDTO dto);

    @Operation(
            summary = "Refrescar Token",
            description = "Recibe el Refresh Token desde la cookie HttpOnly y devuelve un nuevo Access Token de corta duración."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token refrescado con éxito"),
            @ApiResponse(responseCode = "403", description = "Refresh token inválido, caducado o ausente")
    })
    ResponseEntity<Object> refrescarToken(@CookieValue(name = "refreshToken", required = false) String refreshToken);

    @Operation(summary = "Obtener usuario actual", description = "Devuelve los datos del usuario logueado usando el Access Token.")
    ResponseEntity<Object> getMe();

    @Operation(summary = "Cerrar sesión", description = "Revoca el Refresh Token server-side y destruye la cookie HttpOnly.")
    ResponseEntity<Object> logout(@CookieValue(name = "refreshToken", required = false) String refreshToken);
}