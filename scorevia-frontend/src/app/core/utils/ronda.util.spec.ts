import { describe, expect, it } from 'vitest';
import { rondaLegible } from './ronda.util';

describe('rondaLegible', () => {

  it('en liga manda el número de jornada', () => {
    expect(rondaLegible('Regular Season - 12', 12)).toBe('Jornada 12');
  });

  it('traduce las fases de una eliminatoria', () => {
    expect(rondaLegible('Quarter-finals', null)).toBe('Cuartos de final');
    expect(rondaLegible('Round of 16', null)).toBe('Octavos de final');
    expect(rondaLegible('Semi-finals', null)).toBe('Semifinales');
    expect(rondaLegible('Final', null)).toBe('Final');
  });

  it('"Semi-finals" no se confunde con "Final"', () => {
    // El orden de los patrones importa: "Final" a secas va el último a propósito.
    expect(rondaLegible('Semi-finals', null)).not.toBe('Final');
  });

  it('la fase de grupos conserva el grupo', () => {
    // Sin el sufijo, los partidos de todos los grupos se leerían exactamente igual.
    expect(rondaLegible('Group Stage - A', null)).toBe('Fase de grupos · A');
    expect(rondaLegible('Group Stage', null)).toBe('Fase de grupos');
  });

  it('una fase desconocida se enseña tal cual, no se esconde', () => {
    expect(rondaLegible('Relegation Round', null)).toBe('Relegation Round');
  });

  it('sin fase ni jornada no devuelve nada', () => {
    // Este es el fallo que arregla: la cabecera pintaba "Jornada " con el hueco vacío en
    // cualquier partido de copa. Con null, la pantalla no pinta nada.
    expect(rondaLegible(null, null)).toBeNull();
    expect(rondaLegible('   ', undefined)).toBeNull();
  });
});
