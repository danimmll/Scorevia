package estadisticas.services.external;

/**
 * Cómo acabó el intento de traerse los datos de un partido concreto.
 *
 * <p>Existe porque quien encola el partido al acabar ({@code ColaPostPartido}) necesita
 * distinguir tres cosas que antes se confundían en un simple boolean: si ya está hecho, si
 * hay que volver a intentarlo dentro de un rato, o si no hay nada que esperar y reintentar
 * solo gastaría cuota.</p>
 */
public enum ResultadoIngestaPartido {

    /** Estadísticas de equipo y de jugadores guardadas. No hay nada más que hacer. */
    OK,

    /**
     * La API respondió, pero todavía no tiene los datos de este partido. Es lo normal en los
     * primeros minutos tras el pitido final: toca esperar y volver.
     */
    SIN_DATOS,

    /**
     * La competición no publica estadísticas (lo dice {@code ligas.cobertura_stats_partido},
     * que sale de {@code /leagues}). No es un fallo ni algo que vaya a aparecer más tarde:
     * reintentar es tirar peticiones.
     */
    SIN_COBERTURA,

    /** El partido no está en la base. Se descarta el encolado. */
    NO_ENCONTRADO,

    /** Fallo al hablar con la API o al guardar. Merece reintento. */
    ERROR
}
