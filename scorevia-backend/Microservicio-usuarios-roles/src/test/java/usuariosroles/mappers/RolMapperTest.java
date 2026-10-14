package usuariosroles.mappers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import usuariosroles.dto.RolDTO;
import usuariosroles.entities.Rol;

import static org.junit.jupiter.api.Assertions.*;

class RolMapperTest {

    private final RolMapper rolMapper = new RolMapperImpl();

    private Rol rol;
    private RolDTO rolDTO;

    @BeforeEach
    void setUp() {
        rol = Rol.builder()
                .idRol(5)
                .nombreRol("ADMIN")
                .activo(true)
                .build();

        rolDTO = RolDTO.builder()
                .idRol(5)
                .nombreRol("ADMIN")
                .activo(true)
                .build();
    }

    @Test
    void toDtoPostDelete_deberiaMapearCamposIgnorandoActivo() {
        RolDTO dto = rolMapper.toDtoPostDelete(rol);

        assertNotNull(dto);
        assertEquals(rol.getIdRol(), dto.getIdRol());
        assertEquals(rol.getNombreRol(), dto.getNombreRol());
        assertNull(dto.getActivo());
    }

    @Test
    void toDtoGet_deberiaMapearTodosLosCampos() {
        RolDTO dto = rolMapper.toDtoGet(rol);

        assertNotNull(dto);
        assertEquals(rol.getIdRol(), dto.getIdRol());
        assertEquals(rol.getNombreRol(), dto.getNombreRol());
        assertEquals(rol.getActivo(), dto.getActivo());
    }

    @Test
    void toDtoPut_deberiaMapearIgnorandoIdRol() {
        RolDTO dto = rolMapper.toDtoPut(rol);

        assertNotNull(dto);
        assertNull(dto.getIdRol());
        assertEquals(rol.getNombreRol(), dto.getNombreRol());
        assertEquals(rol.getActivo(), dto.getActivo());
    }

    @Test
    void toRol_deberiaMapearDesdeDTO() {
        Rol resultado = rolMapper.toRol(rolDTO);

        assertNotNull(resultado);
        assertEquals(rolDTO.getIdRol(), resultado.getIdRol());
        assertEquals(rolDTO.getNombreRol(), resultado.getNombreRol());
        assertEquals(rolDTO.getActivo(), resultado.getActivo());
    }

    @Test
    void toDtoGet_deberiaDevolverNullSiElRolEsNull() {
        assertNull(rolMapper.toDtoGet(null));
    }
}