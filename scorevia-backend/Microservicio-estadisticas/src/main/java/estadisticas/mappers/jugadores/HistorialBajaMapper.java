package estadisticas.mappers.jugadores;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import estadisticas.dto.jugadores.HistorialBajaDTO;
import estadisticas.entities.jugadores.HistorialBaja;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface HistorialBajaMapper {

    @Mapping(target = "idJugador", source = "jugador.idJugador")
    HistorialBajaDTO toDto(HistorialBaja entity);
}