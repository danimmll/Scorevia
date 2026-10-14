import { describe, expect, it } from 'vitest';
import { bajaSigueAbierta, textoFinBaja } from './baja.util';

const HOY = new Date('2026-09-08T00:00:00Z');

describe('textoFinBaja', () => {
  it('con fecha de alta, la enseña', () => {
    expect(textoFinBaja({ idBaja: 1, idJugador: 1, fechaInicio: '2026-01-10', fechaFin: '2026-02-20' }, HOY))
      .toBe('2026-02-20');
  });

  it('una baja reciente sin alta sigue en curso', () => {
    expect(textoFinBaja({ idBaja: 1, idJugador: 1, fechaInicio: '2026-08-20' }, HOY)).toBe('en curso');
    expect(bajaSigueAbierta({ idBaja: 1, idJugador: 1, fechaInicio: '2026-08-20' }, HOY)).toBe(true);
  });

  it('una baja vieja sin alta no es "en curso": es que falta el dato del regreso', () => {
    // Hay 1.473 bajas de 2025 y una de 2014 sin fecha de fin. Enseñarlas como "en curso" decía
    // que hay futbolistas lesionados desde hace once años.
    expect(textoFinBaja({ idBaja: 1, idJugador: 1, fechaInicio: '2014-03-11' }, HOY)).toBe('sin fecha de alta');
    expect(textoFinBaja({ idBaja: 1, idJugador: 1, fechaInicio: '2025-11-02' }, HOY)).toBe('sin fecha de alta');
    expect(bajaSigueAbierta({ idBaja: 1, idJugador: 1, fechaInicio: '2025-11-02' }, HOY)).toBe(false);
  });

  it('sin fecha de inicio utilizable, no se inventa nada', () => {
    expect(textoFinBaja({ idBaja: 1, idJugador: 1 }, HOY)).toBe('sin fecha de alta');
    expect(textoFinBaja({ idBaja: 1, idJugador: 1, fechaInicio: 'no-es-una-fecha' }, HOY)).toBe('sin fecha de alta');
  });
});
