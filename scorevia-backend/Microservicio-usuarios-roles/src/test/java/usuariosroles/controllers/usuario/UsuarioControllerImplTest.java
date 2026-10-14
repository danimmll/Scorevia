package usuariosroles.controllers.usuario;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BindingResult;
import usuariosroles.config.ValidationUtils;
import usuariosroles.dto.UsuarioDTO;
import usuariosroles.dto.UsuarioUpdateDTO;
import usuariosroles.exception.BadRequestException;
import usuariosroles.exception.ConflictException;
import usuariosroles.exception.DuplicateException;
import usuariosroles.exception.NoContentException;
import usuariosroles.exception.NotFoundException;
import usuariosroles.services.excel.UsuarioExcelService;
import usuariosroles.services.usuario.UsuarioServiceImpl;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioControllerImplTest {

    @Mock private UsuarioServiceImpl service;
    @Mock private UsuarioExcelService excelService;
    @Mock private BindingResult bindingResult;

    @InjectMocks
    private UsuarioControllerImpl controller;

    private UsuarioDTO usuarioDTO;
    private UsuarioUpdateDTO usuarioUpdateDTO;

    @BeforeEach
    void setUp() {
        usuarioDTO = new UsuarioDTO();
        usuarioDTO.setIdUsuario(1);
        usuarioDTO.setCorreoUsuario("test@test.com");

        usuarioUpdateDTO = new UsuarioUpdateDTO();
        usuarioUpdateDTO.setIdUsuario(1);
        usuarioUpdateDTO.setCorreoUsuario("test@test.com");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getUsuarios_Vacio_LanzaNoContent() {
        when(service.getUsuarios(any(Pageable.class))).thenReturn(Page.empty());
        assertThrows(NoContentException.class, () -> controller.getUsuarios(0, 10, "idUsuario", "desc"));
    }

    @Test
    void getUsuarios_Desc_Exito() {
        when(service.getUsuarios(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(usuarioDTO)));
        ResponseEntity<Page<UsuarioDTO>> response = controller.getUsuarios(0, 10, "idUsuario", "desc");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assert response.getBody() != null;
        assertFalse(response.getBody().isEmpty());
    }

    @Test
    void getUsuarios_Asc_Exito() {
        when(service.getUsuarios(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(usuarioDTO)));
        ResponseEntity<Page<UsuarioDTO>> response = controller.getUsuarios(0, 10, "idUsuario", "asc"); // Probar case "asc"
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void getUsuarioById_NoExiste_LanzaNotFound() {
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            when(service.getUsuarioById(1)).thenReturn(null);

            assertThrows(NotFoundException.class, () -> controller.getUsuarioById("1"));
        }
    }

    @Test
    void getUsuarioById_Exito() {
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            when(service.getUsuarioById(1)).thenReturn(usuarioDTO);

            ResponseEntity<UsuarioDTO> response = controller.getUsuarioById("1");
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assert response.getBody() != null;
            assertEquals(1, response.getBody().getIdUsuario());
        }
    }

    @Test
    void crearUsuario_CuentaDesactivada_Reactiva() {
        when(service.existsUsuarioDesactivado(anyString())).thenReturn(true);
        when(service.activarUsuarioDTO(any())).thenReturn(usuarioDTO);

        ResponseEntity<Object> response = controller.crearUsuario(usuarioDTO, bindingResult);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(service).activarUsuarioDTO(usuarioDTO);
    }

    @Test
    void crearUsuario_Duplicado_LanzaDuplicateException() {
        when(service.existsUsuarioDesactivado(anyString())).thenReturn(false);
        when(service.existsUsuario(anyString())).thenReturn(true);

        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validation(bindingResult)).thenReturn("Error Validacion");
            assertThrows(DuplicateException.class, () -> controller.crearUsuario(usuarioDTO, bindingResult));
        }
    }

    @Test
    void crearUsuario_ErroresValidacion_LanzaBadRequest() {
        when(bindingResult.hasFieldErrors()).thenReturn(true);

        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validation(bindingResult)).thenReturn("Error Validacion");
            assertThrows(BadRequestException.class, () -> controller.crearUsuario(usuarioDTO, bindingResult));
        }
    }

    @Test
    void crearUsuario_Exito() {
        when(service.existsUsuarioDesactivado(anyString())).thenReturn(false);
        when(service.existsUsuario(anyString())).thenReturn(false);
        when(bindingResult.hasFieldErrors()).thenReturn(false);
        when(service.crearUsuario(any())).thenReturn(usuarioDTO);

        ResponseEntity<Object> response = controller.crearUsuario(usuarioDTO, bindingResult);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void putUsuario_NoExiste_LanzaNotFound() {
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            when(service.getUsuarioById(1)).thenReturn(null);

            assertThrows(NotFoundException.class, () -> controller.putUsuario(usuarioUpdateDTO, bindingResult, "1"));
        }
    }

    // Tras separar UsuarioUpdateDTO de UsuarioDTO (ya no exige contraseña en cada PUT), el
    // filtrado especial de "ignorar errores de correo/contraseña si un ADMIN edita a otro
    // usuario" dejó de tener sentido -- esas reglas ya no viven en el DTO de actualización --
    // así que ahora cualquier error de validación en el body simplemente lanza BadRequest,
    // sin importar quién edite a quién.
    @Test
    void putUsuario_ConErroresValidacion_LanzaBadRequest() {
        when(bindingResult.hasFieldErrors()).thenReturn(true);

        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            val.when(() -> ValidationUtils.validation(bindingResult)).thenReturn("Error Validacion");
            when(service.getUsuarioById(1)).thenReturn(usuarioDTO);

            assertThrows(BadRequestException.class, () -> controller.putUsuario(usuarioUpdateDTO, bindingResult, "1"));
        }
    }

    @Test
    void putUsuario_ConflictoIds_LanzaConflictException() {
        when(bindingResult.hasFieldErrors()).thenReturn(false);
        usuarioUpdateDTO.setIdUsuario(2); // DTO tiene ID 2

        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1); // URL tiene ID 1
            when(service.getUsuarioById(1)).thenReturn(usuarioDTO);

            assertThrows(ConflictException.class, () -> controller.putUsuario(usuarioUpdateDTO, bindingResult, "1"));
        }
    }

    @Test
    void putUsuario_IdDTONull_Exito() {
        when(bindingResult.hasFieldErrors()).thenReturn(false);
        usuarioUpdateDTO.setIdUsuario(null); // ID null pasa la validación de conflicto

        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            when(service.getUsuarioById(1)).thenReturn(new UsuarioDTO());
            when(service.putUsuarioDTO(any())).thenReturn(usuarioDTO);

            ResponseEntity<Object> response = controller.putUsuario(usuarioUpdateDTO, bindingResult, "1");
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(1, usuarioUpdateDTO.getIdUsuario()); // Se le debió asignar el ID 1
        }
    }

    @Test
    void putUsuario_Exito() {
        when(bindingResult.hasFieldErrors()).thenReturn(false);

        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            when(service.getUsuarioById(1)).thenReturn(usuarioDTO);
            when(service.putUsuarioDTO(any())).thenReturn(usuarioDTO);

            ResponseEntity<Object> response = controller.putUsuario(usuarioUpdateDTO, bindingResult, "1");
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }

    @Test
    void deleteUsuario_NoExiste_LanzaNotFound() {
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            when(service.getUsuarioById(1)).thenReturn(null);

            assertThrows(NotFoundException.class, () -> controller.deleteUsuario("1"));
        }
    }

    @Test
    void deleteUsuario_Exito() {
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            when(service.getUsuarioById(1)).thenReturn(usuarioDTO);
            when(service.deleteUsuarioDTO(1)).thenReturn(usuarioDTO);

            ResponseEntity<UsuarioDTO> response = controller.deleteUsuario("1");
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }

    // ==========================================
    // EXPORTAR EXCEL
    // ==========================================

    @Test
    void exportarTodosUsuariosExcel_Vacio_LanzaNoContent() {
        when(service.contarUsuarios()).thenReturn(0L);
        assertThrows(NoContentException.class, () -> controller.exportarTodosUsuariosExcel());
        verifyNoInteractions(excelService);
    }

    @Test
    void exportarTodosUsuariosExcel_Exito() {
        when(service.contarUsuarios()).thenReturn(1L);
        when(excelService.exportarUsuariosAExcel(any())).thenReturn(new ByteArrayInputStream(new byte[0]));

        ResponseEntity<InputStreamResource> response = controller.exportarTodosUsuariosExcel();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                Objects.requireNonNull(response.getHeaders().getContentType()).toString());
        assertNotNull(response.getHeaders().get("Content-Disposition"));
    }
}