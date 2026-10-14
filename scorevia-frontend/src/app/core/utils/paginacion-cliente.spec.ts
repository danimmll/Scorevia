import { describe, expect, it } from 'vitest';
import { signal } from '@angular/core';
import { paginarEnCliente } from './paginacion-cliente';

describe('paginarEnCliente', () => {
  const lista = (n: number) => Array.from({ length: n }, (_, i) => i);

  it('parte la lista en páginas del tamaño pedido', () => {
    const origen = signal(lista(25));
    const p = paginarEnCliente(origen, 10);

    expect(p.totalPaginas()).toBe(3);
    expect(p.visibles()).toEqual([0, 1, 2, 3, 4, 5, 6, 7, 8, 9]);

    p.pagina.set(2);
    expect(p.visibles()).toEqual([20, 21, 22, 23, 24]);
  });

  it('si la lista encoge, no deja la página en blanco', () => {
    // Es el bug clásico de estas listas: se filtra o se cambia de temporada estando en la
    // página 5, la lista nueva tiene 2, y la pantalla se queda vacía sin explicación.
    const origen = signal(lista(50));
    const p = paginarEnCliente(origen, 10);
    p.pagina.set(4);
    expect(p.visibles()).toHaveLength(10);

    origen.set(lista(12));

    expect(p.totalPaginas()).toBe(2);
    expect(p.visibles()).toEqual([10, 11]);
  });

  it('una lista vacía no rompe', () => {
    const p = paginarEnCliente(signal<number[]>([]), 10);

    expect(p.totalPaginas()).toBe(0);
    expect(p.visibles()).toEqual([]);
  });

  it('una lista más corta que la página cabe entera', () => {
    const p = paginarEnCliente(signal(lista(3)), 10);

    expect(p.totalPaginas()).toBe(1);
    expect(p.visibles()).toEqual([0, 1, 2]);
  });

  it('reacciona a que cambie la lista de origen', () => {
    const origen = signal(lista(5));
    const p = paginarEnCliente(origen, 10);
    expect(p.visibles()).toHaveLength(5);

    origen.set(lista(8));
    expect(p.visibles()).toHaveLength(8);
  });
});
