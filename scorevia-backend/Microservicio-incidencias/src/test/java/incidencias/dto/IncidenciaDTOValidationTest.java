package incidencias.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidenciaDTOValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void deberiaFallar_CuandoFaltanCamposObligatorios() {
        IncidenciaDTO dto = new IncidenciaDTO();

        Set<ConstraintViolation<IncidenciaDTO>> violaciones = validator.validate(dto);

        assertFalse(violaciones.isEmpty());
    }

    @Test
    void deberiaPasar_CuandoTodosLosCamposObligatoriosEstanPresentes() {
        IncidenciaDTO dto = IncidenciaDTO.builder()
                .endpoint("/usuarios/5")
                .tipo("NullPointerException")
                .clase("UsuarioServiceImpl")
                .metodo("getUsuarioById")
                .traza("java.lang.NullPointerException: ...")
                .build();

        Set<ConstraintViolation<IncidenciaDTO>> violaciones = validator.validate(dto);

        assertTrue(violaciones.isEmpty());
    }
}
