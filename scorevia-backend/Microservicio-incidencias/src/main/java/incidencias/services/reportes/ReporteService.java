package incidencias.services.reportes;

import incidencias.dto.EstadoReporte;
import incidencias.dto.ReporteDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReporteService {
    ReporteDTO crearReporte(ReporteDTO dto);
    Page<ReporteDTO> obtenerReportesPendientes(Pageable pageable);
    ReporteDTO resolverReporte(Integer idReporte, EstadoReporte estado, String resolucion, Integer idAdmin);
    void retirarReporte(Integer idReporte, Integer idUsuario);
}