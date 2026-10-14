package estadisticas.mappers.partidos;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import estadisticas.dto.partidos.EstadisticaJugadorPartidoDTO;
import estadisticas.entities.partidos.EstadisticaJugadorPartido;

// Añadido el ReportingPolicy.IGNORE para limpiar los warnings de Jenkins
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EstadisticaJugadorMapper {

    @Mapping(source = "equipo.idEquipo", target = "idEquipo")
    @Mapping(source = "jugador.nombre", target = "nombreJugador")
    @Mapping(source = "jugador.idJugador", target = "idJugador")
    @Mapping(source = "jugador.fotoUrl", target = "foto")
    EstadisticaJugadorPartidoDTO toDto(EstadisticaJugadorPartido entity);
}