package usuariosroles.services.rol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import usuariosroles.dto.RolDTO;
import usuariosroles.entities.Rol;
import usuariosroles.entities.Usuario;
import usuariosroles.exception.ConflictException;
import usuariosroles.exception.NotFoundException;
import usuariosroles.mappers.RolMapper;
import usuariosroles.repositories.RolRepository;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RolServiceImplTest {

    @Mock
    private RolRepository rolRepository;

    @Mock
    private RolMapper rolMapper;

    @InjectMocks
    private RolServiceImpl rolService;

    private Rol rolEntity;
    private RolDTO rolDto;

    @BeforeEach
    void setUp() {
        rolEntity = new Rol();
        rolEntity.setIdRol(1);
        rolEntity.setNombreRol("PREMIUM");
        rolEntity.setActivo(true);
        rolEntity.setUsuarios(new HashSet<>()); // Inicializado sin usuarios

        rolDto = new RolDTO();
        rolDto.setIdRol(1);
        rolDto.setNombreRol("PREMIUM");
        rolDto.setActivo(true);
    }

    // =========================================================================
    // TESTS PARA crearRolDTO
    // =========================================================================
    @Test
    void crearRolDTO_DeberiaCrearYRetornarRol() {
        when(rolMapper.toRol(any(RolDTO.class))).thenReturn(rolEntity);
        when(rolRepository.save(any(Rol.class))).thenReturn(rolEntity);
        when(rolMapper.toDtoPostDelete(any(Rol.class))).thenReturn(rolDto);

        RolDTO result = rolService.crearRolDTO(rolDto);

        assertNotNull(result);
        assertEquals("PREMIUM", result.getNombreRol());
        verify(rolRepository, times(1)).save(rolEntity);
        verify(rolMapper, times(1)).toDtoPostDelete(rolEntity);
    }

    // =========================================================================
    // TESTS PARA getRolByIdDTO
    // =========================================================================
    @Test
    void getRolByIdDTO_CuandoExiste_DeberiaRetornarRol() {
        when(rolRepository.findByIdRolAndActivo(1, true)).thenReturn(Optional.of(rolEntity));
        when(rolMapper.toDtoGet(rolEntity)).thenReturn(rolDto);

        RolDTO result = rolService.getRolByIdDTO(1);

        assertNotNull(result);
        assertEquals(1, result.getIdRol());
        verify(rolRepository, times(1)).findByIdRolAndActivo(1, true);
    }

    @Test
    void getRolByIdDTO_CuandoNoExiste_DeberiaRetornarNull() {
        when(rolRepository.findByIdRolAndActivo(99, true)).thenReturn(Optional.empty());

        RolDTO result = rolService.getRolByIdDTO(99);

        assertNull(result);
        verify(rolRepository, times(1)).findByIdRolAndActivo(99, true);
        verify(rolMapper, never()).toDtoGet(any());
    }

    // =========================================================================
    // TESTS PARA getRolesDTO
    // =========================================================================
    @Test
    void getRolesDTO_DeberiaRetornarListaDeRoles() {
        when(rolRepository.findAllByActivo(true)).thenReturn(List.of(rolEntity));
        when(rolMapper.toDtoGet(any(Rol.class))).thenReturn(rolDto);

        List<RolDTO> result = rolService.getRolesDTO();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("PREMIUM", result.getFirst().getNombreRol());
        verify(rolRepository, times(1)).findAllByActivo(true);
    }

    // =========================================================================
    // TESTS PARA putRolDTO
    // =========================================================================
    @Test
    void putRolDTO_CuandoExisteYActivoNoEsNull_DeberiaActualizarCampos() {
        RolDTO updateDto = new RolDTO();
        updateDto.setIdRol(1);
        updateDto.setNombreRol("NUEVO_PREMIUM");
        updateDto.setActivo(false);

        when(rolRepository.findByIdRolAndActivo(1, true)).thenReturn(Optional.of(rolEntity));
        when(rolRepository.save(any(Rol.class))).thenReturn(rolEntity);
        when(rolMapper.toDtoPut(any(Rol.class))).thenReturn(updateDto);

        RolDTO result = rolService.putRolDTO(updateDto);

        assertNotNull(result);
        assertEquals("NUEVO_PREMIUM", result.getNombreRol());
        verify(rolRepository, times(1)).save(rolEntity);
    }

    @Test
    void putRolDTO_CuandoExisteYActivoEsNull_DeberiaMapearSoloNombre() {
        RolDTO updateDto = new RolDTO();
        updateDto.setIdRol(1);
        updateDto.setNombreRol("ADMIN");
        updateDto.setActivo(null); // Caso donde Activo viene nulo

        when(rolRepository.findByIdRolAndActivo(1, true)).thenReturn(Optional.of(rolEntity));
        when(rolRepository.save(any(Rol.class))).thenReturn(rolEntity);
        when(rolMapper.toDtoPut(any(Rol.class))).thenReturn(updateDto);

        RolDTO result = rolService.putRolDTO(updateDto);

        assertNotNull(result);
        verify(rolRepository, times(1)).save(rolEntity);
        assertTrue(rolEntity.getActivo(), "El campo activo de la entidad no debió modificarse");
    }

    @Test
    void putRolDTO_CuandoNoExiste_DeberiaRetornarNull() {
        when(rolRepository.findByIdRolAndActivo(1, true)).thenReturn(Optional.empty());

        RolDTO result = rolService.putRolDTO(rolDto);

        assertNull(result);
        verify(rolRepository, never()).save(any());
    }

    // =========================================================================
    // TESTS PARA activarRolDTO
    // =========================================================================
    @Test
    void activarRolDTO_CuandoExiste_DeberiaActivarRolYGuardar() {
        rolEntity.setActivo(false);
        when(rolRepository.findByNombreRol("PREMIUM")).thenReturn(Optional.of(rolEntity));
        when(rolRepository.save(rolEntity)).thenReturn(rolEntity);
        when(rolMapper.toDtoPut(rolEntity)).thenReturn(rolDto);

        RolDTO result = rolService.activarRolDTO(rolDto);

        assertNotNull(result);
        assertTrue(rolEntity.getActivo(), "La entidad debió marcarse como activa a true antes de guardar");
        verify(rolRepository, times(1)).save(rolEntity);
    }

    @Test
    void activarRolDTO_CuandoNoExiste_DeberiaRetornarNull() {
        when(rolRepository.findByNombreRol("PREMIUM")).thenReturn(Optional.empty());

        RolDTO result = rolService.activarRolDTO(rolDto);

        assertNull(result);
        verify(rolRepository, never()).save(any());
    }

    // =========================================================================
    // TESTS PARA deleteRol
    // =========================================================================
    @Test
    void deleteRol_CuandoNoTieneUsuarios_DeberiaDesactivarLogicamente() {
        // rolEntity ya inicializa usuarios como un set vacío
        when(rolRepository.findByIdRolAndActivo(1, true)).thenReturn(Optional.of(rolEntity));
        when(rolRepository.save(rolEntity)).thenReturn(rolEntity);

        RolDTO postDeleteDto = new RolDTO();
        postDeleteDto.setActivo(false);
        when(rolMapper.toDtoPostDelete(rolEntity)).thenReturn(postDeleteDto);

        RolDTO result = rolService.deleteRol(1);

        assertNotNull(result);
        assertFalse(rolEntity.getActivo(), "La entidad debió ser marcada como inactiva (soft delete)");
        verify(rolRepository, times(1)).save(rolEntity);
    }

    @Test
    void deleteRol_CuandoTieneUsuarios_DeberiaLanzarConflictException() {
        Set<Usuario> usuarios = new HashSet<>();
        usuarios.add(new Usuario()); // Se añade un usuario al Rol
        rolEntity.setUsuarios(usuarios);

        when(rolRepository.findByIdRolAndActivo(1, true)).thenReturn(Optional.of(rolEntity));

        assertThrows(ConflictException.class, () -> rolService.deleteRol(1));
        verify(rolRepository, never()).save(any());
    }

    @Test
    void deleteRol_CuandoNoExiste_DeberiaLanzarNotFoundException() {
        when(rolRepository.findByIdRolAndActivo(99, true)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> rolService.deleteRol(99));
        verify(rolRepository, never()).save(any());
    }

    // =========================================================================
    // TESTS PARA existsRol y existsRolDesactivado
    // =========================================================================
    @Test
    void existsRol_DeberiaRetornarResultadoDelRepositorio() {
        when(rolRepository.existsByNombreRol("PREMIUM")).thenReturn(true);

        boolean result = rolService.existsRol("PREMIUM");

        assertTrue(result);
        verify(rolRepository, times(1)).existsByNombreRol("PREMIUM");
    }

    @Test
    void existsRolDesactivado_DeberiaRetornarResultadoDelRepositorio() {
        when(rolRepository.existsByNombreRolAndActivo("PREMIUM", false)).thenReturn(true);

        boolean result = rolService.existsRolDesactivado("PREMIUM");

        assertTrue(result);
        verify(rolRepository, times(1)).existsByNombreRolAndActivo("PREMIUM", false);
    }
}