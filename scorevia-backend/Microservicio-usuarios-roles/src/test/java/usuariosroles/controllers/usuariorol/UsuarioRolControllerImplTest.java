package usuariosroles.controllers.usuariorol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import usuariosroles.config.ValidationUtils;
import usuariosroles.dto.RolDTO;
import usuariosroles.dto.UsuarioDTO;
import usuariosroles.dto.UsuarioRolDTO;
import usuariosroles.exception.BadRequestException;
import usuariosroles.exception.NoContentException;
import usuariosroles.exception.NotFoundException;
import usuariosroles.services.rol.RolServiceImpl;
import usuariosroles.services.usuariorol.UsuarioRolServiceImpl;
import usuariosroles.services.usuario.UsuarioServiceImpl;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioRolControllerImplTest {

    @Mock private UsuarioRolServiceImpl service;
    @Mock private UsuarioServiceImpl usuarioService;
    @Mock private RolServiceImpl rolService;
    @Mock private BindingResult bindingResult;

    @InjectMocks
    private UsuarioRolControllerImpl controller;

    private UsuarioRolDTO dto;

    @BeforeEach
    void setUp() {
        dto = new UsuarioRolDTO();
        dto.setIdRol(1);
    }

    // ==========================================
    // ASIGNAR ROL
    // ==========================================

    @Test
    void asignarRol_UsuarioNotFound() {
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            when(usuarioService.getUsuarioById(1)).thenReturn(null);

            assertThrows(NotFoundException.class, () -> controller.asignarRol(dto, bindingResult, "1"));
        }
    }

    @Test
    void asignarRol_ConErroresValidacion_LanzaBadRequest() {
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            val.when(() -> ValidationUtils.validation(bindingResult)).thenReturn("Error Valid");
            when(usuarioService.getUsuarioById(1)).thenReturn(new UsuarioDTO());
            when(bindingResult.hasFieldErrors()).thenReturn(true);

            assertThrows(BadRequestException.class, () -> controller.asignarRol(dto, bindingResult, "1"));
        }
    }

    @Test
    void asignarRol_IdRolNulo_LanzaBadRequest() {
        dto.setIdRol(null);
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            when(usuarioService.getUsuarioById(1)).thenReturn(new UsuarioDTO());
            when(bindingResult.hasFieldErrors()).thenReturn(false);

            assertThrows(BadRequestException.class, () -> controller.asignarRol(dto, bindingResult, "1"));
        }
    }

    @Test
    void asignarRol_IdRolCeroONegativo_LanzaBadRequest() {
        dto.setIdRol(0);
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            val.when(() -> ValidationUtils.validarId("0")).thenReturn(0);
            when(usuarioService.getUsuarioById(1)).thenReturn(new UsuarioDTO());
            when(bindingResult.hasFieldErrors()).thenReturn(false);

            assertThrows(BadRequestException.class, () -> controller.asignarRol(dto, bindingResult, "1"));
        }
    }

    @Test
    void asignarRol_RolNotFound() {
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            when(usuarioService.getUsuarioById(1)).thenReturn(new UsuarioDTO());
            when(bindingResult.hasFieldErrors()).thenReturn(false);
            when(rolService.getRolByIdDTO(1)).thenReturn(null);

            assertThrows(NotFoundException.class, () -> controller.asignarRol(dto, bindingResult, "1"));
        }
    }

    @Test
    void asignarRol_Exito() {
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            when(usuarioService.getUsuarioById(1)).thenReturn(new UsuarioDTO());
            when(bindingResult.hasFieldErrors()).thenReturn(false);
            when(rolService.getRolByIdDTO(1)).thenReturn(new RolDTO());
            when(service.asignarRolUsuario(1, 1)).thenReturn(dto);

            ResponseEntity<Object> response = controller.asignarRol(dto, bindingResult, "1");
            assertEquals(HttpStatus.CREATED, response.getStatusCode());
        }
    }

    // ==========================================
    // QUITAR ROL
    // ==========================================

    @Test
    void quitarRol_UsuarioNotFound() {
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            when(usuarioService.getUsuarioById(1)).thenReturn(null);
            assertThrows(NotFoundException.class, () -> controller.quitarRol("1", "1"));
        }
    }

    @Test
    void quitarRol_RolNotFound() {
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            when(usuarioService.getUsuarioById(1)).thenReturn(new UsuarioDTO());
            when(rolService.getRolByIdDTO(1)).thenReturn(null);
            assertThrows(NotFoundException.class, () -> controller.quitarRol("1", "1"));
        }
    }

    @Test
    void quitarRol_Exito() {
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            when(usuarioService.getUsuarioById(1)).thenReturn(new UsuarioDTO());
            when(rolService.getRolByIdDTO(1)).thenReturn(new RolDTO());
            when(service.quitarRolUsuario(1, 1)).thenReturn(dto);

            ResponseEntity<UsuarioRolDTO> response = controller.quitarRol("1", "1");
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }

    // ==========================================
    // GET ROLES DE UN USUARIO
    // ==========================================

    @Test
    void getRolesUsuario_UsuarioNotFound() {
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            when(usuarioService.usuarioActivo(1)).thenReturn(false);
            assertThrows(NotFoundException.class, () -> controller.getRolesUsuario("1"));
        }
    }

    @Test
    void getRolesUsuario_Exito() {
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            when(usuarioService.usuarioActivo(1)).thenReturn(true);
            when(service.getRolesUsuario(1)).thenReturn(List.of(dto));

            ResponseEntity<List<UsuarioRolDTO>> response = controller.getRolesUsuario("1");
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }

    // ==========================================
    // GET USUARIOS DE UN ROL
    // ==========================================

    @Test
    void getUsuariosRol_RolNotFound() {
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            when(rolService.getRolByIdDTO(1)).thenReturn(null);
            assertThrows(NotFoundException.class, () -> controller.getUsuariosRol("1"));
        }
    }

    @Test
    void getUsuariosRol_Exito() {
        try (MockedStatic<ValidationUtils> val = mockStatic(ValidationUtils.class)) {
            val.when(() -> ValidationUtils.validarId("1")).thenReturn(1);
            when(rolService.getRolByIdDTO(1)).thenReturn(new RolDTO());
            when(service.getUsuariosRol(1)).thenReturn(List.of(dto));

            ResponseEntity<List<UsuarioRolDTO>> response = controller.getUsuariosRol("1");
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }

    // ==========================================
    // GET TODAS LAS ASIGNACIONES
    // ==========================================

    @Test
    void getTodasAsignaciones_NoContent() {
        when(service.getAsignaciones(any(Pageable.class))).thenReturn(Page.empty());
        assertThrows(NoContentException.class, () -> controller.getTodasAsignaciones(0, 20, "id.idUsuario", "asc"));
    }

    @Test
    void getTodasAsignaciones_Exito() {
        when(service.getAsignaciones(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(dto)));
        ResponseEntity<Page<UsuarioRolDTO>> response = controller.getTodasAsignaciones(0, 20, "id.idUsuario", "asc");
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}