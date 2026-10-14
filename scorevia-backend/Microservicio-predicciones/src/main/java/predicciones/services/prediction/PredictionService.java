package predicciones.services.prediction;

import predicciones.dto.LivePredictionDTO;

import java.math.BigDecimal;
import java.util.Map;

public interface PredictionService {
    /**
     * Coordina la obtención de datos y el cálculo matemático avanzado pre-partido.
     * Incluye multiplicadores de contexto (Rachas, H2H, Lesiones, Árbitros) para el servicio PREMIUM.
     *
     * @param idPartido El partido que el usuario quiere predecir.
     * @param idUsuario El usuario que hace la petición (para comprobar el acceso PREMIUM).
     * @return Mapa con los porcentajes de victoria Local, Empate y Visitante.
     */
    Map<String, BigDecimal> predecirPartido(Integer idPartido, Integer idUsuario);

    /**
     * Calcula la predicción en tiempo real aplicando el Decaimiento del Tiempo (Time Decay).
     * <p>
     * El marcador real NUNCA se recibe del cliente (que podría falsificarlo): siempre se relee
     * fresco del contexto que devuelve estadísticas (ver {@code obtenerGolesReales}).
     * </p>
     *
     * @param idPartido El ID del partido en curso.
     * @param minuto El minuto exacto en el que se encuentra el partido.
     * @param idUsuario El usuario que hace la petición (para comprobar el acceso PREMIUM).
     * @return DTO con los datos del directo y las nuevas probabilidades.
     */
    LivePredictionDTO predecirPartidoEnVivo(Integer idPartido, int minuto, Integer idUsuario);
}