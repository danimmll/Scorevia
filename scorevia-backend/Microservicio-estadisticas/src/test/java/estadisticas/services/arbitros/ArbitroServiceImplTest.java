package estadisticas.services.arbitros;

import estadisticas.dto.arbitros.ArbitroDetalleDTO;
import estadisticas.entities.arbitros.Arbitro;
import estadisticas.exception.NotFoundException;
import estadisticas.mappers.partidos.PartidoMapper;
import estadisticas.repositories.arbitros.ArbitroRepository;
import estadisticas.repositories.partidos.PartidoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArbitroServiceImplTest {

    private static final int ID_ARBITRO = 312;

    @Mock private ArbitroRepository arbitroRepository;
    @Mock private PartidoRepository partidoRepository;
    @Mock private PartidoMapper partidoMapper;

    @InjectMocks private ArbitroServiceImpl service;

    private Arbitro arbitro(String pais) {
        Arbitro a = new Arbitro();
        a.setIdArbitro(ID_ARBITRO);
        a.setNombre("Mateu Lahoz");
        a.setPais(pais);
        return a;
    }

    @Test
    void getArbitro_CalculaLaMediaSobreLosPartidosConEventos() {
        when(arbitroRepository.findById(ID_ARBITRO)).thenReturn(Optional.of(arbitro("Spain")));
        // 44 amarillas en 10 partidos con timeline, aunque haya dirigido 30: dividir entre los
        // 30 daría 1,5 y diría que es un árbitro permisivo cuando no lo sabemos.
        when(arbitroRepository.contarTarjetasPorArbitro(ID_ARBITRO))
                .thenReturn(List.<Object[]>of(new Object[]{BigInteger.valueOf(44), BigInteger.valueOf(3), 10L}));
        when(partidoRepository.countByArbitro_IdArbitro(ID_ARBITRO)).thenReturn(30L);

        ArbitroDetalleDTO dto = service.getArbitroById(ID_ARBITRO);

        assertEquals(30L, dto.getPartidosDirigidos());
        assertEquals(44L, dto.getTarjetasAmarillas());
        assertEquals(3L, dto.getTarjetasRojas());
        assertEquals(10L, dto.getPartidosConEventos());
        assertEquals(new BigDecimal("4.4"), dto.getMediaAmarillas());
    }

    @Test
    void getArbitro_SinPartidosConEventos_NoInventaUnaMediaDeCero() {
        // "0,0 amarillas por partido" diría que este árbitro no saca tarjetas; lo que pasa es
        // que de sus partidos no consta ningún timeline.
        when(arbitroRepository.findById(ID_ARBITRO)).thenReturn(Optional.of(arbitro("Spain")));
        when(arbitroRepository.contarTarjetasPorArbitro(ID_ARBITRO))
                .thenReturn(List.<Object[]>of(new Object[]{BigInteger.ZERO, BigInteger.ZERO, 0L}));
        when(partidoRepository.countByArbitro_IdArbitro(ID_ARBITRO)).thenReturn(4L);

        assertNull(service.getArbitroById(ID_ARBITRO).getMediaAmarillas());
    }

    @Test
    void getArbitro_SinFilaDeTarjetas_NoRevienta() {
        when(arbitroRepository.findById(ID_ARBITRO)).thenReturn(Optional.of(arbitro("Spain")));
        when(arbitroRepository.contarTarjetasPorArbitro(ID_ARBITRO)).thenReturn(List.of());
        when(partidoRepository.countByArbitro_IdArbitro(ID_ARBITRO)).thenReturn(0L);

        ArbitroDetalleDTO dto = service.getArbitroById(ID_ARBITRO);

        assertEquals(0L, dto.getTarjetasAmarillas());
        assertNull(dto.getMediaAmarillas());
    }

    @Test
    void getArbitro_PaisVacio_SaleComoNulo() {
        when(arbitroRepository.findById(ID_ARBITRO)).thenReturn(Optional.of(arbitro("")));
        when(arbitroRepository.contarTarjetasPorArbitro(ID_ARBITRO))
                .thenReturn(List.<Object[]>of(new Object[]{BigInteger.ZERO, BigInteger.ZERO, 0L}));
        lenient().when(partidoRepository.countByArbitro_IdArbitro(ID_ARBITRO)).thenReturn(0L);

        assertNull(service.getArbitroById(ID_ARBITRO).getPais());
    }

    @Test
    void getArbitro_Inexistente_LanzaNotFound() {
        when(arbitroRepository.findById(ID_ARBITRO)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.getArbitroById(ID_ARBITRO));
    }
}
