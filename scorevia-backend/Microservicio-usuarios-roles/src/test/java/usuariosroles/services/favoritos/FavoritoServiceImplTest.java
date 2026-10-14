package usuariosroles.services.favoritos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import usuariosroles.dto.FavoritoDTO;
import usuariosroles.entities.*;
import usuariosroles.exception.DuplicateException;
import usuariosroles.exception.NotFoundException;
import usuariosroles.mappers.FavoritoMapper;
import usuariosroles.repositories.*;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FavoritoServiceImplTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private UsuarioEquipoFavoritoRepository equipoRepo;
    @Mock private UsuarioLigaFavoritaRepository ligaRepo;
    @Mock private UsuarioPartidoFavoritoRepository partidoRepo;
    @Mock private FavoritoMapper favoritoMapper;

    @InjectMocks
    private FavoritoServiceImpl service;

    private Usuario usuario;
    private FavoritoDTO dtoMock;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setIdUsuario(1);
        dtoMock = new FavoritoDTO();
    }

    @Test
    void throwExceptionSiUsuarioNoExiste() {
        when(usuarioRepository.findByIdUsuarioAndActivo(99, true)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.addEquipoFavorito(99, 1));
    }

    // --- EQUIPOS ---
    @Test
    void addEquipoFavorito_Exito() {
        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.of(usuario));
        when(equipoRepo.existsByUsuario_IdUsuarioAndId_IdEquipoApi(1, 10)).thenReturn(false);

        service.addEquipoFavorito(1, 10);
        verify(equipoRepo).save(any(UsuarioEquipoFavorito.class));
    }

    @Test
    void addEquipoFavorito_Duplicado_LanzaExcepcion() {
        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.of(usuario));
        when(equipoRepo.existsByUsuario_IdUsuarioAndId_IdEquipoApi(1, 10)).thenReturn(true);

        assertThrows(DuplicateException.class, () -> service.addEquipoFavorito(1, 10));
    }

    @Test
    void removeEquipoFavorito_Exito() {
        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.of(usuario));
        service.removeEquipoFavorito(1, 10);
        verify(equipoRepo).deleteFavoritoDirecto(1, 10);
    }

    @Test
    void getEquiposFavoritos_Exito() {
        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.of(usuario));
        when(equipoRepo.findByUsuario_IdUsuario(1)).thenReturn(List.of(new UsuarioEquipoFavorito()));
        when(favoritoMapper.toDto(any(UsuarioEquipoFavorito.class))).thenReturn(dtoMock);

        List<FavoritoDTO> result = service.getEquiposFavoritos(1);
        assertFalse(result.isEmpty());
    }

    // --- LIGAS ---
    @Test
    void addLigaFavorita_Exito() {
        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.of(usuario));
        when(ligaRepo.existsByUsuario_IdUsuarioAndId_IdLigaApi(1, 10)).thenReturn(false);

        service.addLigaFavorita(1, 10);
        verify(ligaRepo).save(any(UsuarioLigaFavorita.class));
    }

    @Test
    void addLigaFavorita_Duplicado_LanzaExcepcion() {
        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.of(usuario));
        when(ligaRepo.existsByUsuario_IdUsuarioAndId_IdLigaApi(1, 10)).thenReturn(true);

        assertThrows(DuplicateException.class, () -> service.addLigaFavorita(1, 10));
    }

    @Test
    void removeLigaFavorita_Exito() {
        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.of(usuario));
        service.removeLigaFavorita(1, 10);
        verify(ligaRepo).deleteByUsuario_IdUsuarioAndId_IdLigaApi(1, 10);
    }

    @Test
    void getLigasFavoritas_Exito() {
        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.of(usuario));
        when(ligaRepo.findByUsuario_IdUsuario(1)).thenReturn(List.of(new UsuarioLigaFavorita()));
        when(favoritoMapper.toDto(any(UsuarioLigaFavorita.class))).thenReturn(dtoMock);

        List<FavoritoDTO> result = service.getLigasFavoritas(1);
        assertFalse(result.isEmpty());
    }

    // --- PARTIDOS ---
    @Test
    void addPartidoFavorito_Exito() {
        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.of(usuario));
        when(partidoRepo.existsByUsuario_IdUsuarioAndId_IdPartidoApi(1, 10)).thenReturn(false);

        service.addPartidoFavorito(1, 10);
        verify(partidoRepo).save(any(UsuarioPartidoFavorito.class));
    }

    @Test
    void addPartidoFavorito_Duplicado_LanzaExcepcion() {
        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.of(usuario));
        when(partidoRepo.existsByUsuario_IdUsuarioAndId_IdPartidoApi(1, 10)).thenReturn(true);

        assertThrows(DuplicateException.class, () -> service.addPartidoFavorito(1, 10));
    }

    @Test
    void removePartidoFavorito_Exito() {
        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.of(usuario));
        service.removePartidoFavorito(1, 10);
        verify(partidoRepo).deleteByUsuario_IdUsuarioAndId_IdPartidoApi(1, 10);
    }

    @Test
    void getPartidosFavoritos_Exito() {
        when(usuarioRepository.findByIdUsuarioAndActivo(1, true)).thenReturn(Optional.of(usuario));
        when(partidoRepo.findByUsuario_IdUsuario(1)).thenReturn(List.of(new UsuarioPartidoFavorito()));
        when(favoritoMapper.toDto(any(UsuarioPartidoFavorito.class))).thenReturn(dtoMock);

        List<FavoritoDTO> result = service.getPartidosFavoritos(1);
        assertFalse(result.isEmpty());
    }
}