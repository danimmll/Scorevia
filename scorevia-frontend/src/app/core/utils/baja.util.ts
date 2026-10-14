import type { HistorialBaja } from '../models/estadisticas.model';

/**
 * Cuánto puede llevar una baja sin fecha de alta antes de dejar de creerse que sigue abierta.
 *
 * API-Football publica la baja cuando empieza y le pone el fin cuando el jugador vuelve, pero
 * solo si alguien vuelve a mirar esa ficha. Nosotros pedimos las bajas de un jugador cuando le
 * toca en la ronda de ingesta, así que hay 1.940 bajas sin fecha de fin, de las cuales 1.473
 * son de 2025 y una de 2014: enseñarlas todas como "en curso" es decir que hay futbolistas
 * lesionados desde hace once años.
 */
const MESES_PARA_DARLA_POR_CERRADA = 6;

/**
 * Qué poner en el hueco de la fecha de fin.
 *
 * @param baja  la baja tal y como viene del backend.
 * @param hoy   inyectable para poder fijarlo en los tests.
 */
export function textoFinBaja(baja: HistorialBaja, hoy: Date = new Date()): string {
  if (baja.fechaFin) return baja.fechaFin;
  if (!baja.fechaInicio) return 'sin fecha de alta';

  const inicio = new Date(baja.fechaInicio);
  if (Number.isNaN(inicio.getTime())) return 'sin fecha de alta';

  const limite = new Date(hoy);
  limite.setMonth(limite.getMonth() - MESES_PARA_DARLA_POR_CERRADA);

  // Una baja reciente sin alta sí es creíble: el jugador todavía está fuera. Una de hace años
  // no; lo que falta ahí es el dato del regreso, y eso es lo que se dice.
  return inicio >= limite ? 'en curso' : 'sin fecha de alta';
}

/** Si la baja se puede presentar como "está lesionado ahora mismo". */
export function bajaSigueAbierta(baja: HistorialBaja, hoy: Date = new Date()): boolean {
  return textoFinBaja(baja, hoy) === 'en curso';
}
