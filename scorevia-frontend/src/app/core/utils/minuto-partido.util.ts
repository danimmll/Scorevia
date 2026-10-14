/**
 * Cómo se enseña el momento de un partido que se está jugando.
 *
 * El backend manda dos cosas: `estado` agrupado (EN_JUEGO) y `estadoCorto`, que es el código
 * original de API-Football. El agrupado es el que usa la lógica, pero para el marcador no
 * basta: en EN_JUEGO caben el descanso, la prórroga, la tanda de penaltis y un partido
 * interrumpido por tormenta, y pintarlos todos igual con un número al lado es mentir.
 *
 * Los códigos salen de la tabla de estados de la documentación de API-Football.
 */

/** Códigos en los que el reloj está parado: el minuto no se enseña porque no corre. */
const RELOJ_PARADO: Record<string, string> = {
  HT: 'match_phase_half_time',
  BT: 'match_phase_break_time',
  P: 'match_phase_penalties',
  SUSP: 'match_phase_suspended',
  INT: 'match_phase_interrupted',
};

/** Códigos que sí van con minuto, y cómo se llama esa parte del partido. */
const CON_MINUTO: Record<string, string> = {
  '1H': 'match_phase_first_half',
  '2H': 'match_phase_second_half',
  ET: 'match_phase_extra_time',
  LIVE: 'match_phase_live',
};

/**
 * Lo que va al lado del marcador, como CLAVE de traduccion y sus parametros.
 *
 * Devuelve `{clave, params}` y no un texto ya montado porque hay dos casos distintos que
 * la plantilla tiene que poder pintar igual: una fase con nombre propio ("Descanso") y un
 * minuto ("67'"). Antes se devolvia el texto en espanol directamente; ahora quien decide
 * como se lee es el catalogo, y aqui solo se decide QUE se dice.
 *
 * Null si el partido no se esta jugando, para que la plantilla no repita la condicion:
 * `@if (etiquetaMinuto(p); as m)`.
 */
export interface EtiquetaMinuto {
  clave: string;
  params?: Record<string, unknown>;
}

export function etiquetaMinuto(partido: {
  estado?: string;
  estadoCorto?: string | null;
  minuto?: number | null;
}): EtiquetaMinuto | null {
  if (partido.estado !== 'EN_JUEGO') return null;

  const codigo = partido.estadoCorto ?? '';
  if (RELOJ_PARADO[codigo]) return { clave: RELOJ_PARADO[codigo] };

  // El minuto es lo que manda cuando lo hay: `67'` se entiende sin mas contexto.
  if (partido.minuto != null) return { clave: 'minute_mark', params: { n: partido.minuto } };

  // Sin minuto y con el reloj corriendo no se ensena nada.
  //
  // Antes se caia a la fase ("2ª parte", "En juego"). Suena util y no lo es: al lado del
  // punto rojo de "en directo", "2ª parte" no dice NADA que no se sepa ya, y ocupa el sitio
  // donde el usuario busca el minuto. Se pedia expresamente ver el minuto y solo el minuto.
  // Con null, la plantilla deja el hueco y el punto rojo sigue diciendo que esta en juego.
  //
  // Las fases con el reloj parado (arriba, RELOJ_PARADO) si se siguen ensenando: en el
  // descanso o en la tanda no hay ningun minuto que pintar, y ahi la fase es el dato.
  return null;
}

/**
 * El nombre largo de la fase, para el `title` de un tooltip: "2ª parte", "Prórroga".
 * Null cuando la etiqueta corta ya lo dice todo (Descanso, Penaltis...).
 */
export function faseDelPartido(partido: { estado?: string; estadoCorto?: string | null }): string | null {
  if (partido.estado !== 'EN_JUEGO') return null;
  return CON_MINUTO[partido.estadoCorto ?? ''] ?? null;
}
