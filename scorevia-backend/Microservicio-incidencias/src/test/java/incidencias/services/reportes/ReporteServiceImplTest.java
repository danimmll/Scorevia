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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReporteServiceImplTest {

    @Mock
    private ReporteRepository reporteRepository;

    @Mock
    private ReporteMapper reporteMapper;

    @Mock
    private UsuariosClient usuariosClient;

    @InjectMocks
    private ReporteServiceImpl reporteService;

    private ReporteDTO reporteDTO;
    private Reporte reporteEntity;

    @BeforeEach
    void setUp() {

        reporteDTO = new ReporteDTO();
        reporteDTO.setIdReporte(1);
        reporteDTO.setIdDenunciado(2);
        reporteDTO.setMotivo("Insultos en los comentarios del partido");

        reporteEntity = new Reporte();
        reporteEntity.setIdReporte(1);
        reporteEntity.setMotivo("Insultos en los comentarios del partido");
    }

    @Test
    void crearReporte_DeberiaAsignarEstadoPendienteYGuardar() {

        when(usuariosClient.existeUsuario(2)).thenReturn(ResponseEntity.ok().build());
        when(reporteMapper.toEntity(any(ReporteDTO.class))).thenReturn(reporteEntity);
        when(reporteRepository.save(any(Reporte.class))).thenReturn(reporteEntity);

        ReporteDTO dtoGuardado = new ReporteDTO();
        dtoGuardado.setEstado("PENDIENTE");
        when(reporteMapper.toDto(any(Reporte.class))).thenReturn(dtoGuardado);

        ReporteDTO resultado = reporteService.crearReporte(reporteDTO);

        assertNotNull(resultado);
        assertEquals("PENDIENTE", resultado.getEstado()); // Verificamos que se le inyectó el estado
        verify(reporteRepository, times(1)).save(any(Reporte.class));
    }

    @Test
    void crearReporte_DenunciadoNoExiste_LanzaNotFoundSinGuardar() {
        when(usuariosClient.existeUsuario(2)).thenThrow(new NotFoundException("Usuario no encontrado"));

        assertThrows(NotFoundException.class, () -> reporteService.crearReporte(reporteDTO));

        verify(reporteRepository, never()).save(any(Reporte.class));
    }

    @Test
    void crearReporte_UsuariosRolesInalcanzable_LanzaConflictSinGuardar() {
        // Falla cerrado a propósito (ver comentario en ReporteServiceImpl): cualquier otro
        // fallo de comunicación (timeout, circuito, 5xx) no debe dejar crear el reporte.
        when(usuariosClient.existeUsuario(anyInt())).thenThrow(new RuntimeException("Timeout"));

        assertThrows(ConflictException.class, () -> reporteService.crearReporte(reporteDTO));

        verify(reporteRepository, never()).save(any(Reporte.class));
    }

    @Test
    void crearReporte_UsuariosRolesLanzaBadRequestTipado_SePropagaTalCual() {
        // A diferencia del RuntimeException genérico de arriba, este SÍ es una respuesta real
        // y ya decodificada de usuarios-roles (400/409/403 vía CustomFeignErrorDecoder) -- debe
        // propagarse tal cual, no enmascararse como "sin comunicación con el servicio".
        when(usuariosClient.existeUsuario(anyInt())).thenThrow(new BadRequestException("Id de usuario mal formado"));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> reporteService.crearReporte(reporteDTO));
        assertEquals("Id de usuario mal formado", ex.getMessage());

        verify(reporteRepository, never()).save(any(Reporte.class));
    }

    @Test
    void crearReporte_YaTieneReportePendienteContraElMismoObjetivo_LanzaConflictSinGuardar() {
        reporteDTO.setIdDenunciante(1);
        when(usuariosClient.existeUsuario(2)).thenReturn(ResponseEntity.ok().build());
        when(reporteRepository.existeReportePendiente(1, 2)).thenReturn(true);

        assertThrows(ConflictException.class, () -> reporteService.crearReporte(reporteDTO));

        verify(reporteRepository, never()).save(any(Reporte.class));
    }

    @Test
    void crearReporte_IgnoraCamposDeResolucionInyectadosPorElCliente() {
        // Mass-assignment: un usuario normal no puede fijar estos campos al crear un reporte,
        // aunque los mande en el JSON -- son exclusivos del flujo de resolución por un admin.
        reporteDTO.setIdAdminResolutor(999);
        reporteDTO.setResolucion("Ya resuelto, no hay nada que ver aquí");
        reporteDTO.setFechaResolucion(LocalDateTime.of(2020, Month.JANUARY, 1, 0, 0));

        when(usuariosClient.existeUsuario(2)).thenReturn(ResponseEntity.ok().build());
        when(reporteMapper.toEntity(any(ReporteDTO.class))).thenReturn(reporteEntity);
        when(reporteRepository.save(any(Reporte.class))).thenReturn(reporteEntity);
        when(reporteMapper.toDto(any(Reporte.class))).thenReturn(new ReporteDTO());

        reporteService.crearReporte(reporteDTO);

        ArgumentCaptor<ReporteDTO> captor = ArgumentCaptor.forClass(ReporteDTO.class);
        verify(reporteMapper).toEntity(captor.capture());
        ReporteDTO dtoMapeado = captor.getValue();
        assertNull(dtoMapeado.getIdAdminResolutor());
        assertNull(dtoMapeado.getResolucion());
        assertNull(dtoMapeado.getFechaResolucion());
        assertEquals("PENDIENTE", dtoMapeado.getEstado());
    }

    @Test
    void obtenerReportesPendientes_DeberiaDevolverPagina() {

        Pageable pageable = PageRequest.of(0, 20);
        Page<Reporte> paginaEntidades = new PageImpl<>(List.of(reporteEntity));
        when(reporteRepository.findByEstadoOrderByFechaReporteDesc("PENDIENTE", pageable)).thenReturn(paginaEntidades);
        when(reporteMapper.toDto(any(Reporte.class))).thenReturn(reporteDTO);

        Page<ReporteDTO> resultados = reporteService.obtenerReportesPendientes(pageable);

        assertNotNull(resultados);
        assertEquals(1, resultados.getTotalElements());
        verify(reporteRepository, times(1)).findByEstadoOrderByFechaReporteDesc("PENDIENTE", pageable);
    }

    @Test
    void resolverReporte_DeberiaActualizarYDevolver_CuandoExiste() {

        when(reporteRepository.findById(1)).thenReturn(Optional.of(reporteEntity));
        when(reporteRepository.save(any(Reporte.class))).thenReturn(reporteEntity);

        ReporteDTO dtoResuelto = new ReporteDTO();
        dtoResuelto.setEstado("RESUELTO");
        dtoResuelto.setResolucion("Baneado de por vida");
        when(reporteMapper.toDto(any(Reporte.class))).thenReturn(dtoResuelto);

        ReporteDTO resultado = reporteService.resolverReporte(1, EstadoReporte.RESUELTO, "Baneado de por vida", 7);

        assertNotNull(resultado);
        assertEquals("RESUELTO", resultado.getEstado());
        assertEquals("Baneado de por vida", resultado.getResolucion());

        assertEquals("RESUELTO", reporteEntity.getEstado());
        assertEquals("Baneado de por vida", reporteEntity.getResolucion());
        assertEquals(7, reporteEntity.getIdAdminResolutor());
        assertNotNull(reporteEntity.getFechaResolucion());
        verify(reporteRepository, times(1)).save(reporteEntity);
    }

    @Test
    void resolverReporte_DeberiaLanzarExcepcion_CuandoNoExiste() {
        when(reporteRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> reporteService.resolverReporte(99, EstadoReporte.RESUELTO, "No importa", 7));

        verify(reporteRepository, never()).save(any(Reporte.class));
    }

    @Test
    void resolverReporte_PermiteReResolverUnoYaResuelto_ActualizaAdminYFecha() {
        reporteEntity.setEstado("RESUELTO");
        reporteEntity.setResolucion("Aviso");
        reporteEntity.setIdAdminResolutor(3);

        when(reporteRepository.findById(1)).thenReturn(Optional.of(reporteEntity));
        when(reporteRepository.save(any(Reporte.class))).thenReturn(reporteEntity);
        when(reporteMapper.toDto(any(Reporte.class))).thenReturn(new ReporteDTO());

        ReporteDTO resultado = reporteService.resolverReporte(1, EstadoReporte.RECHAZADO, "Corrección: no procede", 9);

        assertNotNull(resultado);
        assertEquals("RECHAZADO", reporteEntity.getEstado());
        assertEquals("Corrección: no procede", reporteEntity.getResolucion());
        assertEquals(9, reporteEntity.getIdAdminResolutor()); // se sobrescribe con el admin que corrige
        assertNotNull(reporteEntity.getFechaResolucion());
        verify(reporteRepository, times(1)).save(reporteEntity);
    }

    @Test
    void retirarReporte_Dueno_Pendiente_Borra() {
        reporteEntity.setIdDenunciante(5);
        // estado por defecto de la entidad ya es "PENDIENTE"
        when(reporteRepository.findById(1)).thenReturn(Optional.of(reporteEntity));

        reporteService.retirarReporte(1, 5);

        verify(reporteRepository, times(1)).delete(reporteEntity);
    }

    @Test
    void retirarReporte_NoEsElDueno_LanzaForbidden() {
        reporteEntity.setIdDenunciante(5);
        when(reporteRepository.findById(1)).thenReturn(Optional.of(reporteEntity));

        assertThrows(ForbiddenException.class, () -> reporteService.retirarReporte(1, 999));

        verify(reporteRepository, never()).delete(any(Reporte.class));
    }

    @Test
    void retirarReporte_YaResuelto_LanzaConflict() {
        reporteEntity.setIdDenunciante(5);
        reporteEntity.setEstado("RESUELTO");
        when(reporteRepository.findById(1)).thenReturn(Optional.of(reporteEntity));

        assertThrows(ConflictException.class, () -> reporteService.retirarReporte(1, 5));

        verify(reporteRepository, never()).delete(any(Reporte.class));
    }

    @Test
    void retirarReporte_NoExiste_LanzaNotFound() {
        when(reporteRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> reporteService.retirarReporte(99, 5));

        verify(reporteRepository, never()).delete(any(Reporte.class));
    }
}