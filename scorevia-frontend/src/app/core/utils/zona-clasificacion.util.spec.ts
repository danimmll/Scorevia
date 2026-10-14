import { describe, expect, it } from 'vitest';
import { clasificarZona, hayPlayoffPorElTitulo, zonasPresentes } from './zona-clasificacion.util';

describe('clasificarZona', () => {

  it('reconoce las competiciones europeas tal y como las manda la API', () => {
    // Textos reales de La Liga 2024/25 en la base.
    expect(clasificarZona('Promotion - Champions League (League phase)')?.tipo).toBe('CHAMPIONS');
    expect(clasificarZona('Promotion - Europa League (League phase)')?.tipo).toBe('EUROPA');
    expect(clasificarZona('Promotion - Conference League (Qualification)')?.tipo).toBe('CONFERENCE');
  });

  it('la competición europea gana al "Promotion" que lleva delante', () => {
    // Si "promotion" se mirara primero, las plazas de Champions saldrían como ascenso.
    expect(clasificarZona('Promotion - Champions League (League phase)')?.tipo).not.toBe('ASCENSO');
  });

  it('"Relegation Playoffs" es promoción de descenso, no playoff de ascenso', () => {
    expect(clasificarZona('Relegation Playoffs')?.tipo).toBe('PROMOCION');
    expect(clasificarZona('Relegation Play-offs')?.tipo).toBe('PROMOCION');
    expect(clasificarZona('Relegation')?.tipo).toBe('DESCENSO');
  });

  it('aguanta las mil formas de escribir playoff que manda la API', () => {
    for (const variante of ['Playoffs', 'Playoff', 'Play-offs', 'Play Offs']) {
      expect(clasificarZona(variante)?.tipo).toBe('PLAYOFF');
    }
    expect(clasificarZona('Promotion Play-offs')?.tipo).toBe('PLAYOFF');
  });

  it('reconoce las sudamericanas', () => {
    expect(clasificarZona('Promotion - CONMEBOL Libertadores')?.tipo).toBe('LIBERTADORES');
    expect(clasificarZona('Promotion - CONMEBOL Sudamericana')?.tipo).toBe('SUDAMERICANA');
  });

  it('la media tabla no tiene zona, y eso no es un dato que falte', () => {
    expect(clasificarZona(null)).toBeNull();
    expect(clasificarZona('')).toBeNull();
    expect(clasificarZona('   ')).toBeNull();
  });

  it('un texto que no encaja con nada no inventa una zona', () => {
    expect(clasificarZona('Placement matches')).toBeNull();
  });
});

describe('zonasPresentes', () => {

  it('devuelve cada zona una sola vez, en el orden de la tabla', () => {
    const zonas = zonasPresentes([
      'Promotion - Champions League (League phase)',
      'Promotion - Champions League (League phase)',
      'Promotion - Europa League (League phase)',
      null,
      'Relegation'
    ]);
    expect(zonas.map(z => z.tipo)).toEqual(['CHAMPIONS', 'EUROPA', 'DESCENSO']);
  });

  it('sin zonas devuelve lista vacía, y así la leyenda no se pinta', () => {
    expect(zonasPresentes([null, undefined, ''])).toEqual([]);
  });
});

describe('hayPlayoffPorElTitulo', () => {

  it('detecta las ligas donde el título se juega en playoff', () => {
    // En la MLS o en Argentina, acabar primero en la fase regular no da el título: por eso
    // ahí el primero se presenta como líder de la fase regular y no como campeón.
    expect(hayPlayoffPorElTitulo(['Playoffs', null, 'Relegation'])).toBe(true);
  });

  it('una liga normal no lo tiene', () => {
    expect(hayPlayoffPorElTitulo([
      'Promotion - Champions League (League phase)', null, 'Relegation'
    ])).toBe(false);
  });
});
