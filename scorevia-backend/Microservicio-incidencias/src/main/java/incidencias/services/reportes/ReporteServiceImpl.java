package incidencias.services.reportes;

import incidencias.config.UsuariosClient;
import incidencias.dto.EstadoReporte;
import incidencias.dto.ReporteDTO;
import incidencias.entities.Reporte;
import incidencias.exception.BadRequestException;
import incidencias.exception.ConflictException;
import incidencias.exception.ForbiddenException;
import incidencias.exception.NotFoundException;
import incidencias.mappers.ReporteMapper;
import incidencias.repositories.ReporteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReporteServiceImpl implements ReporteService {

    private final ReporteRepository reporteRepository;
    private final ReporteMapper reporteMapper;
    private final UsuariosClient usuariosClient;

    @Override
    @Transactional
    public ReporteDTO crearReporte(ReporteDTO dto) {
        comprobarDenunciadoExiste(dto.getIdDenunciado());

        if (reporteRepository.existeReportePendiente(dto.getIdDenunciante(), dto.getIdDenunciado())) {
            throw new ConflictException("Ya tienes un reporte pendiente contra este usuario.");
        }

        // Mass-assignment: de cuanto trae el DTO al crear, solo idDenunciado/motivo vienen
        // realmente del cliente (idDenunciante ya lo fija el
        // controller desde el usuario autenticado). Sin limpiar esto, un usuario normal podía
        // inyectar en el JSON idAdminResolutor/resolucion/fechaResolucion y dejar un reporte
        // PENDIENTE que "parece" ya resuelto por un admin concreto, corrompiendo el rastro de
        // auditoría de moderación.
        dto.setIdReporte(null);
        dto.setEstado(EstadoReporte.PENDIENTE.name());
        dto.setIdAdminResolutor(null);
        dto.setResolucion(null);
        dto.setFechaResolucion(null);

        Reporte reporte = reporteMapper.toEntity(dto);
        return reporteMapper.toDto(reporteRepository.save(reporte));
    }

    // Falla CERRADO a propósito: incidencias es, entre otras cosas, el histórico de
    // moderación de la app -- un reporte contra un id inventado (o mal mapeado por un bug
    // del cliente) ensucia ese histórico de forma silenciosa y no hay forma de limpiarlo
    // después sin intervención manual. Si usuarios-roles no responde, mejor que el usuario
    // reintente el reporte a que se cree contra un usuario que no existe.
    private void comprobarDenunciadoExiste(Integer idDenunciado) {
        try {
            usuariosClient.existeUsuario(idDenunciado);
        } catch (NotFoundException e) {
            throw new NotFoundException("El usuario denunciado no existe.");
        } catch (BadRequestException | ConflictException | ForbiddenException e) {
            // Errores tipados que CustomFeignErrorDecoder ya decodificó desde una respuesta
            // real de usuarios-roles (400/409/403) -- relanzarlos tal cual conserva el mensaje
            // y el código de estado reales. El catch-all de abajo, en cambio, sí SÍ hubo
            // comunicación, así que envolverlos como "sin comunicación" sería un mensaje falso.
            throw e;
        } catch (Exception e) {
            log.error("No se pudo validar el usuario denunciado {} vía Feign: {}", idDenunciado, e.getMessage());
            throw new ConflictException("Sin comunicación con el servicio de usuarios.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReporteDTO> obtenerReportesPendientes(Pageable pageable) {
        return reporteRepository.findByEstadoOrderByFechaReporteDesc(EstadoReporte.PENDIENTE.name(), pageable)
                .map(reporteMapper::toDto);
    }

    @Override
    @Transactional
    public ReporteDTO resolverReporte(Integer idReporte, EstadoReporte estado, String resolucion, Integer idAdmin) {
        if (estado == EstadoReporte.PENDIENTE) {
            throw new BadRequestException("No se puede resolver un reporte con estado PENDIENTE.");
        }
        if (idAdmin == null) {
            throw new BadRequestException("No se pudo identificar al administrador que resuelve el reporte.");
        }

        Reporte reporte = reporteRepository.findById(idReporte)
                .orElseThrow(() -> new NotFoundException("Reporte no encontrado"));

        reporte.setEstado(estado.name());
        reporte.setResolucion(resolucion);
        // Se permite volver a resolver un reporte ya RESUELTO/RECHAZADO (p.ej. para
        // corregir un error de un admin), pero queda rastro de quién hizo el último
        // cambio y cuándo: no es un historial completo, solo la última resolución.
        reporte.setIdAdminResolutor(idAdmin);
        reporte.setFechaResolucion(LocalDateTime.now(ZoneId.systemDefault()));
        return reporteMapper.toDto(reporteRepository.save(reporte));
    }

    @Override
    @Transactional
    public void retirarReporte(Integer idReporte, Integer idUsuario) {
        Reporte reporte = reporteRepository.findById(idReporte)
                .orElseThrow(() -> new NotFoundException("Reporte no encontrado"));

        if (!reporte.getIdDenunciante().equals(idUsuario)) {
            throw new ForbiddenException("No puedes retirar un reporte que no creaste tú.");
        }

        if (!EstadoReporte.PENDIENTE.name().equals(reporte.getEstado())) {
            throw new ConflictException("No se puede retirar un reporte que ya ha sido revisado.");
        }

        reporteRepository.delete(reporte);
    }
}