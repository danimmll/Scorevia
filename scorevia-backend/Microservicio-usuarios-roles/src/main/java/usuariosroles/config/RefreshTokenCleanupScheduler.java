package usuariosroles.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import usuariosroles.repositories.RefreshTokenRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Limpieza periódica de filas de refresh_tokens ya caducadas (revocadas o no): no aportan nada
 * pasada su fecha de expiración, solo harían crecer la tabla indefinidamente sin límite.
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void limpiarRefreshTokensCaducados() {
        try {
            long eliminados = refreshTokenRepository.deleteByFechaExpiracionBefore(LocalDateTime.now(ZoneId.systemDefault()));
            if (eliminados > 0) {
                log.info("Limpieza de refresh tokens: {} filas caducadas eliminadas.", eliminados);
            }
        } catch (Exception e) {
            log.error("Fallo limpiando refresh tokens caducados: {}", e.getMessage());
        }
    }
}
