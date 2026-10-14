/**
 * Qué significa cada puesto de una clasificación, y de qué color se pinta.
 *
 * <p><b>Lo bueno: no hay que definir nada por liga.</b> API-Football manda, fila por fila, un
 * texto con lo que está en juego en esa posición, y ya se guarda en
 * `clasificaciones.estado_competicion`. En La Liga 2024/25, por ejemplo, llega
 * "Promotion - Champions League (League phase)" en los cinco primeros, "Promotion - Europa
 * League (League phase)" en el sexto y "Relegation" abajo. Estaba descargado desde hace
 * tiempo y no se pintaba en ninguna parte.</p>
 *
 * <p><b>Lo malo: el texto es libre y viene de mil maneras.</b> Solo en la temporada 2026 hay
 * `Playoffs`, `Playoff`, `Play-offs`, `Play Offs`, `Promotion Play-offs`, `Relegation`,
 * `Relegation Playoffs`, `Promotion - Serie D (Play Offs: 1/32-finals)`... Por eso esto no
 * compara cadenas: busca palabras clave y las reduce a un puñado de zonas con color.</p>
 */

/** Las zonas que se distinguen con color. El orden importa: ver `clasificarZona`. */
export type TipoZona =
  | 'CHAMPIONS'
  | 'EUROPA'
  | 'CONFERENCE'
  | 'LIBERTADORES'
  | 'SUDAMERICANA'
  | 'ASCENSO'
  | 'PLAYOFF'
  | 'PROMOCION'
  | 'DESCENSO';

export interface Zona {
  tipo: TipoZona;
  /** Lo que se lee en la leyenda. */
  etiqueta: string;
}

/**
 * Reglas, de la más específica a la más general.
 *
 * <p>El orden es lo que hace que funcionen los casos mezclados: "Relegation Playoffs" es
 * promoción de descenso y no un playoff de ascenso, y "Promotion - Conference League
 * (Qualification)" es Conference y no un ascenso de categoría. Por eso las competiciones
 * europeas y el descenso van antes que las palabras genéricas.</p>
 */
const REGLAS: { patron: RegExp; tipo: TipoZona; etiqueta: string }[] = [
  { patron: /champions league/i, tipo: 'CHAMPIONS', etiqueta: 'Champions League' },
  { patron: /europa league/i, tipo: 'EUROPA', etiqueta: 'Europa League' },
  { patron: /conference league/i, tipo: 'CONFERENCE', etiqueta: 'Conference League' },
  { patron: /libertadores/i, tipo: 'LIBERTADORES', etiqueta: 'Copa Libertadores' },
  { patron: /sudamericana/i, tipo: 'SUDAMERICANA', etiqueta: 'Copa Sudamericana' },

  // "Relegation Playoffs" / "Relegation Play-offs": se juega el descenso, no el ascenso.
  { patron: /relegation[\s-]*play[\s-]*offs?/i, tipo: 'PROMOCION', etiqueta: 'Promoción de descenso' },
  { patron: /relegation|descenso/i, tipo: 'DESCENSO', etiqueta: 'Descenso' },

  { patron: /promotion[\s-]*play[\s-]*offs?/i, tipo: 'PLAYOFF', etiqueta: 'Playoff de ascenso' },
  { patron: /promotion|ascenso/i, tipo: 'ASCENSO', etiqueta: 'Ascenso' },

  // Lo más genérico al final: en la MLS o en Argentina, "Playoffs" a secas es la fase por el
  // título, no un ascenso.
  { patron: /play[\s-]*offs?/i, tipo: 'PLAYOFF', etiqueta: 'Playoffs' },
];

/**
 * De la cadena en crudo de la API a una zona con color, o null si no dice nada.
 *
 * <p>La media tabla no tiene zona y llega vacía: eso es correcto, no es un dato que falte.</p>
 */
export function clasificarZona(estadoCompeticion?: string | null): Zona | null {
  const texto = (estadoCompeticion ?? '').trim();
  if (!texto) return null;

  for (const regla of REGLAS) {
    if (regla.patron.test(texto)) {
      return { tipo: regla.tipo, etiqueta: regla.etiqueta };
    }
  }
  return null;
}

/**
 * Las zonas presentes en una tabla, sin repetir y en el orden en que aparecen.
 *
 * <p>La leyenda se construye con esto y no con la lista entera de zonas posibles: en una
 * Segunda no pinta nada explicar qué es el color de la Champions.</p>
 */
export function zonasPresentes(estados: (string | null | undefined)[]): Zona[] {
  const vistas = new Map<TipoZona, Zona>();
  for (const estado of estados) {
    const zona = clasificarZona(estado);
    if (zona && !vistas.has(zona.tipo)) vistas.set(zona.tipo, zona);
  }
  return [...vistas.values()];
}

/**
 * ¿Se decide el título en un playoff en esta competición?
 *
 * <p>Importa para no llamar "campeón" a quien no lo es: en la MLS o en Argentina, acabar
 * primero en la fase regular no da el título. Cuando la propia API marca posiciones de
 * playoff, el primero se presenta como líder de la fase regular y no como campeón.</p>
 */
export function hayPlayoffPorElTitulo(estados: (string | null | undefined)[]): boolean {
  return zonasPresentes(estados).some(z => z.tipo === 'PLAYOFF');
}
