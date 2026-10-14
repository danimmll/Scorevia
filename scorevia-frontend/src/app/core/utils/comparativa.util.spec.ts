import { describe, expect, it } from 'vitest';
import { porcentajeLadoA, valorComparativa } from './comparativa.util';

describe('porcentajeLadoA', () => {
  it('reparte proporcionalmente', () => {
    expect(porcentajeLadoA(3, 1)).toBe(75);
    expect(porcentajeLadoA(1, 1)).toBe(50);
    expect(porcentajeLadoA(0, 4)).toBe(0);
  });

  it('con los dos a cero reparte al 50, no divide por cero', () => {
    // Sin esto sale NaN y el navegador pinta la barra vacía, que parece un fallo de carga.
    // Pasa de verdad: tarjetas rojas es 0-0 en la inmensa mayoría de partidos.
    expect(porcentajeLadoA(0, 0)).toBe(50);
  });

  it('trata null y undefined como cero', () => {
    expect(porcentajeLadoA(null, 2)).toBe(0);
    expect(porcentajeLadoA(undefined, undefined)).toBe(50);
    expect(porcentajeLadoA(5, null)).toBe(100);
  });
});

describe('valorComparativa', () => {
  it('sin decimales por defecto', () => {
    expect(valorComparativa(7)).toBe('7');
  });

  it('respeta los decimales pedidos, para medias', () => {
    expect(valorComparativa(1.6666, 2)).toBe('1.67');
  });

  it('un valor ausente se muestra como cero, no como vacío', () => {
    expect(valorComparativa(null)).toBe('0');
    expect(valorComparativa(undefined, 2)).toBe('0.00');
  });
});
