package usuariosroles.services.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import usuariosroles.exception.ForbiddenException;
import usuariosroles.exception.InvalidTokenTypeException;
import usuariosroles.repositories.RefreshTokenRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtTokenServiceImplTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private JwtTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new JwtTokenServiceImpl(refreshTokenRepository);
    }

    @Test
    void generarYValidarAccessToken_Exito() {
        List<GrantedAuthority> roles = List.of(new SimpleGrantedAuthority("ROLE_USER"));

        String token = service.generarAccessToken(1, "test@test.com", roles);
        assertNotNull(token);

        Claims claims = service.validarTokenExtraerClaims(token, "ACCESS");

        assertEquals("test@test.com", claims.get("correo"));
        assertEquals(1, claims.get("idUsuario"));
        assertEquals("ACCESS", claims.get("token_type"));
    }

    @Test
    void generarYValidarRefreshToken_Exito() {
        String token = service.generarRefreshToken(1, "test@test.com");
        assertNotNull(token);

        Claims claims = service.validarTokenExtraerClaims(token, "REFRESH");

        assertEquals("test@test.com", claims.get("correo"));
        assertEquals(1, claims.get("idUsuario"));
        assertEquals("REFRESH", claims.get("token_type"));
        assertNotNull(claims.getId(), "El refresh token debe llevar jti para poder rotarse/revocarse");
        verify(refreshTokenRepository).save(any());
    }

    @Test
    void validarTokenExtraerClaims_TipoIncorrecto_LanzaInvalidTokenType() {
        String token = service.generarRefreshToken(1, "test@test.com");

        assertThrows(InvalidTokenTypeException.class, () ->
                service.validarTokenExtraerClaims(token, "ACCESS")
        );
    }

    @Test
    void validarTokenExtraerClaims_TokenInvalido_LanzaJwtException() {
        assertThrows(JwtException.class, () ->
                service.validarTokenExtraerClaims("token.falso.inventado.y.mal.formado", "ACCESS")
        );
    }

    @Test
    void verificarYRotarRefreshToken_Activo_LoRevocaSinLanzar() {
        String token = service.generarRefreshToken(1, "test@test.com");
        Claims claims = service.validarTokenExtraerClaims(token, "REFRESH");

        when(refreshTokenRepository.revocarSiActivo(claims.getId())).thenReturn(1);

        assertDoesNotThrow(() -> service.verificarYRotarRefreshToken(claims));
        verify(refreshTokenRepository, never()).revocarTodosDelUsuario(anyInt());
    }

    @Test
    void verificarYRotarRefreshToken_YaRevocado_RevocaTodoYLanzaForbidden() {
        String token = service.generarRefreshToken(1, "test@test.com");
        Claims claims = service.validarTokenExtraerClaims(token, "REFRESH");

        when(refreshTokenRepository.revocarSiActivo(claims.getId())).thenReturn(0);

        assertThrows(ForbiddenException.class, () -> service.verificarYRotarRefreshToken(claims));
        verify(refreshTokenRepository).revocarTodosDelUsuario(1);
    }

    @Test
    void verificarYRotarRefreshToken_JtiNulo_LanzaForbiddenSinConsultarRepo() {
        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn(null);
        when(claims.get("idUsuario", Integer.class)).thenReturn(1);

        assertThrows(ForbiddenException.class, () -> service.verificarYRotarRefreshToken(claims));
        verify(refreshTokenRepository, never()).revocarSiActivo(anyString());
        verify(refreshTokenRepository).revocarTodosDelUsuario(1);
    }

    @Test
    void revocarRefreshTokenPorJti_JtiValido_DelegaEnRepositorio() {
        service.revocarRefreshTokenPorJti("un-jti");
        verify(refreshTokenRepository).revocarSiActivo("un-jti");
    }

    @Test
    void revocarRefreshTokenPorJti_JtiNuloOBlank_NoHaceNada() {
        service.revocarRefreshTokenPorJti(null);
        service.revocarRefreshTokenPorJti("  ");
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void revocarTodasLasSesiones_DelegaEnRepositorio() {
        service.revocarTodasLasSesiones(1);
        verify(refreshTokenRepository).revocarTodosDelUsuario(1);
    }
}
