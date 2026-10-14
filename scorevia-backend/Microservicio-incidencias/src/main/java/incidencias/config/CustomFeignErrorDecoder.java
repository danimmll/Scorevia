package incidencias.config;

import feign.Response;
import feign.codec.ErrorDecoder;
import incidencias.exception.BadRequestException;
import incidencias.exception.ConflictException;
import incidencias.exception.NotFoundException;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Mismo patrón que el decoder homónimo de predicciones. */
@Component
public class CustomFeignErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultErrorDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        String message = "Error interno en la comunicación de microservicios";

        try {
            if (response.body() != null) {
                InputStream inputStream = response.body().asInputStream();
                message = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
            // Si no podemos leer el body, devolvemos el genérico
        }

        return switch (response.status()) {
            case 400 -> new BadRequestException(message);
            case 404 -> new NotFoundException(message);
            case 409 -> new ConflictException(message);
            default -> defaultErrorDecoder.decode(methodKey, response);
        };
    }
}
