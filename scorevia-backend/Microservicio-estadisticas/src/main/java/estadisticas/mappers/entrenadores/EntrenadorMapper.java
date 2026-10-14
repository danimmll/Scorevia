package estadisticas.mappers.entrenadores;

import estadisticas.dto.entrenadores.EntrenadorDTO;
import estadisticas.entities.entrenadores.Entrenador;
import estadisticas.mappers.equipos.EquipoMapper;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

// Añadimos uses = {EquipoMapper.class} para que sepa cómo convertir el Equipo anidado
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, uses = {EquipoMapper.class})
public interface EntrenadorMapper {

    // Eliminamos el @Mapping anterior porque el campo "nombreEquipoActual" ya no existe en tu DTO
    @org.mapstruct.Mapping(target = "palmaresConsultado",
            expression = "java(entrenador.getUltimaSyncTrofeos() != null)")
    EntrenadorDTO toDto(Entrenador entrenador);
}