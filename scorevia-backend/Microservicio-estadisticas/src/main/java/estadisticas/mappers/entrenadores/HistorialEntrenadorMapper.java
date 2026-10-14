package estadisticas.mappers.entrenadores;

import estadisticas.dto.entrenadores.HistorialEntrenadorDTO;
import estadisticas.entities.entrenadores.HistorialEntrenador;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface HistorialEntrenadorMapper {

    @Mapping(target = "idEntrenador", source = "entrenador.idEntrenador")
    @Mapping(target = "nombreEntrenador", source = "entrenador.nombre")
    @Mapping(target = "fotoEntrenador", source = "entrenador.foto")
    @Mapping(target = "idEquipo", source = "equipo.idEquipo")
    @Mapping(target = "nombreEquipo", source = "equipo.nombre")
    @Mapping(target = "escudoEquipo", source = "equipo.escudoUrl")
    HistorialEntrenadorDTO toDto(HistorialEntrenador entity);
}
