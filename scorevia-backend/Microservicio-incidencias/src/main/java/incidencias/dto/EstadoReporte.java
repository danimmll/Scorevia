package incidencias.dto;

/**
 * Estados válidos para un {@link ReporteDTO}.
 *
 * <p>Se usa como tipo del parámetro {@code estado} en los endpoints de moderación
 * para que Spring rechace automáticamente (HTTP 400) cualquier valor que no sea
 * uno de los definidos aquí, evitando que se persista texto libre o mal escrito
 * en la columna {@code estado} de la entidad {@code Reporte}.</p>
 */
public enum EstadoReporte {
    PENDIENTE,
    RESUELTO,
    RECHAZADO
}
