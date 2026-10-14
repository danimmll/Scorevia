package usuariosroles.services.jwt;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import usuariosroles.entities.RefreshToken;
import usuariosroles.exception.ForbiddenException;
import usuariosroles.exception.InvalidTokenTypeException;
import usuariosroles.repositories.RefreshTokenRepository;
import usuariosroles.security.filter.TokenJwtConfig;

@Service
@RequiredArgsConstructor
// java:S2143/S6355 suprimidas: la API de jjwt 0.13.0 (ClaimsMutator/JwtBuilder) solo admite
// java.util.Date en expiration()/issuedAt()/notBefore(), no existe overload con Instant.
@SuppressWarnings({"java:S3366", "java:S2143", "java:S6355"})
public class JwtTokenServiceImpl implements JwtTokenService {

    private static final String TYPE_ACCESS = "ACCESS";
    private static final String TYPE_REFRESH = "REFRESH";
    private static final String CLAIM_TYPE = "token_type";
    private static final String CLAIM_CORREO = "correo";
    private static final String CLAIM_ID = "idUsuario";
    private static final String CLAIM_AUTH = "authorities";

    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    public String generarAccessToken(Integer idUsuario, String correo, Collection<? extends GrantedAuthority> roles) {
        List<String> roleNames = roles != null
                ? roles.stream().map(GrantedAuthority::getAuthority).toList()
                : List.of();

        Claims claims = Jwts.claims()
                .add(CLAIM_AUTH, roleNames)
                .add(CLAIM_CORREO, correo)
                .add(CLAIM_ID, idUsuario)
                .add(CLAIM_TYPE, TYPE_ACCESS) // Marcamos el tipo de token
                .build();

        return Jwts.builder()
                .subject(correo)
                .claims(claims)
                .expiration(Date.from(Instant.now().plus(15, ChronoUnit.MINUTES))) // 15 Minutos estándar de seguridad
                .signWith(TokenJwtConfig.SECRET_KEY)
                .compact();
    }

    @Override
    public String generarRefreshToken(Integer idUsuario, String correo) {
        String jti = UUID.randomUUID().toString();
        Instant expiracion = Instant.now().plus(7, ChronoUnit.DAYS);

        Claims claims = Jwts.claims()
                .add(CLAIM_CORREO, correo)
                .add(CLAIM_ID, idUsuario)
                .add(CLAIM_TYPE, TYPE_REFRESH) // Marcamos que este es el Refresh
                .build();

        String token = Jwts.builder()
                .id(jti)
                .subject(correo)
                .claims(claims)
                .expiration(Date.from(expiracion))
                .signWith(TokenJwtConfig.SECRET_KEY)
                .compact();

        // Rastro server-side del jti: sin esto el token es puramente stateless y no se puede
        // rotar ni revocar (ver JwtTokenService.verificarYRotarRefreshToken/revocarRefreshTokenPorJti).
        refreshTokenRepository.save(RefreshToken.builder()
                .idUsuario(idUsuario)
                .jti(jti)
                .fechaExpiracion(LocalDateTime.ofInstant(expiracion, ZoneId.systemDefault()))
                .revocado(false)
                .build());

        return token;
    }

    @Override
    public Claims validarTokenExtraerClaims(String token, String tipoEsperado) {
        Claims claims = Jwts.parser()
                .verifyWith(TokenJwtConfig.SECRET_KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String tipoToken = claims.get(CLAIM_TYPE, String.class);
        if (tipoToken == null || !tipoToken.equals(tipoEsperado)) {
            throw new InvalidTokenTypeException("El tipo de token proporcionado no es válido para esta operación.");
        }

        return claims;
    }

    @Override
    public void verificarYRotarRefreshToken(Claims claimsRefresh) {
        String jti = claimsRefresh.getId();

        // revocarSiActivo() devuelve 0 tanto si el jti no existe (token forjado, o emitido antes
        // de que existiera esta tabla y por tanto sin jti) como si ya estaba revocado (reuso de
        // un refresh ya rotado -señal de robo-, o perdió la carrera contra otro refresh
        // concurrente con el mismo token). Los tres casos se tratan igual: por precaución, se
        // revocan TODAS las sesiones activas del usuario y se rechaza la petición.
        if (jti == null || refreshTokenRepository.revocarSiActivo(jti) == 0) {
            Integer idUsuario = claimsRefresh.get(CLAIM_ID, Integer.class);
            if (idUsuario != null) {
                refreshTokenRepository.revocarTodosDelUsuario(idUsuario);
            }
            throw new ForbiddenException(
                    "Se ha detectado un uso sospechoso de tu sesión; por seguridad, se han cerrado todas tus sesiones. Inicia sesión de nuevo.");
        }
    }

    @Override
    public void revocarRefreshTokenPorJti(String jti) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        refreshTokenRepository.revocarSiActivo(jti);
    }

    @Override
    public void revocarTodasLasSesiones(Integer idUsuario) {
        refreshTokenRepository.revocarTodosDelUsuario(idUsuario);
    }
}
