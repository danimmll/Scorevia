package usuariosroles.services.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private MailServiceImpl service;

    @Test
    void enviarCodigoVerificacion_Exito() {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        service.enviarCodigoVerificacion("test@test.com", "UserTest", "123456");

        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    void enviarCodigoVerificacion_FallaYThrowIllegalState() {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Simulamos que al intentar enviar lanza el error interno de Messaging
        doAnswer(invocation -> {
            throw new MessagingException("Simulated error");
        }).when(mailSender).send(any(MimeMessage.class));

        assertThrows(IllegalStateException.class, () ->
                service.enviarCodigoVerificacion("test@test.com", "User", "123")
        );
    }

    @Test
    void enviarCodigoResetPassword_Exito() {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        service.enviarCodigoResetPassword("test@test.com", "UserTest", "123456");

        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    void enviarCodigoResetPassword_FallaYThrowIllegalState() {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        doAnswer(invocation -> {
            throw new MessagingException("Simulated error");
        }).when(mailSender).send(any(MimeMessage.class));

        assertThrows(IllegalStateException.class, () ->
                service.enviarCodigoResetPassword("test@test.com", "User", "123")
        );
    }
}