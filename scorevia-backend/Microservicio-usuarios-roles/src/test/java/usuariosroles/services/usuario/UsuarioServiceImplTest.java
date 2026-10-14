package usuariosroles.services.usuario;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import usuariosroles.dto.UsuarioDTO;
import usuariosroles.dto.UsuarioRolDTO;
import usuariosroles.dto.UsuarioUpdateDTO;
import usuariosroles.entities.Rol;
import usuariosroles.entities.Usuario;
import usuariosroles.exception.*;
import usuariosroles.mappers.UsuarioMapper;
import usuariosroles.repositories.RolRepository;
import usuariosroles.repositories.UsuarioRepository;
import usuariosroles.services.jwt.JwtTokenService;
import usuariosroles.services.mail.MailService;
import usuariosroles.services.usuariorol.UsuarioRolServiceImpl;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceImplTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private UsuarioMapper usuarioMapper;
    @Mock
    private RolRepository rolRepository;
    @Mock
    private UsuarioRolServiceImpl usuarioRolService;
    @Mock
    private PasswordEncoder encoder;
    @Mock
    private MailService mailService;
    @Mock
    private GoogleIdTokenVerifier googleIdTokenVerifier;
    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private Authentication authentication;
    @Mock
    private SecurityContext securityContext;

    @InjectMocks
    private UsuarioServiceImpl usuarioService;

    private Usuario usuarioEntity;
    private UsuarioDTO usuarioDTO;
    private UsuarioUpdateDTO usuarioUpdateDTO;
    private Rol rolDefault;

    // FECHAS FIJAS PARA EVITAR EL REGLA SONAR [java:S8692] y [java:S8694]
    private static final LocalDateTime FECHA_FUTURA = LocalDateTime.of(2050, Month.JANUARY, 1, 12, 0);
    private static final LocalDateTime FECHA_PASADA = LocalDateTime.of(2000, Month.JANUARY, 1, 12, 0);

    @BeforeEach
    void setUp() {
        usuarioEntity = new Usuario();
        usuarioEntity.setIdUsuario(1);
        usuarioEntity.setCorreoUsuario("test@app.com");
        usuarioEntity.setContrasenhaUsuario("hashedPass");
        usuarioEntity.setNombreUsuario("Test");
        usuarioEntity.setActivo(true);
        usuarioEntity.setEmailVerificado(false);
        usuarioEntity.setIntentosVerificacion(0);
        usuarioEntity.setRoles(new HashSet<>());

        usuarioDTO = new UsuarioDTO();
        usuarioDTO.setIdUsuario(1);
        usuarioDTO.setCorreoUsuario("test@app.com");
        usuarioDTO.setContrasenhaUsuario("rawPass");
        usuarioDTO.setNombreUsuario("Test");

        usuarioUpdateDTO = new UsuarioUpdateDTO();
        usuarioUpdateDTO.setIdUsuario(1);
        usuarioUpdateDTO.setCorreoUsuario("test@app.com");
        usuarioUpdateDTO.setContrasenhaUsuario("rawPass");
        usuarioUpdateDTO.setNombreUsuario("Test");

        rolDefault = new Rol();
        rolDefault.setIdRol(1);
        rolDefault.setNombreRol("FREE");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // =========================================================================
    // CREAR USUARIO
    // =========================================================================
    @Test
    void crearUsuario_DeberiaCrearUsuarioYAsignarRolFree() {
        when(usuarioMapper.toUsuario(any(UsuarioDTO.class))).thenReturn(usuarioEntity);
        when(encoder.encode(anyString())).thenReturn("hashedPass");
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(usuarioEntity);
        when(rolRepository.findByNombreRolAndActivo("FREE", true)).thenReturn(Optional.of(rolDefault));
        when(usuarioMapper.toDtoPost(any(Usuario.class))).thenReturn(usuarioDTO);
        when(usuarioRolService.asignarRolUsuario(1, 1)).thenReturn(new UsuarioRolDTO());

        UsuarioDTO resultado = usuarioService.crearUsuario(usuarioDTO);

        assertNotNull(resultado);
        assertNotNull(usuarioEntity.getCodigoVerificacion());
        verify(usuarioRolService, times(1)).asignarRolUsuario(1, 1);
        verify(mailService, times(1)).enviarCodigoVerificacion(anyString(), anyString(), anyString());
    }

    @Test
    void crearUsuario_LanzaConflictoSiNoExisteRolDefault() {
        when(usuarioMapper.toUsuario(any(UsuarioDTO.class))).thenReturn(usuarioEntity);
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(usuarioEntity);
        when(rolRepository.findByNombreRolAndActivo("FREE", true)).thenReturn(Optional.empty());

        assertThrows(ConflictException.class, () -> usuarioService.crearUsuario(usuarioDTO));
    }

    // =========================================================================
    // VERIFICAR EMAIL (FUERZA BRUTA Y BLOQUEOS)
    // =========================================================================
    @Test
    void verificarEmail_LanzaNotFoundSiUsuarioNoExiste() {
        when(usuarioRepository.findByCorreoUsuario("fantasma@app.com")).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> usuarioService.verificarEmail("fantasma@app.com", "123456"));
    }

    @Test
    void verificarEmail_Exito() {
        usuarioEntity.setCodigoVerificacion("123456");
        usuarioEntity.setCodigoVerificacionExpira(FECHA_FUTURA);
        when(usuarioRepository.findByCorreoUsuario("test@app.com")).thenReturn(Optional.of(usuarioEntity));

        usuarioService.verificarEmail("test@app.com", "123456");

        assertTrue(usuarioEntity.getEmailVerificado());
        assertNull(usuarioEntity.getCodigoVerificacion());
        verify(usuarioRepository, times(1)).save(usuarioEntity);
    }

    @Test
    void verificarEmail_FallaSiYaEstaVerificado() {
        usuarioEntity.setEmailVerificado(true);
        when(usuarioRepository.findByCorreoUsuario("test@app.com")).thenReturn(Optional.of(usuarioEntity));

        assertThrows(BadRequestException.class, () -> usuarioService.verificarEmail("test@app.com", "123456"));
    }

    @Test
    void verificarEmail_FallaSiEstaBloqueadoEnFuturo() {
        usuarioEntity.setBloqueadoVerificacionHasta(FECHA_FUTURA);
        when(usuarioRepository.findByCorreoUsuario("test@app.com")).thenReturn(Optional.of(usuarioEntity));

        assertThrows(ForbiddenException.class, () -> usuarioService.verificarEmail("test@app.com", "123456"));
    }

    @Test
    void verificarEmail_DesbloqueaSiElTiempoYaPaso() {
        usuarioEntity.setBloqueadoVerificacionHasta(FECHA_PASADA); // El castigo ya caducó
        usuarioEntity.setCodigoVerificacion("123456");
        usuarioEntity.setCodigoVerificacionExpira(FECHA_FUTURA);
        when(usuarioRepository.findByCorreoUsuario("test@app.com")).thenReturn(Optional.of(usuarioEntity));

        usuarioService.verificarEmail("test@app.com", "123456");

        assertNull(usuarioEntity.getBloqueadoVerificacionHasta());
        assertTrue(usuarioEntity.getEmailVerificado());
    }

    @Test
    void verificarEmail_LanzaBadRequest_CodigoNullEnBD() {
        usuarioEntity.setCodigoVerificacion(null);
        usuarioEntity.setCodigoVerificacionExpira(FECHA_FUTURA);
        when(usuarioRepository.findByCorreoUsuario("test@app.com")).thenReturn(Optional.of(usuarioEntity));

        assertThrows(BadRequestException.class, () -> usuarioService.verificarEmail("test@app.com", "123456"));
    }

    @Test
    void verificarEmail_FallaSiCodigoNoCoincide() {
        usuarioEntity.setCodigoVerificacion("123456");
        usuarioEntity.setCodigoVerificacionExpira(FECHA_FUTURA);
        when(usuarioRepository.findByCorreoUsuario("test@app.com")).thenReturn(Optional.of(usuarioEntity));

        assertThrows(BadRequestException.class, () -> usuarioService.verificarEmail("test@app.com", "000000"));
        assertEquals(1, usuarioEntity.getIntentosVerificacion()); // Registra el fallo
    }

    @Test
    void verificarEmail_RegistraIntentoFallido_CuandoIntentosEsNull() {
        usuarioEntity.setCodigoVerificacion("123456");
        usuarioEntity.setCodigoVerificacionExpira(FECHA_FUTURA);
        usuarioEntity.setIntentosVerificacion(null); // Probamos el null-safe
        when(usuarioRepository.findByCorreoUsuario("test@app.com")).thenReturn(Optional.of(usuarioEntity));

        assertThrows(BadRequestException.class, () -> usuarioService.verificarEmail("test@app.com", "000000"));
        assertEquals(1, usuarioEntity.getIntentosVerificacion());
    }

    @Test
    void verificarEmail_BloqueaCuentaAlSuperarMaxIntentos() {
        usuarioEntity.setCodigoVerificacion("123456");
        usuarioEntity.setCodigoVerificacionExpira(FECHA_FUTURA);
        usuarioEntity.setIntentosVerificacion(4);
        when(usuarioRepository.findByCorreoUsuario("test@app.com")).thenReturn(Optional.of(usuarioEntity));

        assertThrows(ForbiddenException.class, () -> usuarioService.verificarEmail("test@app.com", "000000"));
        assertNotNull(usuarioEntity.getBloqueadoVerificacionHasta());
        assertEquals(0, usuarioEntity.getIntentosVerificacion());
    }

    @Test
    void verificarEmail_FallaSiCodigoExpiraEsNull() {
        usuarioEntity.setCodigoVerificacion("123456");
        usuarioEntity.setCodigoVerificacionExpira(null);
        when(usuarioRepository.findByCorreoUsuario("test@app.com")).thenReturn(Optional.of(usuarioEntity));

        assertThrows(BadRequestException.class, () -> usuarioService.verificarEmail("test@app.com", "123456"));
    }

    @Test
    void verificarEmail_FallaSiCodigoExpiro() {
        usuarioEntity.setCodigoVerificacion("123456");
        usuarioEntity.setCodigoVerificacionExpira(FECHA_PASADA); // Ya caducó
        when(usuarioRepository.findByCorreoUsuario("test@app.com")).thenReturn(Optional.of(usuarioEntity));

        assertThrows(BadRequestException.class, () -> usuarioService.verificarEmail("test@app.com", "123456"));
    }

    // =========================================================================
    // RESET DE CONTRASEÑA
    // =========================================================================
    @Test
    void solicitarResetPassword_UsuarioNoExiste_NoHaceNadaNiLanza() {
        when(usuarioRepository.findByCorreoUsuario("fantasma@app.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> usuarioService.solicitarResetPassword("fantasma@app.com"));

        verify(usuarioRepository, never()).save(any());
        verifyNoInteractions(mailService);
    }

    @Test
    void solicitarResetPassword_UsuarioInactivo_NoHaceNadaNiLanza() {
        usuarioEntity.setActivo(false);
        when(usuarioRepository.findByCorreoUsuario("test@app.com")).thenReturn(Optional.of(usuarioEntity));

        assertDoesNotThrow(() -> usuarioService.solicitarResetPassword("test@app.com"));

        verify(usuarioRepository, never()).save(any());
        verifyNoInteractions(mailService);
    }

    @Test
    void solicitarResetPassword_UsuarioActivo_GeneraCodigoYEnviaCorreo() {
        when(usuarioRepository.findByCorreoUsuario("test@app.com")).thenReturn(Optional.of(usuarioEntity));

        usuarioService.solicitarResetPassword("test@app.com");

        assertNotNull(usuarioEntity.getCodigoResetPassword());
        assertNotNull(usuarioEntity.getCodigoResetPasswordExpira());
        verify(usuarioRepository).save(usuarioEntity);
        verify(mailService).enviarCodigoResetPassword(eq("test@app.com"), eq("Test"), anyString());
    }

    @Test
    void resetPassword_LanzaBadRequestSiUsuarioNoExiste() {
        // BadRequestException (no NotFoundException): un correo desconocido debe dar la misma
        // respuesta que un código incorrecto sobre un correo real, para no permitir enumerar
        // cuentas registradas por status code (ver comentario en UsuarioServiceImpl).
        when(usuarioRepository.findByCorreoUsuario("fantasma@app.com")).thenReturn(Optional.empty());
        assertThrows(BadRequestException.class, () -> usuarioService.resetPassword("fantasma@app.com", "123456", "N3wP@ss!!"));
    }

    @Test
    void resetPassword_Exito_CambiaContrasenhaYRevocaSesiones() {
        usuarioEntity.setCodigoResetPassword("123456");
        usuarioEntity.setCodigoResetPasswordExpira(FECHA_FUTURA);
        when(usuarioRepository.findByCorreoUsuario("test@app.com")).thenReturn(Optional.of(usuarioEntity));
        when(encoder.encode("N3wP@ss!!")).thenReturn("hashedNewPass");

        usuarioService.resetPassword("test@app.com", "123456", "N3wP@ss!!");

        assertEquals("hashedNewPass", usuarioEntity.getContrasenhaUsuario());
        assertNull(usuarioEntity.getCodigoResetPassword());
        assertNull(usuarioEntity.getCodigoResetPasswordExpira());
        verify(usuarioRepository).save(usuarioEntity);
        verify(jwtTokenService).revocarTodasLasSesiones(1);
    }

    @Test
    void resetPassword_FallaSiEstaBloqueadoEnFuturo() {
        usuarioEntity.setBloqueadoResetPasswordHasta(FECHA_FUTURA);
        when(usuarioRepository.findByCorreoUsuario("test@app.com")).thenReturn(Optional.of(usuarioEntity));

        assertThrows(ForbiddenException.class, () -> usuarioService.resetPassword("test@app.com", "123456", "N3wP@ss!!"));
        verifyNoInteractions(jwtTokenService);
    }

    @Test
    void resetPassword_DesbloqueaSiElTiempoYaPaso() {
        usuarioEntity.setBloqueadoResetPasswordHasta(FECHA_PASADA);
        usuarioEntity.setCodigoResetPassword("123456");
        usuarioEntity.setCodigoResetPasswordExpira(FECHA_FUTURA);
        when(usuarioRepository.findByCorreoUsuario("test@app.com")).thenReturn(Optional.of(usuarioEntity));

        usuarioService.resetPassword("test@app.com", "123456", "N3wP@ss!!");

        assertNull(usuarioEntity.getBloqueadoResetPasswordHasta());
        verify(jwtTokenService).revocarTodasLasSesiones(1);
    }

    @Test
    void resetPassword_FallaSiCodigoNoCoincide() {
        usuarioEntity.setCodigoResetPassword("123456");
        usuarioEntity.setCodigoResetPasswordExpira(FECHA_FUTURA);
        when(usuarioRepository.findByCorreoUsuario("test@app.com")).thenReturn(Optional.of(usuarioEntity));

        assertThrows(BadRequestException.class, () -> usuarioService.resetPassword("test@app.com", "000000", "N3wP@ss!!"));
        assertEquals(1, usuarioEntity.getIntentosResetPassword());
        verifyNoInteractions(jwtTokenService);
    }

    @Test
    void resetPassword_BloqueaCuentaAlSuperarMaxIntentos() {
        usuarioEntity.setCodigoResetPassword("123456");
        usuarioEntity.setCodigoResetPasswordExpira(FECHA_FUTURA);
        usuarioEntity.setIntentosResetPassword(4);
        when(usuarioRepository.findByCorreoUsuario("test@app.com")).thenReturn(Optional.of(usuarioEntity));

        assertThrows(ForbiddenException.class, () -> usuarioService.resetPassword("test@app.com", "000000", "N3wP@ss!!"));
        assertNotNull(usuarioEntity.getBloqueadoResetPasswordHasta());
        assertEquals(0, usuarioEntity.getIntentosResetPassword());
        // El código queda invalidado tras el bloqueo -- a diferencia de la verificación de
        // email, aquí no se reenvía uno nuevo automáticamente: ya existe un endpoint dedicado
        // (solicitarResetPassword) para pedir uno nuevo explícitamente.
        assertNull(usuarioEntity.getCodigoResetPassword());
    }

    @Test
    void resetPassword_FallaSiCodigoExpiro() {
        usuarioEntity.setCodigoResetPassword("123456");
        usuarioEntity.setCodigoResetPasswordExpira(FECHA_PASADA);
        when(usuarioRepository.findByCorreoUsuario("test@app.com")).thenReturn(Optional.of(usuarioEntity));

        assertThrows(BadRequestException.class, () -> usuarioService.resetPassword("test@app.com", "123456", "N3wP@ss!!"));
        verifyNoInteractions(jwtTokenService);
    }

    // =========================================================================
    // OBTENER USUARIO (SEGURIDAD Y ROLES)
    // =========================================================================
    @Test
    void getUsuarioById_RetornaNullSiNoExiste() {
        when(usuarioRepository.findByIdUsuarioAndActivo(99, true)).thenReturn(Optional.empty());
        assertNull(usuarioService.getUsuarioById(99));
    }

    @Test
    void getUsuarioById_LanzaBadRequestSiAuthEsNull() {
        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.of(usuarioEntity));
        SecurityContextHolder.getContext().setAuthentication(null);

        assertThrows(BadRequestException.class, () -> usuarioService.getUsuarioById(1));
    }

    @ParameterizedTest
    @CsvSource({
            "admin@app.com, ROLE_ADMIN",
            "admin@app.com, ADMIN",
            "test@app.com, ROLE_USER"
    })
    void getUsuarioById_ExitoPermisosValidos(String correoAuth, String rolAuth) {
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn(correoAuth);
        doReturn(List.of(new SimpleGrantedAuthority(rolAuth))).when(authentication).getAuthorities();

        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.of(usuarioEntity));
        when(usuarioMapper.toDtoGetById(any(Usuario.class))).thenReturn(usuarioDTO);

        UsuarioDTO resultado = usuarioService.getUsuarioById(1);
        assertNotNull(resultado);
    }

    @Test
    void getUsuarioById_FallaPorFaltaDePermisos() {
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("otro@app.com");
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_USER"))).when(authentication).getAuthorities();

        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.of(usuarioEntity));

        assertThrows(ForbiddenException.class, () -> usuarioService.getUsuarioById(1));
    }

    // =========================================================================
    // MODIFICAR USUARIO (PUT)
    // =========================================================================
    @Test
    void putUsuarioDTO_NoExiste() {
        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> usuarioService.putUsuarioDTO(usuarioUpdateDTO));
    }

    @Test
    void putUsuarioDTO_MismoCorreoQueElPropioId_NoLanzaConflicto() {
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("test@app.com");
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_USER"))).when(authentication).getAuthorities();

        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.of(usuarioEntity));
        when(usuarioRepository.findByCorreoUsuario("test@app.com")).thenReturn(Optional.of(usuarioEntity));

        usuarioUpdateDTO.setCorreoUsuario("test@app.com");
        usuarioUpdateDTO.setContrasenhaUsuario("rawPass");

        // Al editarse a sí mismo se procesa la codificación de forma normal
        when(encoder.encode("rawPass")).thenReturn("encoded");
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(usuarioEntity);
        when(usuarioMapper.toDtoPut(any(Usuario.class))).thenReturn(usuarioDTO);

        assertNotNull(usuarioService.putUsuarioDTO(usuarioUpdateDTO));
    }

    @Test
    void putUsuarioDTO_OmiteCorreoYPassword_Exito() {
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("test@app.com");
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_USER"))).when(authentication).getAuthorities();

        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.of(usuarioEntity));
        when(usuarioRepository.findByCorreoUsuario(null)).thenReturn(Optional.empty());

        usuarioUpdateDTO.setCorreoUsuario(null);
        usuarioUpdateDTO.setContrasenhaUsuario(null);

        when(usuarioRepository.save(any(Usuario.class))).thenReturn(usuarioEntity);
        when(usuarioMapper.toDtoPut(any(Usuario.class))).thenReturn(usuarioDTO);

        assertNotNull(usuarioService.putUsuarioDTO(usuarioUpdateDTO));
    }

    @Test
    void putUsuarioDTO_FallaSiCorreoExisteEnOtroID() {
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("test@app.com");
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_USER"))).when(authentication).getAuthorities();

        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.of(usuarioEntity));
        Usuario otroUsuario = new Usuario();
        otroUsuario.setIdUsuario(2); // Diferente ID
        when(usuarioRepository.findByCorreoUsuario("test@app.com")).thenReturn(Optional.of(otroUsuario));

        assertThrows(BadRequestException.class, () -> usuarioService.putUsuarioDTO(usuarioUpdateDTO));
    }

    @Test
    void putUsuarioDTO_LanzaBadRequestSiAuthEsNull() {
        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.of(usuarioEntity));
        SecurityContextHolder.getContext().setAuthentication(null);

        assertThrows(BadRequestException.class, () -> usuarioService.putUsuarioDTO(usuarioUpdateDTO));
    }

    @Test
    void putUsuarioDTO_FallaSiNoAdminEditaOtroUsuario() {
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("pepe@app.com");
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_USER"))).when(authentication).getAuthorities();

        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.of(usuarioEntity));

        assertThrows(ForbiddenException.class, () -> usuarioService.putUsuarioDTO(usuarioUpdateDTO));
    }

    @Test
    void putUsuarioDTO_FallaSiAdminIntentaCambiarCorreoDeOtro() {
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("admin@app.com");
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))).when(authentication).getAuthorities();

        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.of(usuarioEntity));

        usuarioUpdateDTO.setCorreoUsuario("nuevo@app.com");

        assertThrows(ForbiddenException.class, () -> usuarioService.putUsuarioDTO(usuarioUpdateDTO));
    }

    @Test
    void putUsuarioDTO_FallaSiAdminIntentaCambiarPasswordDeOtro() {
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("admin@app.com");
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))).when(authentication).getAuthorities();

        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.of(usuarioEntity));

        usuarioUpdateDTO.setContrasenhaUsuario("passNueva");
        when(encoder.matches("passNueva", "hashedPass")).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> usuarioService.putUsuarioDTO(usuarioUpdateDTO));
    }

    @Test
    void putUsuarioDTO_AdminCambiaSoloNombreExito() {
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("admin@app.com");
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))).when(authentication).getAuthorities();

        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.of(usuarioEntity));
        when(usuarioRepository.findByCorreoUsuario("test@app.com")).thenReturn(Optional.of(usuarioEntity));

        usuarioUpdateDTO.setNombreUsuario("Editado por Admin");
        usuarioUpdateDTO.setContrasenhaUsuario("rawPass");
        when(encoder.matches("rawPass", "hashedPass")).thenReturn(true);
        when(encoder.encode("rawPass")).thenReturn("encoded");

        when(usuarioRepository.save(any(Usuario.class))).thenReturn(usuarioEntity);
        when(usuarioMapper.toDtoPut(any(Usuario.class))).thenReturn(usuarioDTO);

        UsuarioDTO resultado = usuarioService.putUsuarioDTO(usuarioUpdateDTO);
        assertNotNull(resultado);
    }

    @Test
    void putUsuarioDTO_UsuarioMismoCambiaCorreoYContrasena() {
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("test@app.com");
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_USER"))).when(authentication).getAuthorities();

        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.of(usuarioEntity));
        when(usuarioRepository.findByCorreoUsuario("nuevo@app.com")).thenReturn(Optional.empty());

        usuarioUpdateDTO.setCorreoUsuario("nuevo@app.com");
        usuarioUpdateDTO.setContrasenhaUsuario("nuevaPass");
        when(encoder.encode("nuevaPass")).thenReturn("encoded");
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(usuarioEntity);
        when(usuarioMapper.toDtoPut(any(Usuario.class))).thenReturn(usuarioDTO);

        UsuarioDTO resultado = usuarioService.putUsuarioDTO(usuarioUpdateDTO);

        assertNotNull(resultado);
        verify(mailService, times(1)).enviarCodigoVerificacion(anyString(), anyString(), anyString());
    }

    // =========================================================================
    // ACTIVAR USUARIO
    // =========================================================================
    @Test
    void activarUsuarioDTO_RetornaNullSiNoExiste() {
        when(usuarioRepository.findByCorreoUsuario(anyString())).thenReturn(Optional.empty());
        assertNull(usuarioService.activarUsuarioDTO(usuarioDTO));
    }

    @Test
    void activarUsuarioDTO_LanzaConflictoSiNoExisteRolDefault() {
        when(usuarioRepository.findByCorreoUsuario(anyString())).thenReturn(Optional.of(usuarioEntity));
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(usuarioEntity);
        when(rolRepository.findByNombreRolAndActivo("FREE", true)).thenReturn(Optional.empty());

        assertThrows(ConflictException.class, () -> usuarioService.activarUsuarioDTO(usuarioDTO));
    }

    @Test
    void activarUsuarioDTO_Exito() {
        when(usuarioRepository.findByCorreoUsuario("test@app.com")).thenReturn(Optional.of(usuarioEntity));
        when(encoder.encode(anyString())).thenReturn("hashed");
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(usuarioEntity);
        when(rolRepository.findByNombreRolAndActivo("FREE", true)).thenReturn(Optional.of(rolDefault));
        when(usuarioMapper.toDtoPost(any(Usuario.class))).thenReturn(usuarioDTO);
        when(usuarioRolService.asignarRolUsuario(1, 1)).thenReturn(new UsuarioRolDTO());

        UsuarioDTO resultado = usuarioService.activarUsuarioDTO(usuarioDTO);

        assertNotNull(resultado);
        verify(usuarioRolService, times(1)).asignarRolUsuario(1, 1);
        verify(mailService, times(1)).enviarCodigoVerificacion(anyString(), anyString(), anyString());
    }

    // =========================================================================
    // BORRADO LÓGICO (GDPR)
    // =========================================================================
    @Test
    void deleteUsuarioDTO_RetornaNullSiNoExiste() {
        when(usuarioRepository.findByIdUsuarioAndActivo(99, true)).thenReturn(Optional.empty());
        assertNull(usuarioService.deleteUsuarioDTO(99));
    }

    @Test
    void deleteUsuarioDTO_LanzaBadRequestSiAuthEsNull() {
        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.of(usuarioEntity));
        SecurityContextHolder.getContext().setAuthentication(null);
        assertThrows(BadRequestException.class, () -> usuarioService.deleteUsuarioDTO(1));
    }

    @Test
    void deleteUsuarioDTO_LanzaForbiddenSiNoEsAdminNiElMismo() {
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("otro@app.com");
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_USER"))).when(authentication).getAuthorities();

        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.of(usuarioEntity));

        assertThrows(ForbiddenException.class, () -> usuarioService.deleteUsuarioDTO(1));
    }

    @Test
    void deleteUsuarioDTO_AdminBorrandoAOtro_ConRoles_NoLimpiaContexto() {
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("admin@app.com");
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))).when(authentication).getAuthorities();

        usuarioEntity.getRoles().add(rolDefault);
        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.of(usuarioEntity));
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(usuarioEntity);
        when(usuarioMapper.toDtoDelete(any(Usuario.class))).thenReturn(usuarioDTO);

        UsuarioDTO resultado = usuarioService.deleteUsuarioDTO(1);

        assertNotNull(resultado);
        verify(usuarioRolService, times(1)).quitarRolUsuario(1, 1);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void deleteUsuarioDTO_AdminBorrandoASiMismo_LimpiaContexto() {
        // Regresión: la comparación de auto-eliminación debe capturarse ANTES de anonimizar
        // el correo (que se sobrescribe a "deleted_<id>@scorevia.local"); si se comparase
        // después, un admin borrándose a sí mismo nunca coincidiría y su sesión seguiría viva.
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("test@app.com"); // Mismo correo que usuarioEntity
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))).when(authentication).getAuthorities();

        usuarioEntity.setRoles(new HashSet<>());
        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.of(usuarioEntity));
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(usuarioEntity);
        when(usuarioMapper.toDtoDelete(any(Usuario.class))).thenReturn(usuarioDTO);

        usuarioService.deleteUsuarioDTO(1);

        assertTrue(usuarioEntity.getCorreoUsuario().startsWith("deleted_"));
        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "Un admin que se borra a sí mismo debe perder también su sesión");
    }

    @Test
    void deleteUsuarioDTO_UsuarioBorrandoASiMismo_SinRoles_LimpiaContexto() {
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("test@app.com");
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_USER"))).when(authentication).getAuthorities();

        usuarioEntity.setRoles(new HashSet<>());
        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.of(usuarioEntity));
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(usuarioEntity);
        when(usuarioMapper.toDtoDelete(any(Usuario.class))).thenReturn(usuarioDTO);

        usuarioService.deleteUsuarioDTO(1);

        assertFalse(usuarioEntity.getActivo());
        assertTrue(usuarioEntity.getCorreoUsuario().startsWith("deleted_"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void obtenerNicknames_DevuelveMapaIdNickname() {
        Usuario otroUsuario = new Usuario();
        otroUsuario.setIdUsuario(2);
        otroUsuario.setNickname("goleador10");
        usuarioEntity.setIdUsuario(1);
        usuarioEntity.setNickname("capitanFutbol");

        when(usuarioRepository.findAllById(List.of(1, 2))).thenReturn(List.of(usuarioEntity, otroUsuario));

        Map<Integer, String> resultado = usuarioService.obtenerNicknames(List.of(1, 2));

        assertEquals("capitanFutbol", resultado.get(1));
        assertEquals("goleador10", resultado.get(2));
    }

    @Test
    void obtenerNicknames_IdsInexistentes_NoAparecenEnElMapa() {
        when(usuarioRepository.findAllById(List.of(99))).thenReturn(List.of());

        Map<Integer, String> resultado = usuarioService.obtenerNicknames(List.of(99));

        assertTrue(resultado.isEmpty());
    }

    @Test
    void existeUsuario_Existe_DevuelveTrue() {
        when(usuarioRepository.existsById(1)).thenReturn(true);
        assertTrue(usuarioService.existeUsuario(1));
    }

    @Test
    void existeUsuario_NoExiste_DevuelveFalse() {
        when(usuarioRepository.existsById(99)).thenReturn(false);
        assertFalse(usuarioService.existeUsuario(99));
    }

    @Test
    void usuarioActivo_Activo_DevuelveTrue() {
        when(usuarioRepository.existsByIdUsuarioAndActivo(1, true)).thenReturn(true);
        assertTrue(usuarioService.usuarioActivo(1));
    }

    @Test
    void usuarioActivo_NoExisteONoActivo_DevuelveFalse() {
        when(usuarioRepository.existsByIdUsuarioAndActivo(99, true)).thenReturn(false);
        assertFalse(usuarioService.usuarioActivo(99));
    }

    // =========================================================================
    // LISTADOS PAGINADOS Y EXISTS
    // =========================================================================
    @Test
    void getUsuarios_LanzaNoContentSiEstaVacio() {
        when(usuarioRepository.findAllByActivo(eq(true), any())).thenReturn(new PageImpl<>(new ArrayList<>()));
        PageRequest page = PageRequest.of(0, 10);
        assertThrows(NoContentException.class, () -> usuarioService.getUsuarios(page));
    }

    @Test
    void getUsuarios_Exito() {
        Page<Usuario> pageData = new PageImpl<>(List.of(usuarioEntity));
        when(usuarioRepository.findAllByActivo(eq(true), any())).thenReturn(pageData);
        when(usuarioMapper.toDtoGetAll(any(Usuario.class))).thenReturn(usuarioDTO);

        Page<UsuarioDTO> result = usuarioService.getUsuarios(PageRequest.of(0, 10));
        assertFalse(result.isEmpty());
    }

    @Test
    void contarUsuarios_DelegaEnElRepositorio() {
        when(usuarioRepository.count()).thenReturn(42L);
        assertEquals(42L, usuarioService.contarUsuarios());
    }

    @Test
    void exportarTodosStreaming_UnSoloLote_EmiteTodosLosUsuariosEnOrden() {
        Usuario u1 = new Usuario();
        u1.setIdUsuario(1);
        Usuario u2 = new Usuario();
        u2.setIdUsuario(2);

        when(usuarioRepository.findByIdUsuarioGreaterThanOrderByIdUsuarioAsc(eq(0), any(PageRequest.class)))
                .thenReturn(List.of(u1, u2));
        when(usuarioMapper.toDtoExcel(u1)).thenReturn(usuarioDTO);
        when(usuarioMapper.toDtoExcel(u2)).thenReturn(usuarioDTO);

        List<UsuarioDTO> emitidos = new ArrayList<>();
        usuarioService.exportarTodosStreaming(emitidos::add);

        assertEquals(2, emitidos.size());
        // Un solo lote (2 < TAMANIO_LOTE_EXPORT): no debe pedir un segundo lote.
        verify(usuarioRepository, times(1))
                .findByIdUsuarioGreaterThanOrderByIdUsuarioAsc(anyInt(), any(PageRequest.class));
    }

    @Test
    void exportarTodosStreaming_VariosLotes_AvanzaPorKeysetHastaAgotarse() {
        int tamanioLote = 1000;
        List<Usuario> loteLleno = new ArrayList<>();
        for (int i = 1; i <= tamanioLote; i++) {
            Usuario u = new Usuario();
            u.setIdUsuario(i);
            loteLleno.add(u);
        }
        Usuario ultimoUsuario = new Usuario();
        ultimoUsuario.setIdUsuario(tamanioLote + 1);

        when(usuarioRepository.findByIdUsuarioGreaterThanOrderByIdUsuarioAsc(eq(0), any(PageRequest.class)))
                .thenReturn(loteLleno);
        when(usuarioRepository.findByIdUsuarioGreaterThanOrderByIdUsuarioAsc(eq(tamanioLote), any(PageRequest.class)))
                .thenReturn(List.of(ultimoUsuario));
        when(usuarioMapper.toDtoExcel(any(Usuario.class))).thenReturn(usuarioDTO);

        List<UsuarioDTO> emitidos = new ArrayList<>();
        usuarioService.exportarTodosStreaming(emitidos::add);

        assertEquals(tamanioLote + 1, emitidos.size());
        verify(usuarioRepository, times(2))
                .findByIdUsuarioGreaterThanOrderByIdUsuarioAsc(anyInt(), any(PageRequest.class));
    }

    @Test
    void existsUsuario_True() {
        when(usuarioRepository.existsByCorreoUsuario("test@app.com")).thenReturn(true);
        assertTrue(usuarioService.existsUsuario("test@app.com"));
    }

    @Test
    void existsUsuarioDesactivado_True() {
        when(usuarioRepository.existsByCorreoUsuarioAndActivo("test@app.com", false)).thenReturn(true);
        assertTrue(usuarioService.existsUsuarioDesactivado("test@app.com"));
    }

    // =========================================================================
    // LOGIN GOOGLE
    // =========================================================================
    @Test
    void procesarLoginGoogle_LanzaBadRequestSiTokenEsInvalido() throws Exception {
        when(googleIdTokenVerifier.verify(anyString())).thenReturn(null);
        assertThrows(BadRequestException.class, () -> usuarioService.procesarLoginGoogle("bad-token"));
    }

    @Test
    void procesarLoginGoogle_LanzaBadRequestSiEmailNoVerificado() throws Exception {
        // Regresión: un token con firma válida pero email_verified=false no debe bastar para
        // entrar a una cuenta existente (ver comentario en procesarLoginGoogle).
        GoogleIdToken idTokenMock = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payloadMock = new GoogleIdToken.Payload();
        payloadMock.setEmail("test@app.com");
        payloadMock.setEmailVerified(false);
        when(idTokenMock.getPayload()).thenReturn(payloadMock);
        when(googleIdTokenVerifier.verify(anyString())).thenReturn(idTokenMock);

        assertThrows(BadRequestException.class, () -> usuarioService.procesarLoginGoogle("valid-token"));
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    void procesarLoginGoogle_LanzaBadRequestSiEmailVerifiedAusente() throws Exception {
        // google-api-client devuelve null (no false) cuando el claim "email_verified" está
        // ausente por completo -- Boolean.TRUE.equals(null) también debe rechazar, no solo el
        // caso false.
        GoogleIdToken idTokenMock = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payloadMock = new GoogleIdToken.Payload();
        payloadMock.setEmail("test@app.com");
        when(idTokenMock.getPayload()).thenReturn(payloadMock);
        when(googleIdTokenVerifier.verify(anyString())).thenReturn(idTokenMock);

        assertThrows(BadRequestException.class, () -> usuarioService.procesarLoginGoogle("valid-token"));
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    void procesarLoginGoogle_LanzaConflictSiUsuarioEstaDesactivadoOParaNull() throws Exception {
        GoogleIdToken idTokenMock = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payloadMock = new GoogleIdToken.Payload();
        payloadMock.setEmail("test@app.com");
        payloadMock.setEmailVerified(true);
        when(idTokenMock.getPayload()).thenReturn(payloadMock);
        when(googleIdTokenVerifier.verify(anyString())).thenReturn(idTokenMock);

        usuarioEntity.setActivo(false);
        when(usuarioRepository.findByCorreoUsuario("test@app.com")).thenReturn(Optional.of(usuarioEntity));
        assertThrows(ConflictException.class, () -> usuarioService.procesarLoginGoogle("valid-token"));
    }

    @Test
    void procesarLoginGoogle_UsuarioActivoNull_Exito() throws Exception {
        GoogleIdToken idTokenMock = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payloadMock = new GoogleIdToken.Payload();
        payloadMock.setEmail("test@app.com");
        payloadMock.setEmailVerified(true);
        when(idTokenMock.getPayload()).thenReturn(payloadMock);
        when(googleIdTokenVerifier.verify(anyString())).thenReturn(idTokenMock);

        usuarioEntity.setActivo(null);
        usuarioEntity.setEmailVerificado(true);
        when(usuarioRepository.findByCorreoUsuario("test@app.com")).thenReturn(Optional.of(usuarioEntity));

        Usuario resultado = usuarioService.procesarLoginGoogle("valid-token");
        assertNotNull(resultado);
    }

    @Test
    void procesarLoginGoogle_ExitoVerificaEmailSiEstabaEnFalseONull() throws Exception {
        GoogleIdToken idTokenMock = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payloadMock = new GoogleIdToken.Payload();
        payloadMock.setEmail("test@app.com");
        payloadMock.setEmailVerified(true);
        when(idTokenMock.getPayload()).thenReturn(payloadMock);
        when(googleIdTokenVerifier.verify(anyString())).thenReturn(idTokenMock);

        usuarioEntity.setEmailVerificado(null);
        when(usuarioRepository.findByCorreoUsuario("test@app.com")).thenReturn(Optional.of(usuarioEntity));

        Usuario resultado = usuarioService.procesarLoginGoogle("valid-token");

        assertNotNull(resultado);
        assertTrue(resultado.getEmailVerificado());
        verify(usuarioRepository, times(1)).save(usuarioEntity);
    }

    @Test
    void procesarLoginGoogle_ExitoConUsuarioActivoYVerificado() throws Exception {
        GoogleIdToken idTokenMock = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payloadMock = new GoogleIdToken.Payload();
        payloadMock.setEmail("test@app.com");
        payloadMock.setEmailVerified(true);
        when(idTokenMock.getPayload()).thenReturn(payloadMock);
        when(googleIdTokenVerifier.verify(anyString())).thenReturn(idTokenMock);

        usuarioEntity.setEmailVerificado(true);
        when(usuarioRepository.findByCorreoUsuario("test@app.com")).thenReturn(Optional.of(usuarioEntity));

        Usuario resultado = usuarioService.procesarLoginGoogle("valid-token");

        assertNotNull(resultado);
        verify(usuarioRepository, never()).save(usuarioEntity);
    }

    @Test
    void procesarLoginGoogle_ExitoCreandoUsuarioNuevo_ConNameNull() throws Exception {
        GoogleIdToken idTokenMock = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payloadMock = new GoogleIdToken.Payload();
        payloadMock.setEmail("nuevo@google.com");
        payloadMock.setEmailVerified(true);
        payloadMock.set("name", null);
        when(idTokenMock.getPayload()).thenReturn(payloadMock);
        when(googleIdTokenVerifier.verify(anyString())).thenReturn(idTokenMock);

        when(usuarioRepository.findByCorreoUsuario("nuevo@google.com")).thenReturn(Optional.empty());
        when(encoder.encode(anyString())).thenReturn("hashed-pass");

        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> {
            Usuario u = i.getArgument(0);
            u.setIdUsuario(99);
            return u;
        });
        when(rolRepository.findByNombreRolAndActivo("FREE", true)).thenReturn(Optional.of(rolDefault));
        when(usuarioRolService.asignarRolUsuario(any(), any())).thenReturn(new UsuarioRolDTO());

        Usuario resultado = usuarioService.procesarLoginGoogle("valid-token");

        assertNotNull(resultado);
        assertEquals("nuevo@google.com", resultado.getCorreoUsuario());
        assertEquals("Usuario Google", resultado.getNombreUsuario());
        verify(usuarioRolService, times(1)).asignarRolUsuario(any(), any());
    }

    @Test
    void procesarLoginGoogle_CapturaExceptionYDevuelveBadRequest() throws Exception {
        when(googleIdTokenVerifier.verify(anyString())).thenThrow(new RuntimeException("Google API falló"));
        assertThrows(BadRequestException.class, () -> usuarioService.procesarLoginGoogle("token"));
    }
}