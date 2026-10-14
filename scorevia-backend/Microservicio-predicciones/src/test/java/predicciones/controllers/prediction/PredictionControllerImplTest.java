package predicciones.controllers.prediction;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import predicciones.dto.LivePredictionDTO;
import predicciones.exception.BadRequestException;
import predicciones.services.prediction.PredictionService;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PredictionControllerImplTest {

    @Mock
    private PredictionService predictionService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private PredictionControllerImpl predictionController;

    @Test
    void predecirPartido_IdValido_RetornaOkConPrediccion() {
        // Arrange
        String idPartidoStr = "1055";
        Integer idPartido = 1055;
        when(request.getAttribute("idUsuario")).thenReturn(1);

        Map<String, BigDecimal> mockResult = new HashMap<>();
        mockResult.put("victoriaLocal", BigDecimal.valueOf(45.5));
        mockResult.put("empate", BigDecimal.valueOf(25.0));
        mockResult.put("victoriaVisitante", BigDecimal.valueOf(29.5));

        when(predictionService.predecirPartido(idPartido, 1)).thenReturn(mockResult);

        // Act
        ResponseEntity<Map<String, BigDecimal>> response = predictionController.predecirPartido(idPartidoStr, request);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResult, response.getBody());
        verify(predictionService).predecirPartido(idPartido, 1);
    }

    @Test
    void predecirPartido_IdInvalido_LanzaException() {
        // Arrange
        String idPartidoStr = "texto-invalido";

        // Act & Assert
        assertThrows(BadRequestException.class, () -> predictionController.predecirPartido(idPartidoStr, request));

        // Verificamos que el servicio nunca llega a ser llamado
        verifyNoInteractions(predictionService);
    }

    @Test
    void predecirPartidoEnVivo_ParametrosValidos_RetornaOkConPrediccion() {
        // Arrange
        String idStr = "1055";
        String minutoStr = "75";
        when(request.getAttribute("idUsuario")).thenReturn(1);

        LivePredictionDTO mockDto = LivePredictionDTO.builder()
                .idPartido(1055)
                .minutoActual(75)
                .golesLocalActuales(2)
                .golesVisitanteActuales(1)
                .build();

        when(predictionService.predecirPartidoEnVivo(1055, 75, 1)).thenReturn(mockDto);

        // Act
        ResponseEntity<LivePredictionDTO> response = predictionController.predecirPartidoEnVivo(
                idStr, minutoStr, request);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockDto, response.getBody());
        verify(predictionService).predecirPartidoEnVivo(1055, 75, 1);
    }

    @Test
    void predecirPartidoEnVivo_ParametrosInvalidos_LanzaException() {
        // Arrange
        String idStr = "1055";
        String minutoStr = "minuto_invalido"; // Esto hará que ValidationUtils lance excepción

        // Act & Assert
        assertThrows(BadRequestException.class, () -> predictionController.predecirPartidoEnVivo(
                idStr, minutoStr, request));

        // Verificamos que el servicio nunca llega a ser llamado si los parámetros son erróneos
        verifyNoInteractions(predictionService);
    }
}