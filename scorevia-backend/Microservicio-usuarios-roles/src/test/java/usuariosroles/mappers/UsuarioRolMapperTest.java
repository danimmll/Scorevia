package usuariosroles.mappers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import usuariosroles.dto.UsuarioRolDTO;
import usuariosroles.entities.Rol;
import usuariosroles.entities.Usuario;
import usuariosroles.entities.UsuarioRol;
import usuariosroles.models.UsuarioRolId;

import static org.junit.jupiter.api.Assertions.*;

class UsuarioRolMapperTest {

    private final UsuarioRolMapper usuarioRolMapper = new UsuarioRolMapperImpl();

    private UsuarioRol usuarioRol;

    @BeforeEach
    void setUp() {
        Usuario usuario = Usuario.builder()
                .idUsuario(12)
                .nombreUsuario("Juan Pérez")
                .activo(true)
                .build();

        Rol rol = Rol.builder()
                .idRol(5)
                .nombreRol("ADMIN")
                .activo(true)
                .build();

        usuarioRol = UsuarioRol.builder()
                .id(UsuarioRolId.builder().idUsuario(12).idRol(5).build())
                .usuario(usuario)
                .rol(rol)
                .build();
    }

    @Test
    void toDtoDeleteGetPost_deberiaMapearSoloIdsDeUsuarioYRol() {
        UsuarioRolDTO dto = usuarioRolMapper.toDtoDeleteGetPost(usuarioRol);

        assertNotNull(dto);
        assertEquals(12, dto.getIdUsuario());
        assertEquals(5, dto.getIdRol());
        assertNull(dto.getNombreUsuario());
        assertNull(dto.getActivoUsuario());
        assertNull(dto.getNombreRol());
        assertNull(dto.getActivoRol());
    }

    @Test
    void toDtoRolesUsuario_deberiaMapearInfoDelRolEIgnorarUsuario() {
        UsuarioRolDTO dto = usuarioRolMapper.toDtoRolesUsuario(usuarioRol);

        assertNotNull(dto);
        assertEquals(5, dto.getIdRol());
        assertEquals("ADMIN", dto.getNombreRol());
        assertTrue(dto.getActivoRol());
        assertNull(dto.getIdUsuario());
        assertNull(dto.getNombreUsuario());
        assertNull(dto.getActivoUsuario());
    }

    @Test
    void toDtoUsuariosRol_deberiaMapearInfoDelUsuarioEIgnorarRol() {
        UsuarioRolDTO dto = usuarioRolMapper.toDtoUsuariosRol(usuarioRol);

        assertNotNull(dto);
        assertEquals("Juan Pérez", dto.getNombreUsuario());
        assertTrue(dto.getActivoUsuario());
        assertNull(dto.getIdUsuario());
        assertNull(dto.getIdRol());
        assertNull(dto.getNombreRol());
        assertNull(dto.getActivoRol());
    }
}
