package estadisticas.controllers.partidos;

import estadisticas.dto.alineaciones.AlineacionesPartidoDTO;
import estadisticas.dto.partidos.BajaPartidoDTO;
import estadisticas.dto.partidos.CuotasPartidoDTO;
import estadisticas.dto.partidos.EstadisticaEquipoDTO;
import estadisticas.dto.partidos.EstadisticaJugadorPartidoDTO;
import estadisticas.dto.partidos.EventoPartidoDTO;
import estadisticas.dto.partidos.PartidoDTO;
import estadisticas.config.ValidationUtils;
import estadisticas.services.alineaciones.AlineacionService;
import estadisticas.services.bajas.BajasPartidoService;
import estadisticas.services.cuotas.CuotasService;
import estadisticas.services.partidos.PartidoServiceImpl;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/partidos")
@RequiredArgsConstructor
@Tag(name = "Partidos", description = "Operaciones relacionadas con la consulta de partidos")
public class PartidoControllerImpl implements PartidoController {

    // FIX: ninguna query del repo llevaba ORDER BY, así que "últimos N partidos"
    // devolvía orden indefinido (en la práctica, orden físico de Postgres, no por
    // fecha). Para forma reciente y H2H el orden cronológico descendente es
    // imprescindible, así que se fija aquí explícitamente.
    private static final Sort MAS_RECIENTES_PRIMERO = Sort.by("fecha").descending();
    private static final Sort PROXIMOS_PRIMERO = Sort.by("fecha").ascending();
    // Sin tope, un size=999999 forzaba a Postgres/Hibernate a materializar la tabla entera
    // en memoria en una sola petición (mismo patrón de límite que ya usan incidencias y
    // usuarios-roles en sus propios endpoints paginados).
    private static final int TAMANIO_PAGINA_MAXIMO = 100;

    private final PartidoServiceImpl service;
    private final AlineacionService alineacionService;
    private final CuotasService cuotasService;
    private final BajasPartidoService bajasService;

