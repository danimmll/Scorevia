package usuariosroles.services.mail;

/**
 * Envio de correos transaccionales (codigo de verificacion de email en el registro, y codigo
 * de reset de contraseña). Separado del resto de UsuarioService para no mezclar la logica de
 * negocio de usuarios con el detalle de como se manda el correo (JavaMailSender, plantilla, etc.).
 *
 * @author Daniel
 * @version 1.0
 */
public interface MailService {

    /**
     * Envia el codigo de verificacion de 6 digitos al correo indicado.
     *
     * @param correoDestino correo del usuario recien registrado
     * @param nombreUsuario nombre para personalizar el saludo
     * @param codigo        codigo de 6 digitos generado para este registro
     */
    void enviarCodigoVerificacion(String correoDestino, String nombreUsuario, String codigo);

    /**
     * Envia el codigo de 6 digitos para restablecer la contraseña.
     *
     * @param correoDestino correo del usuario que solicito el reset
     * @param nombreUsuario nombre para personalizar el saludo
     * @param codigo        codigo de 6 digitos generado para este reset
     */
    void enviarCodigoResetPassword(String correoDestino, String nombreUsuario, String codigo);
}