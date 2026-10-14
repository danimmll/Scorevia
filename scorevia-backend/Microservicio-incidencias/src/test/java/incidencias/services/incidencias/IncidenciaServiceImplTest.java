package incidencias.services.incidencias;

import incidencias.dto.IncidenciaDTO;
import incidencias.entities.Incidencia;
import incidencias.exception.NoContentException;
import incidencias.exception.NotFoundException;
import incidencias.mappers.IncidenciaMapper;
import incidencias.repositories.IncidenciaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncidenciaServiceImplTest {

    @Mock
    private IncidenciaRepository incidenciaRepository;

    @Mock
    private IncidenciaMapper incidenciaMapper;

    @InjectMocks
    private IncidenciaServiceImpl incidenciaService;

    private IncidenciaDTO incidenciaDTO;
    private Incidencia incidenciaEntity;

    @BeforeEach
    void setUp() {
        incidenciaDTO = new IncidenciaDTO();
        incidenciaDTO.setIdIncidencia(1);
        incidenciaDTO.setUsuarioContexto("socio@test.com");
        incidenciaDTO.setClase("PartidoController");
        incidenciaDTO.setMetodo("obtenerPartido");

        incidenciaEntity = new Incidencia();
        incidenciaEntity.setIdIncidencia(1);
        incidenciaEntity.setUsuarioContexto("socio@test.com");
        incidenciaEntity.setClase("PartidoController");
        incidenciaEntity.setMetodo("obtenerPartido");
    }

    @Test
    void crearIncidenciaDTO_DeberiaCrearCorrectamente() {
        when(incidenciaMapper.toIncidencia(any(IncidenciaDTO.class))).thenReturn(incidenciaEntity);
        when(incidenciaRepository.save(any(Incidencia.class))).thenReturn(incidenciaEntity);
        when(incidenciaMapper.toDTO(any(Incidencia.class))).thenReturn(incidenciaDTO);

        IncidenciaDTO resultado = incidenciaService.crearIncidenciaDTO(incidenciaDTO);

        assertNotNull(resultado);
        verify(incidenciaRepository, times(1)).save(incidenciaEntity);
    }

    @Test
    void getIncidenciaByIdDTO_DeberiaDevolverIncidencia_CuandoExiste() {
        when(incidenciaRepository.findById(1)).thenReturn(Optional.of(incidenciaEntity));
        when(incidenciaMapper.toDTO(any(Incidencia.class))).thenReturn(incidenciaDTO);

        IncidenciaDTO resultado = incidenciaService.getIncidenciaByIdDTO(1);

        assertNotNull(resultado);
        assertEquals(1, resultado.getIdIncidencia());
    }

    @Test
    void getIncidenciaByIdDTO_DeberiaLanzarNotFound_CuandoNoExiste() {
        when(incidenciaRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> incidenciaService.getIncidenciaByIdDTO(99));
    }

    @Test
    void getIncidenciasDTO_DeberiaDevolverPagina() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Incidencia> paginaMock = new PageImpl<>(List.of(incidenciaEntity));

        when(incidenciaRepository.findAll(pageable)).thenReturn(paginaMock);
        when(incidenciaMapper.toDTO(any(Incidencia.class))).thenReturn(incidenciaDTO);

        Page<IncidenciaDTO> resultado = incidenciaService.getIncidenciasDTO(pageable);

        assertFalse(resultado.isEmpty());
        assertEquals(1, resultado.getTotalElements());
    }

    @Test
    void getIncidenciasDTO_DeberiaLanzarNoContent_CuandoVacia() {
        Pageable pageable = PageRequest.of(0, 10);
        when(incidenciaRepository.findAll(pageable)).thenReturn(Page.empty());

        assertThrows(NoContentException.class, () -> incidenciaService.getIncidenciasDTO(pageable));
    }

    @Test
    void getIncidenciasByUsuarioDTO_DeberiaDevolverPagina() {
        Pageable pageable = PageRequest.of(0, 5);
        when(incidenciaRepository.findByUsuarioContexto("socio@test.com", pageable))
                .thenReturn(new PageImpl<>(List.of(incidenciaEntity)));
        when(incidenciaMapper.toDTO(any(Incidencia.class))).thenReturn(incidenciaDTO);

        Page<IncidenciaDTO> resultado = incidenciaService.getIncidenciasByUsuarioDTO("socio@test.com", pageable);

        assertFalse(resultado.isEmpty());
    }

    @Test
    void getIncidenciasByUsuarioDTO_DeberiaLanzarNoContent_CuandoVacia() {
        Pageable pageable = PageRequest.of(0, 5);
        when(incidenciaRepository.findByUsuarioContexto("fantasma@test.com", pageable)).thenReturn(Page.empty());

        assertThrows(NoContentException.class, () -> incidenciaService.getIncidenciasByUsuarioDTO("fantasma@test.com", pageable));
    }

    @Test
    void getIncidenciasByClaseDTO_DeberiaDevolverPagina() {
        Pageable pageable = PageRequest.of(0, 5);
        when(incidenciaRepository.findByClase("PartidoController", pageable))
                .thenReturn(new PageImpl<>(List.of(incidenciaEntity)));
        when(incidenciaMapper.toDTO(any(Incidencia.class))).thenReturn(incidenciaDTO);

        Page<IncidenciaDTO> resultado = incidenciaService.getIncidenciasByClaseDTO("PartidoController", pageable);

        assertFalse(resultado.isEmpty());
    }

    @Test
    void getIncidenciasByClaseDTO_DeberiaLanzarNoContent_CuandoVacia() {
        Pageable pageable = PageRequest.of(0, 5);
        when(incidenciaRepository.findByClase("ClaseFalsa", pageable)).thenReturn(Page.empty());

        assertThrows(NoContentException.class, () -> incidenciaService.getIncidenciasByClaseDTO("ClaseFalsa", pageable));
    }

    @Test
    void getIncidenciasByMetodoDTO_DeberiaDevolverPagina() {
        Pageable pageable = PageRequest.of(0, 5);
        when(incidenciaRepository.findByMetodo("obtenerPartido", pageable))
                .thenReturn(new PageImpl<>(List.of(incidenciaEntity)));
        when(incidenciaMapper.toDTO(any(Incidencia.class))).thenReturn(incidenciaDTO);

        Page<IncidenciaDTO> resultado = incidenciaService.getIncidenciasByMetodoDTO("obtenerPartido", pageable);

        assertFalse(resultado.isEmpty());
    }

    @Test
    void getIncidenciasByMetodoDTO_DeberiaLanzarNoContent_CuandoVacia() {
        Pageable pageable = PageRequest.of(0, 5);
        when(incidenciaRepository.findByMetodo("metodoFalso", pageable)).thenReturn(Page.empty());

        assertThrows(NoContentException.class, () -> incidenciaService.getIncidenciasByMetodoDTO("metodoFalso", pageable));
    }

    @Test
    void existeUsuario_DeberiaDevolverVerdaderoOFalso() {
        when(incidenciaRepository.existsByUsuarioContexto("socio@test.com")).thenReturn(true);
        assertTrue(incidenciaService.existeUsuario("socio@test.com"));
    }

    @Test
    void existeClase_DeberiaDevolverVerdaderoOFalso() {
        when(incidenciaRepository.existsByClase("PartidoController")).thenReturn(true);
        assertTrue(incidenciaService.existeClase("PartidoController"));
    }

    @Test
    void existeMetodo_DeberiaDevolverVerdaderoOFalso() {
        when(incidenciaRepository.existsByMetodo("obtenerPartido")).thenReturn(true);
        assertTrue(incidenciaService.existeMetodo("obtenerPartido"));
    }
}