import type { Liga } from '../models/estadisticas.model';

/**
 * El país "World" agrupa ~150 competiciones (Champions League, Mundial, pero también
 * decenas de categorías juveniles/femeninas/clasificatorias) que la API-Football no separa
 * por tipo -- solo por país, y "World" es el país que le pone a todo lo que no es una liga
 * doméstica. Sin esto, el selector de "World" es una lista plana imposible de recorrer.
 *
 * Heurística por nombre, no hay campo de categoría real en el backend. Orden de prioridad:
 * las categorías menores/femeninas se comprueban primero porque sus nombres a veces
 * contienen palabras de competiciones "grandes" (p.ej. "U20 World Cup").
 */
const CATEGORIA_MENORES = 'Categorías menores y femenino';
const CATEGORIA_CLUBES = 'Competiciones de clubes';
const CATEGORIA_SELECCIONES = 'Selecciones nacionales';
const CATEGORIA_OTRAS = 'Otras competiciones';

export const ORDEN_CATEGORIAS_LIGA = [CATEGORIA_CLUBES, CATEGORIA_SELECCIONES, CATEGORIA_OTRAS, CATEGORIA_MENORES];

export function categoriaLiga(nombre: string): string {
  const n = nombre.toLowerCase();

  // Además del formato "U20" hay que reconocer la palabra escrita: "Youth Championship"
  // (Rusia) y "Reserve Pro League" (Bélgica) tienen partidos casi a diario y se colaban
  // entre las competiciones grandes porque el patrón solo cubría "U18" y "women".
  // El \b de "junior" evita comerse nombres de club como Atlético Junior; aquí solo
  // llegan nombres de competición, pero la lista se amplía con facilidad y más vale.
  if (/\bu(1[5-9]|2[0-3])\b|\bsub-?\d{2}\b|youth|juvenil|\bjuniors?\b|reserv|academy|women|femenin/.test(n)) {
    return CATEGORIA_MENORES;
  }

  if (n.includes('champions league') || n.includes('europa league') || n.includes('conference league')
    || n.includes('libertadores') || n.includes('sudamericana') || n.includes('club world cup')
    || n.includes('recopa')) {
    return CATEGORIA_CLUBES;
  }

  if (n.includes('world cup') || n.includes('nations league') || n.includes('copa america')
    || n.includes('euro championship') || n.includes('qualification') || n.includes('friendlies')
    || n.includes('afc ') || n.includes('caf ') || n.includes('concacaf') || n.includes('confederations')) {
    return CATEGORIA_SELECCIONES;
  }

  return CATEGORIA_OTRAS;
}

// IDs de API-Football de ligas conocidas, para elegir un default razonable al cargar una
// página en vez de "lo primero que devuelva /api/v1/ligas" (sin orden garantizado, podía
// caer en cualquier competición minúscula y desconocida).
const IDS_LIGAS_DESTACADAS = [140, 39, 2, 135, 78, 61]; // La Liga, Premier, Champions, Serie A, Bundesliga, Ligue 1

/** Liga "de bienvenida" razonable para seleccionar por defecto al entrar a una pantalla. */
export function ligaDestacadaPorDefecto(ligas: Liga[]): Liga | null {
  for (const id of IDS_LIGAS_DESTACADAS) {
    const encontrada = ligas.find(l => l.idLiga === id);
    if (encontrada) return encontrada;
  }
  return ligas[0] ?? null;
}

export interface GrupoLigas {
  categoria: string;
  ligas: Liga[];
}

/** Agrupa una lista de ligas ya filtrada por país en las categorías de arriba, en orden fijo. */
export function agruparLigasPorCategoria(ligas: Liga[]): GrupoLigas[] {
  const mapa = new Map<string, Liga[]>();
  for (const liga of ligas) {
    const categoria = categoriaLiga(liga.nombre ?? '');
    const lista = mapa.get(categoria) ?? [];
    lista.push(liga);
    mapa.set(categoria, lista);
  }
  return ORDEN_CATEGORIAS_LIGA
    .filter(c => mapa.has(c))
    .map(categoria => ({
      categoria,
      ligas: (mapa.get(categoria) ?? []).sort((a, b) => (a.nombre ?? '').localeCompare(b.nombre ?? ''))
    }));
}

