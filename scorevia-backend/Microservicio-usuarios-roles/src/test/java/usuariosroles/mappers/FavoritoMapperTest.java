package usuariosroles.mappers;

import org.junit.jupiter.api.Test;
import usuariosroles.dto.FavoritoDTO;
import usuariosroles.entities.Usuario;
import usuariosroles.entities.UsuarioEquipoFavorito;
import usuariosroles.entities.UsuarioLigaFavorita;
import usuariosroles.entities.UsuarioPartidoFavorito;
import usuariosroles.models.UsuarioEquipoFavoritoId;
import usuariosroles.models.UsuarioLigaFavoritaId;
import usuariosroles.models.UsuarioPartidoFavoritoId;

import java.time.LocalDateTime;
import java.time.Month;

import static org.junit.jupiter.api.Assertions.*;

class FavoritoMapperTest {

    private final FavoritoMapper favoritoMapper = new FavoritoMapperImpl();

    private final Usuario usuario = Usuario.builder()
            .idUsuario(12)
            .nombreUsuario("Juan")
            .build();

    private final LocalDateTime fechaAgregado = LocalDateTime.of(2026, Month.JANUARY, 15, 10, 30);

    @Test
    void toDto_deberiaMapearEquipoFavorito() {
        UsuarioEquipoFavorito entity = UsuarioEquipoFavorito.builder()
                .id(UsuarioEquipoFavoritoId.builder().idUsuario(12).idEquipoApi(33).build())
                .usuario(usuario)
                .fechaAgregado(fechaAgregado)
                .build();

        FavoritoDTO dto = favoritoMapper.toDto(entity);

        assertNotNull(dto);
        assertEquals(12, dto.getIdUsuario());
        assertEquals(33, dto.getIdReferenciaApi());
        assertEquals(fechaAgregado, dto.getFechaAgregado());
    }

    @Test
    void toDto_deberiaMapearLigaFavorita() {
        UsuarioLigaFavorita entity = UsuarioLigaFavorita.builder()
                .id(UsuarioLigaFavoritaId.builder().idUsuario(12).idLigaApi(140).build())
                .usuario(usuario)
                .fechaAgregado(fechaAgregado)
                .build();

        FavoritoDTO dto = favoritoMapper.toDto(entity);

        assertNotNull(dto);
        assertEquals(12, dto.getIdUsuario());
        assertEquals(140, dto.getIdReferenciaApi());
        assertEquals(fechaAgregado, dto.getFechaAgregado());
    }

    @Test
    void toDto_deberiaMapearPartidoFavorito() {
        UsuarioPartidoFavorito entity = UsuarioPartidoFavorito.builder()
                .id(UsuarioPartidoFavoritoId.builder().idUsuario(12).idPartidoApi(987654).build())
                .usuario(usuario)
                .fechaAgregado(fechaAgregado)
                .build();

        FavoritoDTO dto = favoritoMapper.toDto(entity);

        assertNotNull(dto);
        assertEquals(12, dto.getIdUsuario());
        assertEquals(987654, dto.getIdReferenciaApi());
        assertEquals(fechaAgregado, dto.getFechaAgregado());
    }
}
