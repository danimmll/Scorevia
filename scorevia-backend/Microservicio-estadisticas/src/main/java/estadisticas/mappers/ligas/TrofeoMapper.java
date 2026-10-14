package estadisticas.mappers.ligas;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import estadisticas.dto.ligas.TrofeoDTO;
import estadisticas.entities.ligas.Trofeo;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TrofeoMapper {

    @Mapping(target = "idJugador", source = "jugador.idJugador")
    @Mapping(target = "idEntrenador", source = "entrenador.idEntrenador")
    TrofeoDTO toDto(Trofeo entity);
}