// ---------------------------------------------------------------------------
// Orden de importancia de competiciones
// ---------------------------------------------------------------------------
// Un día cualquiera, lo que más partidos tiene no es lo que más interesa: el
// 2026-09-04 los tres primeros puestos por volumen eran "Paulista U20" (17),
// la liga de Kenia (9) y el juvenil ruso (8), mientras la FA Cup y la Ligue 2
// quedaban enterradas más abajo. Ordenar por número de partidos, por id o por
// orden de llegada de la API deja siempre la portada así.
//
// No hay ningún campo de "importancia" en API-Football, así que va a mano por
// id conocido. Lo que no esté en la lista se ordena después por país y nombre,
// y lo juvenil/femenino baja del todo (que no es un juicio de valor: es que son
// cientos de competiciones y ninguna es lo que alguien viene a mirar de primeras).

const COMPETICIONES_POR_IMPORTANCIA: number[] = [
  // Las cinco grandes ligas europeas
  140, 39, 135, 78, 61,
  // Competición europea y sudamericana de clubes
  2, 3, 848, 13, 11,
  // Grandes torneos de selecciones
  1, 4, 9, 6, 5,
  // Copas nacionales de las cinco grandes
  143, 45, 137, 81, 66,
  // Segundas divisiones de las cinco grandes
  141, 40, 136, 79, 62,
  // Otras ligas de primer nivel muy seguidas
  88, 94, 203, 71, 128, 253, 262, 197, 144, 218,
];

const PESO_NO_LISTADA = COMPETICIONES_POR_IMPORTANCIA.length;
const PESO_MENORES = PESO_NO_LISTADA + 1;

/**
 * Peso de orden de una competición: cuanto más bajo, más arriba sale.
 *
 * Se le pasa el nombre además del id porque lo juvenil/femenino se detecta por nombre
 * (ver `categoriaLiga`) y hay cientos de ids que no merece la pena enumerar.
 */
export function prioridadCompeticion(idLiga: number | undefined, nombre: string | undefined): number {
  if (nombre && categoriaLiga(nombre) === CATEGORIA_MENORES) return PESO_MENORES;
  if (idLiga == null) return PESO_NO_LISTADA;
  const posicion = COMPETICIONES_POR_IMPORTANCIA.indexOf(idLiga);
  return posicion === -1 ? PESO_NO_LISTADA : posicion;
}

/**
 * Las competiciones más seguidas, en orden, para los accesos directos de la barra lateral.
 *
 * Se apoya en la misma lista de importancia que ordena los partidos del día, así que el
 * criterio es uno solo en toda la web: si mañana sube o baja una competición ahí, cambia en
 * los dos sitios a la vez.
 *
 * Solo devuelve las que existen de verdad en el catálogo recibido, y nunca una que no esté en
 * la lista de importancia: rellenar los huecos con "la siguiente que haya" acabaría poniendo
 * la tercera regional de turno como acceso directo.
 */
export function competicionesDestacadas(ligas: Liga[], cuantas = 8): Liga[] {
  const porId = new Map(ligas.map(l => [l.idLiga, l]));
  const destacadas: Liga[] = [];
  for (const id of COMPETICIONES_POR_IMPORTANCIA) {
    const liga = porId.get(id);
    if (liga) destacadas.push(liga);
    if (destacadas.length === cuantas) break;
  }
  return destacadas;
}

/** Compara dos competiciones por importancia, y a igualdad por país y nombre. */
export function compararCompeticiones(
  a: { idLiga?: number; nombre?: string; pais?: string },
  b: { idLiga?: number; nombre?: string; pais?: string },
): number {
  const diferencia = prioridadCompeticion(a.idLiga, a.nombre) - prioridadCompeticion(b.idLiga, b.nombre);
  if (diferencia !== 0) return diferencia;
  return (a.pais ?? '').localeCompare(b.pais ?? '')
    || (a.nombre ?? '').localeCompare(b.nombre ?? '');
}
