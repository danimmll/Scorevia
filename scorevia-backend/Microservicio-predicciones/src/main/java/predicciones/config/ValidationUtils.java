package predicciones.config;

import predicciones.exception.BadRequestException;

/**
 * Utilidad para validar valores numéricos recibidos como String en la request.
 * Misma convención que en Microservicio-estadisticas / Microservicio-usuarios-roles.
 *
 * @author Daniel
 * @version 1.0
 */
public class ValidationUtils {

    private ValidationUtils() {
    }

    public static Integer validarId(String idString) {
        int id;
        try {
            id = Integer.parseInt(idString);
        } catch (NumberFormatException e) {
            throw new BadRequestException("ID no válido, debe ser un número entero", "id-invalido");
        }
        if (id <= 0) {
            throw new BadRequestException("ID no válido, debe ser un número entero positivo", "id-invalido");
        }
        return id;
    }

    public static int validarEntero(String valorString, String nombreCampo) {
        int valor;
        try {
            valor = Integer.parseInt(valorString);
        } catch (NumberFormatException e) {
            throw new BadRequestException("El campo '" + nombreCampo + "' debe ser un número entero");
        }
        if (valor < 0) {
            throw new BadRequestException("El campo '" + nombreCampo + "' no puede ser negativo");
        }
        return valor;
    }
}