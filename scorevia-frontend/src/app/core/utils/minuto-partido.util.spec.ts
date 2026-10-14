import { describe, expect, it } from 'vitest';
import { etiquetaMinuto, faseDelPartido } from './minuto-partido.util';
import en from '../i18n/en.json';
import es from '../i18n/es.json';

/**
 * Desde que la interfaz tiene dos idiomas, estas funciones devuelven la CLAVE de traduccion
 * y no el texto: quien decide como se lee cada fase es el catalogo.
 *
 * Por eso cada prueba comprueba dos cosas: que sale la clave que toca, y que esa clave
 * existe de verdad en los dos catalogos. Sin lo segundo, renombrar una clave en el json
 * dejaria la pantalla enseniando "match_phase_half_time" y los tests seguirian en verde.
 */
const catalogos = { en, es } as Record<string, Record<string, string>>;

function claveExiste(clave: string): boolean {
  return Object.keys(catalogos).every(idioma => typeof catalogos[idioma][clave] === 'string');
}

describe('etiquetaMinuto', () => {
  it('ensena el minuto de un partido en juego', () => {
    const r = etiquetaMinuto({ estado: 'EN_JUEGO', estadoCorto: '2H', minuto: 67 });
    expect(r).toEqual({ clave: 'minute_mark', params: { n: 67 } });
    expect(claveExiste('minute_mark')).toBe(true);
    // El formato del minuto vive en el catalogo, no en el codigo.
    expect(es['minute_mark']).toBe("{{n}}'");
  });

  it('en el descanso dice "Descanso" y no "45\'"', () => {
    // La API deja elapsed clavado en 45 durante el descanso. Ensenar "45'" haria creer que
    // el partido sigue corriendo y que el reloj esta parado por un fallo nuestro.
    const r = etiquetaMinuto({ estado: 'EN_JUEGO', estadoCorto: 'HT', minuto: 45 });
    expect(r).toEqual({ clave: 'match_phase_half_time' });
    expect(es['match_phase_half_time']).toBe('Descanso');
    expect(en['match_phase_half_time']).toBe('Half-time');
  });

  it('distingue la tanda de penaltis de la prorroga', () => {
    expect(etiquetaMinuto({ estado: 'EN_JUEGO', estadoCorto: 'P', minuto: 120 }))
      .toEqual({ clave: 'match_phase_penalties' });
    expect(etiquetaMinuto({ estado: 'EN_JUEGO', estadoCorto: 'ET', minuto: 105 }))
      .toEqual({ clave: 'minute_mark', params: { n: 105 } });
  });

  it('un partido interrumpido lo dice, no finge que se esta jugando', () => {
    expect(etiquetaMinuto({ estado: 'EN_JUEGO', estadoCorto: 'SUSP', minuto: 30 }))
      .toEqual({ clave: 'match_phase_suspended' });
  });

  it('sin minuto y con el reloj corriendo no se ensena nada', () => {
    // Antes caia a la fase ("2ª parte", "En juego"). Suena util y no lo es: al lado del punto
    // rojo de "en directo" no dice nada nuevo, y ocupa el sitio donde se busca el minuto.
    expect(etiquetaMinuto({ estado: 'EN_JUEGO', estadoCorto: '1H', minuto: null })).toBeNull();
    expect(etiquetaMinuto({ estado: 'EN_JUEGO', estadoCorto: '2H', minuto: null })).toBeNull();
    expect(etiquetaMinuto({ estado: 'EN_JUEGO', estadoCorto: null, minuto: null })).toBeNull();
  });

  it('con el reloj PARADO si se ensena la fase: ahi no hay minuto que pintar', () => {
    // El descanso y la tanda no tienen minuto, asi que la fase es el dato, no un relleno.
    expect(etiquetaMinuto({ estado: 'EN_JUEGO', estadoCorto: 'HT', minuto: null }))
      .toEqual({ clave: 'match_phase_half_time' });
    expect(etiquetaMinuto({ estado: 'EN_JUEGO', estadoCorto: 'P', minuto: null }))
      .toEqual({ clave: 'match_phase_penalties' });
  });

  it('devuelve null si el partido no se esta jugando', () => {
    expect(etiquetaMinuto({ estado: 'FINALIZADO', estadoCorto: 'FT', minuto: null })).toBeNull();
    expect(etiquetaMinuto({ estado: 'PROGRAMADO', estadoCorto: 'NS', minuto: null })).toBeNull();
    // Un minuto que se quedo pegado de un tick viejo no debe resucitar el partido.
    expect(etiquetaMinuto({ estado: 'FINALIZADO', estadoCorto: 'FT', minuto: 90 })).toBeNull();
  });

  it('toda clave que se pueda devolver esta en los dos catalogos', () => {
    // Solo los codigos con el reloj parado devuelven clave; los demas devuelven null y por
    // eso no entran aqui.
    for (const estadoCorto of ['HT', 'BT', 'P', 'SUSP', 'INT']) {
      const r = etiquetaMinuto({ estado: 'EN_JUEGO', estadoCorto, minuto: null });
      expect(r, `sin clave para ${estadoCorto}`).not.toBeNull();
      expect(claveExiste(r!.clave), `falta ${r!.clave} en algun catalogo`).toBe(true);
    }
    // Y el minuto, que es la clave con parametro.
    const conMinuto = etiquetaMinuto({ estado: 'EN_JUEGO', estadoCorto: '2H', minuto: 67 });
    expect(claveExiste(conMinuto!.clave)).toBe(true);
  });
});

describe('faseDelPartido', () => {
  it('da el nombre largo solo cuando la etiqueta corta no lo dice ya', () => {
    expect(faseDelPartido({ estado: 'EN_JUEGO', estadoCorto: '2H' })).toBe('match_phase_second_half');
    expect(faseDelPartido({ estado: 'EN_JUEGO', estadoCorto: 'HT' })).toBeNull();
    expect(faseDelPartido({ estado: 'FINALIZADO', estadoCorto: 'FT' })).toBeNull();
  });
});
