package incidencias.controllers.incidencias;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import incidencias.dto.IncidenciaDTO;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controller interface for managing incidents.
 * Defines endpoints for retrieving and filtering incidents.
 */
@Tag(name = "Incidencias", description = "Operaciones relacionadas con la gestión y consulta de incidencias")
public interface IncidenciaController {

    /**
     * Retrieves all registered incidents with pagination and sorting.
     *
     * @param page page number (default 0)
     * @param size number of items per page (default 5)
     * @param sort field to sort by (default "fecha")
     * @param dir sort direction ("asc" or "desc", default "desc")
     * @return list of incident DTOs
     */
    @Operation(summary = "Obtener todas las incidencias", description = "Recupera todas las incidencias registradas con soporte de paginación y ordenamiento.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Incidencias recuperadas correctamente"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "403", description = "Acceso prohibido")
    })
    ResponseEntity<Page<IncidenciaDTO>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "fecha") String sort,
            @RequestParam(defaultValue = "desc") String dir);

    /**
     * Retrieves an incident by its ID.
     *
     * @param idString incident ID in string format
     * @return incident DTO
     */
    @Operation(summary = "Obtener incidencia por ID", description = "Recupera una incidencia específica usando su identificador.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Incidencia recuperada correctamente"),
        @ApiResponse(responseCode = "404", description = "Incidencia no encontrada"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "403", description = "Acceso prohibido")
    })
    ResponseEntity<IncidenciaDTO> getById(@PathVariable("id_incidencia") String idString);

    /**
     * Retrieves incidents associated with a specific user, paginated.
     *
     * @param idString user ID in string format
     * @param page page number (default 0)
     * @param size number of items per page (default 5)
     * @param sort field to sort by (default "fecha")
     * @param dir sort direction ("asc" or "desc", default "desc")
     * @return list of incident DTOs
     */
    @Operation(summary = "Obtener incidencias por usuario", description = "Recupera todas las incidencias asociadas a un usuario específico, con paginación.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Incidencias recuperadas correctamente"),
        @ApiResponse(responseCode = "404", description = "No se encontraron incidencias para el usuario"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "403", description = "Acceso prohibido")
    })
    ResponseEntity<Page<IncidenciaDTO>> getByUsuario(@PathVariable("id_usuario") String idString,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "fecha") String sort,
            @RequestParam(defaultValue = "desc") String dir);

    /**
     * Retrieves incidents associated with a specific class, paginated.
     *
     * @param clase class name
     * @param page page number (default 0)
     * @param size number of items per page (default 5)
     * @param sort field to sort by (default "fecha")
     * @param dir sort direction ("asc" or "desc", default "desc")
     * @return list of incident DTOs
     */
    @Operation(summary = "Obtener incidencias por clase", description = "Recupera todas las incidencias asociadas a una clase específica, con paginación.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Incidencias recuperadas correctamente"),
        @ApiResponse(responseCode = "404", description = "No se encontraron incidencias para la clase"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "403", description = "Acceso prohibido")
    })
    ResponseEntity<Page<IncidenciaDTO>> getByClase(@PathVariable String clase,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "fecha") String sort,
            @RequestParam(defaultValue = "desc") String dir);

    /**
     * Retrieves incidents associated with a specific method, paginated.
     *
     * @param metodo method name
     * @param page page number (default 0)
     * @param size number of items per page (default 5)
     * @param sort field to sort by (default "fecha")
     * @param dir sort direction ("asc" or "desc", default "desc")
     * @return list of incident DTOs
     */
    @Operation(summary = "Obtener incidencias por método", description = "Recupera todas las incidencias asociadas a un método específico, con paginación.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Incidencias recuperadas correctamente"),
        @ApiResponse(responseCode = "404", description = "No se encontraron incidencias para el método"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "403", description = "Acceso prohibido")
    })
    ResponseEntity<Page<IncidenciaDTO>> getByMetodo(@PathVariable String metodo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "fecha") String sort,
            @RequestParam(defaultValue = "desc") String dir);
    
    /**
     * Creates a new incident.
     *
     * @param incidenciaDTO DTO containing incident data
     * @return created incident DTO
     */
    @Operation(summary = "Crear incidencia", description = "Registra una nueva incidencia en el sistema.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Incidencia creada correctamente"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos en la solicitud"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "403", description = "Acceso prohibido")
    })
    ResponseEntity<IncidenciaDTO> crearIncidencia(@Valid @RequestBody IncidenciaDTO incidenciaDTO);
}
