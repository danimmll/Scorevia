package estadisticas.services.arbitros;

import estadisticas.dto.arbitros.ArbitroDetalleDTO;
import estadisticas.dto.partidos.PartidoDTO;
import estadisticas.entities.arbitros.Arbitro;
import estadisticas.exception.NotFoundException;
import estadisticas.mappers.partidos.PartidoMapper;
import estadisticas.repositories.arbitros.ArbitroRepository;
import estadisticas.repositories.partidos.PartidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Ficha de árbitro.
 *
 * <p>Existe porque desde la ficha de un partido se puede ir al equipo, al jugador y al
 * entrenador, pero el árbitro era un nombre sin destino. No hay foto ni biografía que sacar
 * —API-Football lo manda como texto suelto dentro del fixture, no como entidad propia—, así
 * que la ficha cuenta lo único que sí sabemos de él: cuánto ha arbitrado y con qué mano.</p>
 */
@Service
@RequiredArgsConstructor
public class ArbitroServiceImpl implements ArbitroService {

    private final ArbitroRepository arbitroRepository;
    private final PartidoRepository partidoRepository;
    private final PartidoMapper partidoMapper;

    @Override
    @Transactional(readOnly = true)
    public ArbitroDetalleDTO getArbitroById(Integer idArbitro) {
        Arbitro arbitro = arbitroRepository.findById(idArbitro)
                .orElseThrow(() -> new NotFoundException("Árbitro no encontrado"));

        List<Object[]> filas = arbitroRepository.contarTarjetasPorArbitro(idArbitro);
        Object[] tarjetas = filas.isEmpty() ? new Object[]{0L, 0L, 0L} : filas.get(0);

        long amarillas = aLong(tarjetas[0]);
        long rojas = aLong(tarjetas[1]);
        long partidosConEventos = aLong(tarjetas[2]);

        return ArbitroDetalleDTO.builder()
                .idArbitro(arbitro.getIdArbitro())
                .nombre(arbitro.getNombre())
                // La columna es NOT NULL DEFAULT '': se manda null en vez de una cadena vacía
                // para que el cliente no tenga que distinguir los dos casos.
                .pais(arbitro.getPais() == null || arbitro.getPais().isBlank() ? null : arbitro.getPais())
                .partidosDirigidos(partidoRepository.countByArbitro_IdArbitro(idArbitro))
                .tarjetasAmarillas(amarillas)
                .tarjetasRojas(rojas)
                .partidosConEventos(partidosConEventos)
                .mediaAmarillas(mediaPorPartido(amarillas, partidosConEventos))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PartidoDTO> getPartidosDelArbitro(Integer idArbitro, Pageable pageable) {
        return partidoRepository.findByArbitro_IdArbitro(idArbitro, pageable).map(partidoMapper::toDto);
    }

    // Null, no 0, cuando no hay ni un partido con eventos: "0,0 amarillas por partido" diría
    // que este árbitro no saca tarjetas, y lo que pasa es que no sabemos nada de él.
    private BigDecimal mediaPorPartido(long tarjetas, long partidos) {
        if (partidos <= 0) {
            return null;
        }
        return BigDecimal.valueOf(tarjetas)
                .divide(BigDecimal.valueOf(partidos), 1, RoundingMode.HALF_UP);
    }

    // Postgres devuelve los SUM/COUNT como BigInteger o Long según el driver y la expresión.
    private long aLong(Object valor) {
        return valor instanceof Number numero ? numero.longValue() : 0L;
    }
}
