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

class ReporteDTOValidationTest {

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
    void deberiaFallar_CuandoFaltanIdDenunciadoYMotivo() {
        ReporteDTO dto = new ReporteDTO();

        Set<ConstraintViolation<ReporteDTO>> violaciones = validator.validate(dto);

        assertFalse(violaciones.isEmpty());
    }

    @Test
    void deberiaPasar_CuandoIdDenunciadoYMotivoEstanPresentes() {
        ReporteDTO dto = new ReporteDTO();
        dto.setIdDenunciado(5);
        dto.setMotivo("Insultos en los comentarios del partido");

        Set<ConstraintViolation<ReporteDTO>> violaciones = validator.validate(dto);

        assertTrue(violaciones.isEmpty());
    }
}
