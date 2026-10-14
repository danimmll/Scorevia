package estadisticas.mappers.partidos;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import estadisticas.dto.partidos.PartidoDTO;
import estadisticas.entities.partidos.Partido;

/**
 * Mapper de Partido a PartidoDTO usando MapStruct, aplanando las relaciones
 * a su nombre legible y extrayendo los identificadores.
 *
 * @author Daniel
 * @version 1.0
 */
@Mapper(componentModel = "spring")
public interface PartidoMapper {

    @Mapping(source = "liga.nombre", target = "liga")
    @Mapping(source = "liga.idLiga", target = "idLiga")
    @Mapping(source = "liga.pais", target = "paisLiga")
    @Mapping(source = "liga.logo", target = "logoLiga")
    @Mapping(source = "liga.bandera", target = "banderaLiga")
    @Mapping(source = "liga.coberturaStatsPartido", target = "coberturaStatsPartido")
    @Mapping(source = "liga.coberturaStatsJugadores", target = "coberturaStatsJugadores")
    @Mapping(source = "liga.coberturaEventos", target = "coberturaEventos")
    @Mapping(source = "liga.coberturaAlineaciones", target = "coberturaAlineaciones")
    @Mapping(source = "equipoLocal.nombre", target = "equipoLocal")
    @Mapping(source = "equipoLocal.escudoUrl", target = "escudoLocal")
    @Mapping(source = "equipoVisitante.nombre", target = "equipoVisitante")
    @Mapping(source = "equipoVisitante.escudoUrl", target = "escudoVisitante")
    @Mapping(source = "equipoLocal.idEquipo", target = "idEquipoLocal")
    @Mapping(source = "equipoVisitante.idEquipo", target = "idEquipoVisitante")
    @Mapping(source = "arbitro.nombre", target = "arbitro")
    PartidoDTO toDto(Partido partido);
}