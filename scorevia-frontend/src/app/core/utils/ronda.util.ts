/**
 * Cómo se enseña la fase de un partido.
 *
 * <p>API-Football manda un único campo `round` para todo, en inglés y con dos formas muy
 * distintas: en liga es "Regular Season - 12" (de donde sale el número de jornada) y en copa
 * es "Quarter-finals", "Round of 16", "Group Stage - A" o "Final", que no tienen número.</p>
 *
 * <p>Antes solo se guardaba el número, así que la cabecera del detalle pintaba
 * "Mundial · Jornada " con el hueco vacío en cualquier partido de eliminatoria.</p>
 */

/** Fases con nombre propio, de la más larga a la más corta para que "Semi-finals" no case con "Final". */
const FASES: { patron: RegExp; etiqueta: string }[] = [
  { patron: /^group stage(\s*-\s*(.+))?$/i, etiqueta: 'Fase de grupos' },
  { patron: /^(1\/16|round of 32)/i, etiqueta: 'Dieciseisavos de final' },
  { patron: /^(1\/8|round of 16)/i, etiqueta: 'Octavos de final' },
  { patron: /^(quarter[- ]?finals?|1\/4)/i, etiqueta: 'Cuartos de final' },
  { patron: /^(semi[- ]?finals?|1\/2)/i, etiqueta: 'Semifinales' },
  { patron: /^(3rd place|third place)/i, etiqueta: 'Tercer y cuarto puesto' },
  { patron: /^(final)$/i, etiqueta: 'Final' },
  { patron: /^preliminary round/i, etiqueta: 'Ronda preliminar' },
  { patron: /^(qualifying|qualification)/i, etiqueta: 'Clasificación' },
  { patron: /^play[- ]?offs?/i, etiqueta: 'Playoff' },
  { patron: /^knockout round play[- ]?offs?/i, etiqueta: 'Playoff de eliminatorias' },
  { patron: /^(league stage|regular season)$/i, etiqueta: 'Fase regular' },
];

/**
 * El texto que se pinta debajo del marcador: "Jornada 12", "Cuartos de final", "Fase de
 * grupos · A"...
 *
 * @param ronda    el campo `round` en crudo, tal cual lo guarda el backend.
 * @param jornada  el número ya parseado, cuando lo hay.
 * @returns el texto listo para pintar, o null si no hay nada que decir — y entonces la
 *          pantalla no debe pintar ni la palabra "Jornada", que es justo lo que fallaba.
 */
export function rondaLegible(ronda?: string | null, jornada?: number | null): string | null {
  // La jornada manda cuando existe: es lo que se espera leer en una liga.
  if (jornada != null) return `Jornada ${jornada}`;

  const texto = (ronda ?? '').trim();
  if (!texto) return null;

  for (const { patron, etiqueta } of FASES) {
    const encaje = patron.exec(texto);
    if (!encaje) continue;

    // "Group Stage - A" conserva el grupo: sin él, los seis partidos de una jornada de
    // grupos se leerían todos igual.
    const sufijo = encaje[2]?.trim();
    return sufijo ? `${etiqueta} · ${sufijo}` : etiqueta;
  }

  // Una fase que no está en la lista se enseña tal cual antes que esconderla: es más
  // información que un hueco vacío, aunque venga en inglés.
  return texto;
}
