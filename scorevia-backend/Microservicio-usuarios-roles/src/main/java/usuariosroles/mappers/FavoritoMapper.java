package usuariosroles.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import usuariosroles.dto.FavoritoDTO;
import usuariosroles.entities.UsuarioEquipoFavorito;
import usuariosroles.entities.UsuarioLigaFavorita;
import usuariosroles.entities.UsuarioPartidoFavorito;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FavoritoMapper {

    // Mapeo para Equipos
    @Mapping(target = "idUsuario", source = "id.idUsuario")
    @Mapping(target = "idReferenciaApi", source = "id.idEquipoApi")
    FavoritoDTO toDto(UsuarioEquipoFavorito entity);

    // Mapeo para Ligas
    @Mapping(target = "idUsuario", source = "id.idUsuario")
    @Mapping(target = "idReferenciaApi", source = "id.idLigaApi")
    FavoritoDTO toDto(UsuarioLigaFavorita entity);

    // Mapeo para Partidos
    @Mapping(target = "idUsuario", source = "id.idUsuario")
    @Mapping(target = "idReferenciaApi", source = "id.idPartidoApi")
    FavoritoDTO toDto(UsuarioPartidoFavorito entity);
}