import { Signal, WritableSignal, computed, signal } from '@angular/core';

/** Lo que hace falta para pintar una lista paginada en cliente. */
export interface PaginacionCliente<T> {
  /** Página actual, en base 0. Se escribe directamente desde `(cambio)` del paginador. */
  pagina: WritableSignal<number>;
  totalPaginas: Signal<number>;
  /** Los elementos de la página actual. */
  visibles: Signal<T[]>;
}

/**
 * Pagina en cliente una lista que ya está entera en memoria.
 *
 * Existe porque el mismo trío de señales —página, total y rodaja— estaba a punto de
 * escribirse por cuarta y quinta vez en pantallas distintas, siempre igual. Y porque el bug
 * que trae de cabeza a estas listas es siempre el mismo: al cambiar el filtro o el elemento
 * que se está mirando, la página se queda donde estaba y la lista sale vacía porque la nueva
 * tiene menos páginas que la anterior. Aquí eso se corrige solo.
 *
 * Es para listas que YA vienen completas del backend (el palmarés de un entrenador, la
 * plantilla de una temporada). Cuando cada página es una llamada al servidor no sirve: eso
 * lo lleva cada componente con su propio `cargarPagina`.
 *
 * @param origen  la lista completa
 * @param porPagina cuántos elementos por página
 */
export function paginarEnCliente<T>(origen: Signal<T[]>, porPagina: number): PaginacionCliente<T> {
  const pagina = signal(0);

  const totalPaginas = computed(() => Math.ceil(origen().length / porPagina));

  const visibles = computed(() => {
    const total = totalPaginas();

    // Si la lista encogió por debajo de la página en la que estábamos, se vuelve a la última
    // que existe en vez de devolver una rodaja vacía. Pasa constantemente: se filtra, se
    // cambia de temporada, se abre otro jugador... y la lista se quedaba en blanco hasta que
    // el usuario pulsaba "anterior" a ciegas.
    const actual = total === 0 ? 0 : Math.min(pagina(), total - 1);
    const desde = actual * porPagina;
    return origen().slice(desde, desde + porPagina);
  });

  return { pagina, totalPaginas, visibles };
}