    @Override
    @GetMapping
    public ResponseEntity<Page<PartidoDTO>> getPartidos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String fecha) {
        int tamanio = Math.min(size, TAMANIO_PAGINA_MAXIMO);

        // Con el calendario ingerido hasta mayo de 2027, "los más recientes" son partidos
        // de dentro de ocho meses: la primera página del listado general no tiene ni un
        // partido de hoy. Por eso el filtro por fecha va aquí y no en el cliente.
        if (fecha != null && !fecha.isBlank()) {
            java.time.LocalDate dia = ValidationUtils.validarFecha(fecha);
            return ResponseEntity.ok(service.getPartidosPorFecha(dia, PageRequest.of(page, tamanio, PROXIMOS_PRIMERO)));
        }
        return ResponseEntity.ok(service.getPartidos(PageRequest.of(page, tamanio, MAS_RECIENTES_PRIMERO)));
    }

    @Override
    @GetMapping("/en-vivo")
    public ResponseEntity<List<PartidoDTO>> getPartidosEnVivo() {
        return ResponseEntity.ok(service.getPartidosEnVivo());
    }

    @Override
    @GetMapping("/{id_partido}")
    public ResponseEntity<PartidoDTO> getPartidoById(@PathVariable("id_partido") String idString) {
        Integer id = ValidationUtils.validarId(idString);
        return ResponseEntity.ok(service.getPartidoById(id));
    }

    @Override
    @GetMapping("/liga/{id_liga}")
    public ResponseEntity<Page<PartidoDTO>> getPartidosByLiga(
            @PathVariable("id_liga") String idString,
            @RequestParam(required = false) Integer temporada,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Integer id = ValidationUtils.validarId(idString);
        return ResponseEntity.ok(service.getPartidosByLiga(id, temporada, PageRequest.of(page, Math.min(size, TAMANIO_PAGINA_MAXIMO), MAS_RECIENTES_PRIMERO)));
    }

    @Override
    @GetMapping("/equipo/{id_equipo}")
    public ResponseEntity<Page<PartidoDTO>> getPartidosByEquipo(
            @PathVariable("id_equipo") String idString,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String estado) {
        Integer id = ValidationUtils.validarId(idString);
        int tamanio = Math.min(size, TAMANIO_PAGINA_MAXIMO);

        // La ficha de equipo pinta dos listas separadas y cada una necesita su propia
        // paginación, así que el filtro va aquí y no en el cliente. Los programados se
        // ordenan al revés (el más próximo primero): en una lista de partidos futuros lo
        // que interesa es el siguiente, no el más lejano.
        if ("JUGADOS".equalsIgnoreCase(estado)) {
            return ResponseEntity.ok(service.getPartidosJugadosByEquipo(
                    id, PageRequest.of(page, tamanio, MAS_RECIENTES_PRIMERO)));
        }
        if ("PROGRAMADOS".equalsIgnoreCase(estado)) {
            return ResponseEntity.ok(service.getPartidosProgramadosByEquipo(
                    id, PageRequest.of(page, tamanio, PROXIMOS_PRIMERO)));
        }
        return ResponseEntity.ok(service.getPartidosByEquipo(id, PageRequest.of(page, tamanio, MAS_RECIENTES_PRIMERO)));
    }

    @Override
    @GetMapping("/enfrentamientos")
    public ResponseEntity<Page<PartidoDTO>> getEnfrentamientosDirectos(
            @RequestParam("equipoA") String idEquipoAString,
            @RequestParam("equipoB") String idEquipoBString,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Integer idEquipoA = ValidationUtils.validarId(idEquipoAString);
        Integer idEquipoB = ValidationUtils.validarId(idEquipoBString);
        return ResponseEntity.ok(service.getEnfrentamientosDirectos(idEquipoA, idEquipoB, PageRequest.of(page, Math.min(size, TAMANIO_PAGINA_MAXIMO), MAS_RECIENTES_PRIMERO)));
    }

    @Override
    @GetMapping("/{id_partido}/estadisticas")
    public ResponseEntity<List<EstadisticaEquipoDTO>> getEstadisticasPartido(@PathVariable("id_partido") String idString) {
        Integer id = ValidationUtils.validarId(idString);
        return ResponseEntity.ok(service.getEstadisticasPartido(id));
    }

    @Override
    @GetMapping("/{id_partido}/jugadores")
    public ResponseEntity<List<EstadisticaJugadorPartidoDTO>> getEstadisticasJugadoresPartido(@PathVariable("id_partido") String idString) {
        Integer id = ValidationUtils.validarId(idString);
        return ResponseEntity.ok(service.getEstadisticasJugadoresPartido(id));
    }

    @Override
    @GetMapping("/{id_partido}/eventos")
    public ResponseEntity<List<EventoPartidoDTO>> getEventosPartido(@PathVariable("id_partido") String idString) {
        Integer id = ValidationUtils.validarId(idString);
        return ResponseEntity.ok(service.getEventosPartido(id));
    }

    @Override
    @GetMapping("/{id_partido}/cuotas")
    public ResponseEntity<CuotasPartidoDTO> getCuotasPartido(@PathVariable("id_partido") String idString) {
        Integer id = ValidationUtils.validarId(idString);
        return ResponseEntity.ok(cuotasService.getCuotasPartido(id));
    }

    @Override
    @GetMapping("/{id_partido}/bajas")
    public ResponseEntity<List<BajaPartidoDTO>> getBajasPartido(@PathVariable("id_partido") String idString) {
        Integer id = ValidationUtils.validarId(idString);
        return ResponseEntity.ok(bajasService.getBajasPartido(id));
    }

    @Override
    @GetMapping("/{id_partido}/alineaciones")
    public ResponseEntity<AlineacionesPartidoDTO> getAlineacionesPartido(@PathVariable("id_partido") String idString) {
        Integer id = ValidationUtils.validarId(idString);
        return ResponseEntity.ok(alineacionService.getAlineacionesPartido(id));
    }
}