package usuariosroles.controllers.usuario;

import java.io.ByteArrayInputStream;

import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import usuariosroles.dto.UsuarioDTO;
import usuariosroles.dto.UsuarioUpdateDTO;
import usuariosroles.exception.BadRequestException;
import usuariosroles.exception.ConflictException;
import usuariosroles.exception.DuplicateException;
import usuariosroles.exception.NoContentException;
import usuariosroles.exception.NotFoundException;
import usuariosroles.services.excel.UsuarioExcelService;
import usuariosroles.services.usuario.UsuarioServiceImpl;
import usuariosroles.config.ValidationUtils;

@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
@Tag(name = "Usuarios", description = "Operaciones relacionadas con la gestión de usuarios")
public class UsuarioControllerImpl implements UsuarioController{

    private final UsuarioServiceImpl service;
    private final UsuarioExcelService excelService;

    @GetMapping
    public ResponseEntity<Page<UsuarioDTO>> getUsuarios(int page,int size,String sort,String dir) {
        Sort.Direction direction = dir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(direction, sort));
        Page<UsuarioDTO> usuarios = service.getUsuarios(pageable);
        if (usuarios.isEmpty()) {
            throw new NoContentException("Sin ningún usuario");
        }
        return ResponseEntity.ok(usuarios);
    }

    @GetMapping("/{id_usuario}")
    public ResponseEntity<UsuarioDTO> getUsuarioById(@PathVariable("id_usuario") String idString) {

        Integer id = ValidationUtils.validarId(idString);

        UsuarioDTO usuario = service.getUsuarioById(id);
        if (usuario == null) {
            throw new NotFoundException("Usuario no encontrado", "usuario-no-encontrado");
        }
        return ResponseEntity.ok(usuario);
    }

    @PostMapping
    public ResponseEntity<Object> crearUsuario(@Valid @RequestBody UsuarioDTO usuarioDTO, BindingResult result) {
        if (result.hasFieldErrors()) {
            throw new BadRequestException(ValidationUtils.validation(result));
        }

        if (service.existsUsuarioDesactivado(usuarioDTO.getCorreoUsuario())) {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.activarUsuarioDTO(usuarioDTO));
        }

        if (service.existsUsuario(usuarioDTO.getCorreoUsuario())) {
            throw new DuplicateException("El correo ya está registrado.", "correo-en-uso");
        }

        if (service.existsNickname(usuarioDTO.getNickname())) {
            throw new DuplicateException("Ese nickname ya está en uso.", "nickname-en-uso");
        }

        UsuarioDTO usuarioAux = service.crearUsuario(usuarioDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioAux);
    }

    @PutMapping("/{id_usuario}")
    public ResponseEntity<Object> putUsuario(@Valid @RequestBody UsuarioUpdateDTO usuarioDTO, BindingResult result,
                                        @PathVariable("id_usuario") String idString) {

        Integer id = ValidationUtils.validarId(idString);

        UsuarioDTO usuario = service.getUsuarioById(id);
        if (usuario == null) {
            throw new NotFoundException("Usuario No encontrado", "usuario-no-encontrado");
        }

        // Ya no hace falta filtrar "falsos" errores de validación por rol: al separar el DTO
        // de actualización del de creación, correoUsuario/contrasenhaUsuario dejaron de tener
        // reglas que solo tenían sentido para un alta nueva (ver UsuarioUpdateDTO).
        if (result.hasFieldErrors()) {
            throw new BadRequestException(ValidationUtils.validation(result));
        }

        if (usuarioDTO.getIdUsuario() != null && !usuarioDTO.getIdUsuario().equals(id)) {
            throw new ConflictException("Conflicto entre id proporcionado y el body");
        }

        usuarioDTO.setIdUsuario(id);
        return ResponseEntity.ok(service.putUsuarioDTO(usuarioDTO));
    }

    @DeleteMapping("/{id_usuario}")
    public ResponseEntity<UsuarioDTO> deleteUsuario(@PathVariable("id_usuario") String idString) {
        
        Integer id = ValidationUtils.validarId(idString);

        UsuarioDTO usuario = service.getUsuarioById(id);
        if (usuario == null) {
            throw new NotFoundException("Usuario No encontrado", "usuario-no-encontrado");
        }
        return ResponseEntity.ok(service.deleteUsuarioDTO(id));
    }
    
    @Override
    public ResponseEntity<InputStreamResource> exportarTodosUsuariosExcel() {

        // Cuenta antes de exportar en vez de comprobar una Page ya cargada: con el export
        // ahora en streaming (ver UsuarioServiceImpl.exportarTodosStreaming) no hay ya una
        // Page completa que inspeccionar de antemano.
        if (service.contarUsuarios() == 0) {
            throw new NoContentException("No hay usuarios para exportar.");
        }

        ByteArrayInputStream stream = excelService.exportarUsuariosAExcel(service::exportarTodosStreaming);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=listado_usuarios_completo.xlsx");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(stream));
    }
    
    
    
    
}