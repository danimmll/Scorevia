package estadisticas.config;

import estadisticas.exception.BadRequestException;

/**
 * Utilidad para validar ids recibidos como String en los path variables.
 * Misma convencion que en Microservicio-usuarios-roles.
 *
 * @author Daniel
 * @version 1.0
 */
public class ValidationUtils {

    private ValidationUtils() {
        //CONSTRUCTOR VACIO SONAR
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

    /**
     * Valida una fecha en formato ISO (YYYY-MM-DD).
     *
     * <p>Sin esto, un parámetro mal escrito sale como una DateTimeParseException sin
     * manejar, que el GlobalExceptionHandler convierte en un 500 -- cuando lo que ha
     * pasado es que el cliente ha mandado algo mal, o sea un 400.</p>
     */
    public static java.time.LocalDate validarFecha(String fechaString) {
        try {
            return java.time.LocalDate.parse(fechaString);
        } catch (java.time.format.DateTimeParseException e) {
            throw new BadRequestException("Fecha no válida, debe tener el formato YYYY-MM-DD");
        }
    }
}
