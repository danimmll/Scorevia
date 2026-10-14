package usuariosroles.controllers.rol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import usuariosroles.config.ValidationUtils;
import usuariosroles.dto.RolDTO;
import usuariosroles.exception.BadRequestException;
import usuariosroles.exception.ConflictException;
import usuariosroles.exception.DuplicateException;
import usuariosroles.exception.NoContentException;
import usuariosroles.exception.NotFoundException;
import usuariosroles.services.rol.RolServiceImpl;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RolControllerImplTest {

    @Mock
    private RolServiceImpl service;

    @Mock
    private BindingResult bindingResult;

    @InjectMocks
    private RolControllerImpl controller;

    private RolDTO rolDTO;

    @BeforeEach
    void setUp() {
        rolDTO = new RolDTO();
        rolDTO.setIdRol(1);
        rolDTO.setNombreRol("ADMIN");
    }

    // ==========================================
    // GET ROLES
    // ==========================================

    @Test
    void getRoles_SinRoles_LanzaNoContent() {
        when(service.getRolesDTO()).thenReturn(Collections.emptyList());
        assertThrows(NoContentException.class, () -> controller.getRoles());
    }

    @Test
    void getRoles_ConRoles_Exito() {
        when(service.getRolesDTO()).thenReturn(List.of(rolDTO));
        ResponseEntity<List<RolDTO>> response = controller.getRoles();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assert response.getBody() != null;
        assertFalse(response.getBody().isEmpty());
    }

    // ==========================================
    // GET ROL BY ID
    // ==========================================

    @Test
    void getRolById_NoExiste_LanzaNotFound() {
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            when(service.getRolByIdDTO(1)).thenReturn(null);
            assertThrows(NotFoundException.class, () -> controller.getRolById("1"));
        }
    }

    @Test
    void getRolById_Exito() {
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            when(service.getRolByIdDTO(1)).thenReturn(rolDTO);
            ResponseEntity<RolDTO> response = controller.getRolById("1");
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }

    // ==========================================
    // POST CREAR ROL
    // ==========================================

    @Test
    void crearRol_RolDesactivado_ActivaRol() {
        when(service.existsRolDesactivado("ADMIN")).thenReturn(true);
        when(service.activarRolDTO(rolDTO)).thenReturn(rolDTO);
        ResponseEntity<Object> response = controller.crearRol(rolDTO, bindingResult);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void crearRol_ConErroresValidacion_LanzaBadRequest() {
        when(bindingResult.hasFieldErrors()).thenReturn(true);
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validation(bindingResult)).thenReturn("Error");
            assertThrows(BadRequestException.class, () -> controller.crearRol(rolDTO, bindingResult));
        }
    }

    @Test
    void crearRol_RolYaExiste_LanzaDuplicate() {
        when(service.existsRolDesactivado("ADMIN")).thenReturn(false);
        when(bindingResult.hasFieldErrors()).thenReturn(false);
        when(service.existsRol("ADMIN")).thenReturn(true);
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validation(bindingResult)).thenReturn("Error");
            assertThrows(DuplicateException.class, () -> controller.crearRol(rolDTO, bindingResult));
        }
    }

    @Test
    void crearRol_Exito() {
        when(service.existsRolDesactivado("ADMIN")).thenReturn(false);
        when(bindingResult.hasFieldErrors()).thenReturn(false);
        when(service.existsRol("ADMIN")).thenReturn(false);
        when(service.crearRolDTO(rolDTO)).thenReturn(rolDTO);
        ResponseEntity<Object> response = controller.crearRol(rolDTO, bindingResult);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    // ==========================================
    // PUT ACTUALIZAR ROL
    // ==========================================

    @Test
    void putRol_NoExiste_LanzaNotFound() {
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            when(service.getRolByIdDTO(1)).thenReturn(null);
            assertThrows(NotFoundException.class, () -> controller.putRol(rolDTO, bindingResult, "1"));
        }
    }

    @Test
    void putRol_NombreNuevoYaExiste_LanzaDuplicateException() {
        RolDTO rolExistente = new RolDTO();
        rolExistente.setIdRol(1);
        rolExistente.setNombreRol("USER"); // Nombre en BBDD es USER

        rolDTO.setNombreRol("ADMIN"); // Intentas ponerle ADMIN

        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            when(service.getRolByIdDTO(1)).thenReturn(rolExistente);
            when(service.existsRol("ADMIN")).thenReturn(true); // Y ADMIN ya lo usa otro

            assertThrows(DuplicateException.class, () -> controller.putRol(rolDTO, bindingResult, "1"));
        }
    }

    @Test
    void putRol_ErroresValidacion_LanzaBadRequest() {
        RolDTO rolExistente = new RolDTO();
        rolExistente.setIdRol(1);
        rolExistente.setNombreRol("ADMIN");

        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            when(service.getRolByIdDTO(1)).thenReturn(rolExistente);
            // No se stubea existsRol: con la validación reordenada (hasFieldErrors antes que
            // el chequeo de duplicado), el 400 se lanza sin llegar a invocarlo -- stubearlo
            // dispara UnnecessaryStubbingException bajo Mockito strict-stubs.
            when(bindingResult.hasFieldErrors()).thenReturn(true);
            val.when(() -> ValidationUtils.validation(bindingResult)).thenReturn("Error");

            assertThrows(BadRequestException.class, () -> controller.putRol(rolDTO, bindingResult, "1"));
        }
    }

    @Test
    void putRol_ConflictoIds_LanzaConflict() {
        RolDTO rolExistente = new RolDTO();
        rolExistente.setIdRol(1);
        rolExistente.setNombreRol("ADMIN");

        rolDTO.setIdRol(2); // DTO tiene ID 2, URL tiene 1

        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            when(service.getRolByIdDTO(1)).thenReturn(rolExistente);
            when(service.existsRol("ADMIN")).thenReturn(true);
            when(bindingResult.hasFieldErrors()).thenReturn(false);

            assertThrows(ConflictException.class, () -> controller.putRol(rolDTO, bindingResult, "1"));
        }
    }

    @Test
    void putRol_Exito() {
        RolDTO rolExistente = new RolDTO();
        rolExistente.setIdRol(1);
        rolExistente.setNombreRol("ADMIN");

        rolDTO.setIdRol(1); // Mismos IDs

        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            when(service.getRolByIdDTO(1)).thenReturn(rolExistente);
            when(service.existsRol("ADMIN")).thenReturn(false); // No colisiona con nadie
            when(bindingResult.hasFieldErrors()).thenReturn(false);
            when(service.putRolDTO(rolDTO)).thenReturn(rolDTO);

            ResponseEntity<Object> response = controller.putRol(rolDTO, bindingResult, "1");
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }

    // ==========================================
    // DELETE ROL
    // ==========================================

    @Test
    void deleteRol_NoExiste_LanzaNotFound() {
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            when(service.getRolByIdDTO(1)).thenReturn(null);

            assertThrows(NotFoundException.class, () -> controller.deleteRol("1"));
        }
    }

    @Test
    void deleteRol_Exito() {
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            when(service.getRolByIdDTO(1)).thenReturn(rolDTO);
            when(service.deleteRol(1)).thenReturn(rolDTO);

            ResponseEntity<RolDTO> response = controller.deleteRol("1");
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }
}