/**
 * Barras comparativas "A contra B".
 *
 * Las usan la ficha de partido (posesión, tiros, córners...) y el comparador de equipos
 * (goles a favor/en contra en los enfrentamientos directos). Vive aquí y no duplicado en
 * cada componente porque el caso raro —los dos valores a cero— hay que resolverlo igual en
 * los dos sitios: sin este reparto al 50%, una división por cero deja la barra en NaN y el
 * navegador la pinta vacía del todo, que parece un fallo de carga.
 */
export interface FilaComparativa {
  label: string;
  a?: number | null;
  b?: number | null;
  /** Para medias y porcentajes, que no deben redondearse a entero al mostrarlos. */
  decimales?: number;
}

/** Porcentaje de la barra que le corresponde al lado A. 50 si no hay nada que repartir. */
export function porcentajeLadoA(valorA?: number | null, valorB?: number | null): number {
  const a = valorA ?? 0;
  const b = valorB ?? 0;
  if (a + b === 0) return 50;
  return Math.round((a / (a + b)) * 100);
}

/** Formatea el valor de una fila respetando sus decimales. */
export function valorComparativa(valor: number | null | undefined, decimales = 0): string {
  return (valor ?? 0).toFixed(decimales);
}
