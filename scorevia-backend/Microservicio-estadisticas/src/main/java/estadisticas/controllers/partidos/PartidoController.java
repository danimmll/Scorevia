package estadisticas.controllers.partidos;

import estadisticas.dto.alineaciones.AlineacionesPartidoDTO;
import estadisticas.dto.partidos.BajaPartidoDTO;
import estadisticas.dto.partidos.CuotasPartidoDTO;
import estadisticas.dto.partidos.EstadisticaEquipoDTO;
import estadisticas.dto.partidos.EstadisticaJugadorPartidoDTO;
import estadisticas.dto.partidos.EventoPartidoDTO;
import estadisticas.dto.partidos.PartidoDTO;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Partidos", description = "Operaciones relacionadas con la consulta de partidos")
public interface PartidoController {

    @Operation(summary = "Obtener una lista paginada de todos los partidos")
    ResponseEntity<Page<PartidoDTO>> getPartidos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String fecha);

    @Operation(summary = "Obtener los partidos que están en juego ahora mismo")
    ResponseEntity<List<PartidoDTO>> getPartidosEnVivo();

    @Operation(summary = "Obtener los detalles de un partido específico por su ID")
    ResponseEntity<PartidoDTO> getPartidoById(
            @PathVariable("id_partido") String idString);

    @Operation(summary = "Obtener partidos por el ID de la liga, opcionalmente filtrados por temporada")
    ResponseEntity<Page<PartidoDTO>> getPartidosByLiga(
            @PathVariable("id_liga") String idString,
            @RequestParam(required = false) Integer temporada,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size);

    @Operation(summary = "Obtener partidos por el ID de un equipo",
            description = "Con estado=JUGADOS devuelve solo los que ya tienen marcador (más recientes primero); "
                    + "con estado=PROGRAMADOS, solo los que faltan por jugar (el más próximo primero). "
                    + "Sin estado, devuelve todos.")
    ResponseEntity<Page<PartidoDTO>> getPartidosByEquipo(
            @PathVariable("id_equipo") String idString,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String estado);

    @Operation(summary = "Obtener el historial de enfrentamientos entre dos equipos")
    ResponseEntity<Page<PartidoDTO>> getEnfrentamientosDirectos(
            @RequestParam("equipoA") String idEquipoAString,
            @RequestParam("equipoB") String idEquipoBString,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size);

    @Operation(summary = "Obtener las estadísticas de los equipos en un partido por su ID")
    ResponseEntity<List<EstadisticaEquipoDTO>> getEstadisticasPartido(
            @PathVariable("id_partido") String idString);

    @Operation(summary = "Obtener las estadísticas de los jugadores en un partido por su ID")
    ResponseEntity<List<EstadisticaJugadorPartidoDTO>> getEstadisticasJugadoresPartido(
            @PathVariable("id_partido") String idString);

    @Operation(summary = "Obtener el timeline de eventos de un partido (goles, tarjetas, cambios)")
    ResponseEntity<List<EventoPartidoDTO>> getEventosPartido(
            @PathVariable("id_partido") String idString);

    @Operation(summary = "Obtener las cuotas de un partido",
            description = "Cuotas de una casa de apuestas (ganador, más/menos de 2.5 goles y ambos marcan). " +
                    "Se piden a API-Football en el momento y se cachean: solo existen entre 14 días antes y " +
                    "7 días después del partido, fuera de esa ventana devuelve hayCuotas=false sin llamar a la API.")
    ResponseEntity<CuotasPartidoDTO> getCuotasPartido(@PathVariable("id_partido") String idString);

    @Operation(summary = "Obtener las bajas de un partido",
            description = "Lesionados, sancionados y dudas de los dos equipos para ese partido concreto.")
    ResponseEntity<List<BajaPartidoDTO>> getBajasPartido(@PathVariable("id_partido") String idString);

    @Operation(summary = "Obtener las alineaciones de un partido",
            description = "Los dos onces titulares con su dibujo (deducido contando titulares por línea), "
                    + "los banquillos, el entrenador de cada equipo en esa temporada y el árbitro. "
                    + "Sale de datos ya ingeridos: no gasta ninguna petición de API-Football.")
    ResponseEntity<AlineacionesPartidoDTO> getAlineacionesPartido(
            @PathVariable("id_partido") String idString);
}