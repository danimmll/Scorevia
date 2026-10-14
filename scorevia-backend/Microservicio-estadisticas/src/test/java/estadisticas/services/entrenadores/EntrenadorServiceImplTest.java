package estadisticas.services.entrenadores;

import estadisticas.dto.entrenadores.EntrenadorDTO;
import estadisticas.dto.ligas.TrofeoDTO;
import estadisticas.entities.entrenadores.Entrenador;
import estadisticas.entities.ligas.Trofeo;
import estadisticas.exception.NotFoundException;
import estadisticas.mappers.entrenadores.EntrenadorMapper;
import estadisticas.mappers.ligas.TrofeoMapper;
import estadisticas.repositories.entrenadores.EntrenadorRepository;
import estadisticas.repositories.ligas.TrofeoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntrenadorServiceImplTest {

    @Mock private EntrenadorRepository entrenadorRepository;
    @Mock private EntrenadorMapper entrenadorMapper;
    @Mock private TrofeoRepository trofeoRepository;
    @Mock private TrofeoMapper trofeoMapper;
    @Mock private estadisticas.services.ligas.BanderasPais banderasPais;

    @InjectMocks private EntrenadorServiceImpl entrenadorService;

    @Test
    void obtenerEntrenadorPorId_Existe_RetornaDTO() {
        Entrenador entrenador = new Entrenador();
        EntrenadorDTO dto = new EntrenadorDTO();

        when(entrenadorRepository.findById(1)).thenReturn(Optional.of(entrenador));
        when(entrenadorMapper.toDto(entrenador)).thenReturn(dto);

        EntrenadorDTO resultado = entrenadorService.obtenerEntrenadorPorId(1);

        assertNotNull(resultado);
        verify(entrenadorRepository).findById(1);
    }

    @Test
    void obtenerEntrenadorPorId_NoExiste_LanzaException() {
        when(entrenadorRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> entrenadorService.obtenerEntrenadorPorId(99));
    }

    @Test
    void obtenerEntrenadorPorEquipo_Existe_RetornaDTO() {
        Entrenador entrenador = new Entrenador();
        EntrenadorDTO dto = new EntrenadorDTO();

        when(entrenadorRepository.findByEquipo_IdEquipo(10)).thenReturn(List.of(entrenador));
        when(entrenadorMapper.toDto(entrenador)).thenReturn(dto);

        EntrenadorDTO resultado = entrenadorService.obtenerEntrenadorPorEquipo(10);

        assertNotNull(resultado);
        verify(entrenadorRepository).findByEquipo_IdEquipo(10);
    }

    @Test
    void obtenerEntrenadorPorEquipo_NoExiste_LanzaException() {
        when(entrenadorRepository.findByEquipo_IdEquipo(99)).thenReturn(Collections.emptyList());

        assertThrows(NotFoundException.class, () -> entrenadorService.obtenerEntrenadorPorEquipo(99));
    }

    @Test
    void obtenerTrofeosEntrenador_RetornaLista() {
        Trofeo trofeo = new Trofeo();
        TrofeoDTO dto = new TrofeoDTO();

        when(trofeoRepository.findByEntrenador_IdEntrenador(1)).thenReturn(List.of(trofeo));
        when(trofeoMapper.toDto(trofeo)).thenReturn(dto);

        List<TrofeoDTO> resultado = entrenadorService.obtenerTrofeosEntrenador(1);

        assertNotNull(resultado);
        assertEquals(1, resultado.size());
        verify(trofeoRepository).findByEntrenador_IdEntrenador(1);
    }
}