package estadisticas.mappers.partidos;

import estadisticas.dto.partidos.EventoPartidoDTO;
import estadisticas.entities.partidos.EventoPartido;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EventoPartidoMapper {

    @Mapping(target = "nombreEquipo", source = "equipo.nombre")
    @Mapping(target = "logoEquipo", source = "equipo.escudoUrl")
    @Mapping(target = "nombreJugadorPrincipal", source = "jugadorPrincipal.nombre")
    @Mapping(target = "nombreJugadorSecundario", source = "jugadorSecundario.nombre")
    EventoPartidoDTO toDto(EventoPartido eventoPartido);
}