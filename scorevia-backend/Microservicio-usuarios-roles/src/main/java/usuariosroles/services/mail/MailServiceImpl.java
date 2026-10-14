package usuariosroles.services.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailServiceImpl implements MailService {

    private final JavaMailSender mailSender;

    @Override
    public void enviarCodigoVerificacion(String correoDestino, String nombreUsuario, String codigo) {
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, "UTF-8");

            helper.setTo(correoDestino);
            helper.setSubject("Verifica tu cuenta de Scorevia");
            helper.setText("""
                    Hola %s,

                    Gracias por registrarte en Scorevia. Tu código de verificación es:

                    %s

                    Este código caduca en 15 minutos. Si no has sido tú, ignora este correo.
                    """.formatted(nombreUsuario, codigo));

            mailSender.send(mensaje);
        } catch (MessagingException e) {
            // Síncrono a propósito: si el correo no se puede mandar, se aborta el registro
            // entero (la transacción hace rollback) en vez de dejar un usuario fantasma que
            // nunca podrá verificarse. Con @Async esta excepción nunca llegaba al hilo
            // llamador (Spring solo la loguea), así que el registro se confirmaba igual
            // aunque el envío fallara. Más adelante se puede añadir un endpoint de
            // "reenviar código" para no depender de que el envío salga bien a la primera.
            log.error("No se pudo enviar el correo de verificación a {}: {}", correoDestino, e.getMessage());
            throw new IllegalStateException("No se pudo enviar el correo de verificación", e);
        }
    }

    @Override
    public void enviarCodigoResetPassword(String correoDestino, String nombreUsuario, String codigo) {
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, "UTF-8");

            helper.setTo(correoDestino);
            helper.setSubject("Restablece tu contraseña de Scorevia");
            helper.setText("""
                    Hola %s,

                    Hemos recibido una solicitud para restablecer tu contraseña. Tu código es:

                    %s

                    Este código caduca en 15 minutos. Si no has sido tú, ignora este correo:
                    tu contraseña actual seguirá funcionando.
                    """.formatted(nombreUsuario, codigo));

            mailSender.send(mensaje);
        } catch (MessagingException e) {
            // Síncrono a propósito, mismo criterio que enviarCodigoVerificacion: si el correo no
            // sale, la solicitud de reset debe fallar visiblemente (el cliente puede reintentar),
            // no quedar "aceptada" en silencio sin que el usuario reciba nunca el código.
            log.error("No se pudo enviar el correo de reset de contraseña a {}: {}", correoDestino, e.getMessage());
            throw new IllegalStateException("No se pudo enviar el correo de restablecimiento de contraseña", e);
        }
    }
}