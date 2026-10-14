package usuariosroles.mappers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import usuariosroles.dto.UsuarioDTO;
import usuariosroles.entities.Usuario;

import static org.junit.jupiter.api.Assertions.*;

class UsuarioMapperTest {

    private final UsuarioMapper usuarioMapper = new UsuarioMapperImpl();

    private Usuario usuario;
    private UsuarioDTO usuarioDTO;

    @BeforeEach
    void setUp() {
        usuario = Usuario.builder()
                .idUsuario(12)
                .nickname("ElBicho7")
                .nombreUsuario("Juan")
                .apellidoUsuario("Pérez")
                .correoUsuario("juan.perez@example.com")
                .contrasenhaUsuario("P@ssw0rd!")
                .activo(true)
                .urlFoto("https://ejemplo.com/foto.jpg")
                .esPremium(false)
                .build();

        usuarioDTO = UsuarioDTO.builder()
                .idUsuario(12)
                .nickname("ElBicho7")
                .nombreUsuario("Juan")
                .apellidoUsuario("Pérez")
                .correoUsuario("juan.perez@example.com")
                .contrasenhaUsuario("P@ssw0rd!")
                .activo(true)
                .urlFoto("https://ejemplo.com/foto.jpg")
                .esPremium(false)
                .build();
    }

    @Test
    void toDtoPost_deberiaIgnorarIdActivoRolesYContrasenha() {
        UsuarioDTO dto = usuarioMapper.toDtoPost(usuario);

        assertNotNull(dto);
        assertNull(dto.getIdUsuario());
        assertNull(dto.getActivo());
        assertNull(dto.getContrasenhaUsuario());
        assertEquals(usuario.getNickname(), dto.getNickname());
        assertEquals(usuario.getNombreUsuario(), dto.getNombreUsuario());
        assertEquals(usuario.getApellidoUsuario(), dto.getApellidoUsuario());
        assertEquals(usuario.getCorreoUsuario(), dto.getCorreoUsuario());
    }

    @Test
    void toDtoGetById_deberiaIgnorarContrasenhaYRoles_peroConservarActivo() {
        UsuarioDTO dto = usuarioMapper.toDtoGetById(usuario);

        assertNotNull(dto);
        assertEquals(usuario.getIdUsuario(), dto.getIdUsuario());
        assertNull(dto.getContrasenhaUsuario());
        assertEquals(usuario.getActivo(), dto.getActivo());
        assertEquals(usuario.getNickname(), dto.getNickname());
    }

    @Test
    void toDtoGetAll_deberiaIgnorarContrasenhaActivoYRoles() {
        UsuarioDTO dto = usuarioMapper.toDtoGetAll(usuario);

        assertNotNull(dto);
        assertEquals(usuario.getIdUsuario(), dto.getIdUsuario());
        assertNull(dto.getContrasenhaUsuario());
        assertNull(dto.getActivo());
        assertEquals(usuario.getNombreUsuario(), dto.getNombreUsuario());
    }

    @Test
    void toDtoExcel_deberiaIgnorarContrasenhaYRoles() {
        UsuarioDTO dto = usuarioMapper.toDtoExcel(usuario);

        assertNotNull(dto);
        assertNull(dto.getContrasenhaUsuario());
        assertEquals(usuario.getIdUsuario(), dto.getIdUsuario());
        assertEquals(usuario.getCorreoUsuario(), dto.getCorreoUsuario());
    }

    @Test
    void toDtoPut_deberiaIgnorarIdRolesYContrasenha() {
        UsuarioDTO dto = usuarioMapper.toDtoPut(usuario);

        assertNotNull(dto);
        assertNull(dto.getIdUsuario());
        assertNull(dto.getContrasenhaUsuario());
        assertEquals(usuario.getNombreUsuario(), dto.getNombreUsuario());
        assertEquals(usuario.getActivo(), dto.getActivo());
    }

    @Test
    void toDtoDelete_deberiaIgnorarCamposSensibles() {
        UsuarioDTO dto = usuarioMapper.toDtoDelete(usuario);

        assertNotNull(dto);
        assertNull(dto.getCorreoUsuario());
        assertNull(dto.getContrasenhaUsuario());
        assertNull(dto.getActivo());
        assertNull(dto.getUrlFoto());
        assertNull(dto.getNickname());
        assertEquals(usuario.getIdUsuario(), dto.getIdUsuario());
        assertEquals(usuario.getNombreUsuario(), dto.getNombreUsuario());
    }

    @Test
    void toUsuario_deberiaMapearDesdeDTO() {
        Usuario resultado = usuarioMapper.toUsuario(usuarioDTO);

        assertNotNull(resultado);
        assertEquals(usuarioDTO.getIdUsuario(), resultado.getIdUsuario());
        assertEquals(usuarioDTO.getNickname(), resultado.getNickname());
        assertEquals(usuarioDTO.getNombreUsuario(), resultado.getNombreUsuario());
        assertEquals(usuarioDTO.getApellidoUsuario(), resultado.getApellidoUsuario());
        assertEquals(usuarioDTO.getCorreoUsuario(), resultado.getCorreoUsuario());
        assertEquals(usuarioDTO.getContrasenhaUsuario(), resultado.getContrasenhaUsuario());
        // activo, igual que esPremium (ver el test de más abajo), lo ignora el
        // mapper a propósito -- no se toma del DTO de entrada del registro.
        assertNull(resultado.getActivo());
        assertNull(resultado.getEmailVerificado());
        assertNull(resultado.getCodigoVerificacion());
        // intentosVerificacion está @Builder.Default = 0 en Usuario; el mapper lo ignora,
        // así que al construir vía Usuario.builder() cae en ese valor por defecto (no null).
        assertEquals(0, resultado.getIntentosVerificacion());
    }

    @Test
    void toUsuario_IgnoraEsPremiumYActivoDelDTO_PrevencionMassAssignment() {
        // Regresión del hallazgo de la auditoría del 2026-09-02: UsuarioDTO expone
        // esPremium/activo como campos JSON normales (sin @JsonIgnore) porque
        // también se usan para DEVOLVER el perfil -- pero un cliente sin autenticar puede
        // mandarlos en el body de POST /api/v1/auth/register. Antes del fix, toUsuario()
        // los copiaba tal cual y Usuario.prePersist() solo aplica su default cuando el campo
        // llega null (nunca sobreescribe uno ya presente), así que un registro con
        // "esPremium": true se persistía como cuenta Premium real.
        UsuarioDTO dtoMalicioso = UsuarioDTO.builder()
                .nickname("Atacante")
                .nombreUsuario("Juan")
                .apellidoUsuario("Perez")
                .correoUsuario("atacante@example.com")
                .contrasenhaUsuario("P@ssw0rd!")
                .activo(true)
                .esPremium(true)
                .build();

        Usuario resultado = usuarioMapper.toUsuario(dtoMalicioso);

        assertNull(resultado.getActivo());
        // esPremium está @Builder.Default en Usuario (false) -- al ignorarlo el mapper, el
        // builder generado por MapStruct cae en ese valor por defecto en vez de en el que
        // mandó el cliente.
        assertEquals(false, resultado.getEsPremium());
    }
}
