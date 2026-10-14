package estadisticas.mappers.equipos;

import estadisticas.dto.equipos.TraspasoDTO;
import estadisticas.entities.equipos.Traspaso;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TraspasoMapper {
    @Mapping(target = "idJugador", source = "jugador.idJugador")
    @Mapping(target = "nombreJugador", source = "jugador.nombre")
    @Mapping(target = "idEquipoOrigen", source = "equipoOrigen.idEquipo")
    @Mapping(target = "equipoOrigen", source = "equipoOrigen.nombre")
    @Mapping(target = "escudoOrigen", source = "equipoOrigen.escudoUrl")
    @Mapping(target = "idEquipoDestino", source = "equipoDestino.idEquipo")
    @Mapping(target = "equipoDestino", source = "equipoDestino.nombre")
    @Mapping(target = "escudoDestino", source = "equipoDestino.escudoUrl")
    TraspasoDTO toDto(Traspaso traspaso);
}