package usuariosroles.config;

import org.springframework.validation.BindingResult;
import usuariosroles.exception.BadRequestException;

/**
 * Utility class for performing common validation tasks.
 *
 * <p>Provides methods for validating IDs and formatting validation error messages.</p>
 *
 * <p>This class is non-instantiable and only contains static methods.</p>
 *
 * @author Daniel
 * @version 1.0
 */
public class ValidationUtils {

    /** Private constructor to prevent instantiation. */
    private ValidationUtils() {
        //CONSTRUCTOR VACIO SONAR
    }

    /**
     * Validates that a given string represents a positive integer ID.
     *
     * @param idString String containing the ID to validate.
     * @return Validated integer ID.
     * @throws BadRequestException if the ID is not a valid positive integer.
     */
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
     * Generates a formatted validation error message from a {@link BindingResult}.
     *
     * @param result BindingResult containing validation errors.
     * @return String with validation errors in snake_case format.
     */
    public static String validation(BindingResult result) {
        StringBuilder sb = new StringBuilder("Validation errors: ");
        result.getFieldErrors().forEach(error ->
            sb.append(toSnakeCase(error.getField()))
              .append(": ")
              .append(error.getDefaultMessage())
              .append("; ")
        );
        return sb.toString();
    }

    /**
     * Converts a camelCase field name to snake_case.
     *
     * @param field Field name in camelCase.
     * @return Field name in snake_case.
     */
    private static String toSnakeCase(String field) {
        return field.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
