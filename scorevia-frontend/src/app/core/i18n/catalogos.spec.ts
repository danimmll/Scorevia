import { describe, expect, it } from 'vitest';
import en from './en.json';
import es from './es.json';

/**
 * Guardia de los catalogos.
 *
 * Un idioma se rompe en silencio: si falta una clave en es.json, Transloco cae al ingles y
 * la pantalla sigue funcionando -- solo que con media frase en el idioma equivocado, y nadie
 * se entera hasta que lo ve un usuario. Estas comprobaciones convierten eso en un test rojo.
 */
const EN = en as Record<string, string>;
const ES = es as Record<string, string>;

/**
 * Claves que se escriben igual en los dos idiomas a proposito, para que la comprobacion de
 * "esto parece sin traducir" no las señale cada vez.
 */
const IGUALES_A_PROPOSITO = new Set([
  // El juego se llama asi tambien en espanol: es el nombre del formato, no una descripcion.
  'game_higher_lower_name',
  // Solo son los dos equipos y una flecha; no hay nada que traducir.
  'transfer_from_to',
]);

/** Los {{parametros}} que lleva un texto. */
function params(texto: string): string[] {
  return [...texto.matchAll(/\{\{\s*([A-Za-z0-9_]+)\s*\}\}/g)]
    .map(m => m[1])
    .sort();
}

describe('catalogos de traduccion', () => {
  it('tienen exactamente las mismas claves', () => {
    const soloEn = Object.keys(EN).filter(k => !(k in ES));
    const soloEs = Object.keys(ES).filter(k => !(k in EN));
    expect(soloEn, `claves que solo estan en ingles: ${soloEn.join(', ')}`).toEqual([]);
    expect(soloEs, `claves que solo estan en espanol: ${soloEs.join(', ')}`).toEqual([]);
  });

  it('no tienen ningun texto vacio', () => {
    for (const [clave, valor] of Object.entries(EN)) {
      expect(valor.trim(), `en.json: ${clave} esta vacio`).not.toBe('');
    }
    for (const [clave, valor] of Object.entries(ES)) {
      expect(valor.trim(), `es.json: ${clave} esta vacio`).not.toBe('');
    }
  });

  it('usan los mismos parametros en los dos idiomas', () => {
    // Si la version inglesa dice "{{n}} coins" y la espanola "Gratis", el numero desaparece
    // al cambiar de idioma y nadie lo ve venir.
    for (const clave of Object.keys(EN)) {
      expect(params(ES[clave]), `los parametros de "${clave}" no coinciden`)
        .toEqual(params(EN[clave]));
    }
  });

  it('el ingles no se ha quedado sin traducir', () => {
    // Red de seguridad boba pero efectiva: si alguien anade una clave y copia el espanol en
    // los dos ficheros, esto lo caza. Se miran solo los textos con varias palabras, que son
    // los que de verdad cambian entre idiomas (los nombres propios y las siglas no).
    const sospechosas = Object.keys(EN).filter(clave => {
      if (IGUALES_A_PROPOSITO.has(clave)) return false;
      const ingles = EN[clave];
      return ingles === ES[clave] && ingles.trim().split(/\s+/).length >= 3;
    });
    expect(sospechosas, `parecen sin traducir: ${sospechosas.join(', ')}`).toEqual([]);
  });
});
