package estadisticas.mappers.jugadores;

import estadisticas.dto.jugadores.JugadorDTO;
import estadisticas.entities.jugadores.Jugador;
import estadisticas.mappers.equipos.EquipoMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

// uses = EquipoMapper.class resuelve el campo "equipo" (mismo nombre en entidad y DTO,
// pero de tipo Equipo -> EquipoDTO) reutilizando el mapper ya existente -- incluye el escudo,
// que es justo lo que necesita la pantalla de detalle de jugador para mostrar el equipo actual.
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, uses = EquipoMapper.class)
public interface JugadorMapper {

    // "foto" en el DTO, "fotoUrl" en la entidad -- mismo motivo que EquipoMapper.logo/escudoUrl,
    // hace falta el puente explícito porque los nombres no coinciden.
    @Mapping(source = "fotoUrl", target = "foto")
    @Mapping(target = "bajasConsultadas", expression = "java(jugador.getUltimaSyncBajas() != null)")
    JugadorDTO toDto(Jugador jugador);

    @Mapping(source = "foto", target = "fotoUrl")
    Jugador toEntity(JugadorDTO dto);
}