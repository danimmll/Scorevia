import { describe, expect, it } from 'vitest';
import { compararCompeticiones, competicionesDestacadas, prioridadCompeticion } from './liga-categoria.util';
import { Liga } from '../models/estadisticas.model';

describe('prioridadCompeticion', () => {
  it('las grandes ligas pesan menos que las desconocidas', () => {
    expect(prioridadCompeticion(140, 'La Liga')).toBeLessThan(prioridadCompeticion(788, 'Liga III - Serie 5'));
    expect(prioridadCompeticion(39, 'Premier League')).toBeLessThan(prioridadCompeticion(276, 'FKF Premier League'));
  });

  it('lo juvenil y femenino cae al fondo aunque el id no esté en la lista', () => {
    const juvenil = prioridadCompeticion(1086, 'Paulista - U20');
    const desconocida = prioridadCompeticion(788, 'Liga III - Serie 5');
    expect(juvenil).toBeGreaterThan(desconocida);
  });

  it('sin id, se sigue pudiendo detectar lo juvenil por el nombre', () => {
    expect(prioridadCompeticion(undefined, 'Youth Championship'))
      .toBeGreaterThan(prioridadCompeticion(undefined, 'Eerste Divisie'));
  });
});

describe('compararCompeticiones', () => {
  it('ordena una jornada real como se espera verla', () => {
    // Las competiciones con más partidos el 2026-09-04: si se ordena por volumen, por id
    // o por orden de llegada, lo primero que se ve es el juvenil brasileño.
    const jornada = [
      { idLiga: 1086, nombre: 'Paulista - U20', pais: 'Brazil' },
      { idLiga: 276, nombre: 'FKF Premier League', pais: 'Kenya' },
      { idLiga: 238, nombre: 'Youth Championship', pais: 'Russia' },
      { idLiga: 45, nombre: 'FA Cup', pais: 'England' },
      { idLiga: 62, nombre: 'Ligue 2', pais: 'France' },
      { idLiga: 140, nombre: 'La Liga', pais: 'Spain' },
    ];

    const orden = [...jornada].sort(compararCompeticiones).map(c => c.nombre);

    expect(orden[0]).toBe('La Liga');
    expect(orden.slice(0, 3)).toContain('FA Cup');
    expect(orden.slice(0, 3)).toContain('Ligue 2');
    // Lo juvenil, al final del todo.
    expect(orden.at(-1)).toBe('Youth Championship');
    expect(orden.at(-2)).toBe('Paulista - U20');
  });

  it('a igualdad de importancia, ordena por país y nombre', () => {
    const a = { idLiga: 9001, nombre: 'Liga B', pais: 'Andorra' };
    const b = { idLiga: 9002, nombre: 'Liga A', pais: 'Zimbabwe' };
    expect(compararCompeticiones(a, b)).toBeLessThan(0);
  });
});

describe('competicionesDestacadas', () => {
  const liga = (idLiga: number, nombre: string): Liga => ({ idLiga, nombre } as Liga);

  it('devuelve las más importantes primero, en el orden de la lista', () => {
    // A propósito desordenadas a la entrada: el orden lo pone la lista de importancia.
    const catalogo = [liga(39, 'Premier League'), liga(140, 'La Liga'), liga(2, 'Champions')];
    expect(competicionesDestacadas(catalogo, 3).map(l => l.idLiga)).toEqual([140, 39, 2]);
  });

  it('se salta las que no están en el catálogo en vez de dejar huecos', () => {
    const catalogo = [liga(140, 'La Liga'), liga(2, 'Champions')];
    expect(competicionesDestacadas(catalogo, 2).map(l => l.idLiga)).toEqual([140, 2]);
  });

  it('nunca mete una competición que no sea de las destacadas', () => {
    // Si rellenara huecos con "la siguiente que haya", acabaría poniendo la regional de turno.
    const catalogo = [liga(140, 'La Liga'), liga(999999, 'Tercera regional')];
    expect(competicionesDestacadas(catalogo, 5).map(l => l.idLiga)).toEqual([140]);
  });

  it('respeta el tope pedido', () => {
    const catalogo = [140, 39, 135, 78, 61, 2].map(id => liga(id, 'x'));
    expect(competicionesDestacadas(catalogo, 3)).toHaveLength(3);
  });
});
