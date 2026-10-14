import { describe, expect, it } from 'vitest';
import { descripcionTraspaso, etiquetaTipoTraspaso } from './traspaso.util';
import en from '../i18n/en.json';
import es from '../i18n/es.json';

/**
 * Estas funciones devuelven la CLAVE de traduccion, no el texto: la interfaz tiene dos
 * idiomas y quien decide como se lee cada cosa es el catalogo.
 *
 * Cada prueba comprueba las dos mitades: que sale la clave que toca, y que esa clave existe
 * en los dos catalogos. Sin lo segundo, renombrar una clave en el json dejaria la pantalla
 * enseniando "transfer_type_loan" y los tests seguirian en verde.
 */
const catalogos: Record<string, Record<string, string>> = { en, es };

function claveExiste(clave: string): boolean {
  return Object.keys(catalogos).every(idioma => typeof catalogos[idioma][clave] === 'string');
}

describe('etiquetaTipoTraspaso', () => {
  it('devuelve la clave de cada codigo que guarda el backend', () => {
    expect(etiquetaTipoTraspaso('TRANSFER')).toBe('transfer_type_transfer');
    expect(etiquetaTipoTraspaso('LOAN')).toBe('transfer_type_loan');
    expect(etiquetaTipoTraspaso('LOAN_RETURN')).toBe('transfer_type_loan_return');
    expect(etiquetaTipoTraspaso('FREE')).toBe('transfer_type_free');
  });

  it('todas las claves de tipo estan en los dos catalogos', () => {
    for (const codigo of ['TRANSFER', 'LOAN', 'LOAN_RETURN', 'FREE',
                          'SWAP', 'PROMOTION', 'RETIRED', 'UNKNOWN']) {
      const clave = etiquetaTipoTraspaso(codigo);
      expect(claveExiste(clave), `falta ${clave} en algun catalogo`).toBe(true);
    }
    expect(es['transfer_type_loan']).toBe('Cesión');
    expect(en['transfer_type_loan']).toBe('Loan');
  });

  it('sin tipo, lo dice en vez de dejar el hueco vacio', () => {
    expect(etiquetaTipoTraspaso(null)).toBe('transfer_type_unknown');
    expect(etiquetaTipoTraspaso(undefined)).toBe('transfer_type_unknown');
  });

  it('deja pasar el texto de filas antiguas sin normalizar', () => {
    // Los traspasos guardados antes de normalizar el catalogo conservan el texto crudo de la
    // API. Ese texto no es una clave, asi que la tuberia no lo encuentra y lo devuelve tal
    // cual: se ve "Back from Loan" en vez de "Sin detalle", que es lo que se quiere.
    expect(etiquetaTipoTraspaso('Back from Loan')).toBe('Back from Loan');
  });
});

describe('descripcionTraspaso', () => {
  it('describe un movimiento normal entre dos clubes', () => {
    expect(descripcionTraspaso({ equipoOrigen: 'Chelsea', equipoDestino: 'Real Madrid' }))
      .toEqual({ clave: 'transfer_from_to', params: { origen: 'Chelsea', destino: 'Real Madrid' } });
  });

  it('reconoce el ascenso del filial, donde origen y destino son el mismo club', () => {
    expect(descripcionTraspaso({
      equipoOrigen: 'Real Madrid', equipoDestino: 'Real Madrid', tipo: 'PROMOTION',
    })).toEqual({ clave: 'transfer_promoted_from_academy', params: { club: 'Real Madrid' } });
  });

  it('con el mismo club a los dos lados pero sin ser un ascenso, no se inventa el motivo', () => {
    // La API publica 10.829 filas asi con tipos que no son PROMOTION. Llamarlas "ascenso del
    // filial" era ponerle una explicacion a un dato del que solo sabemos el club.
    expect(descripcionTraspaso({ equipoOrigen: 'Girona', equipoDestino: 'Girona', tipo: 'LOAN' }))
      .toEqual({ clave: 'transfer_same_club', params: { club: 'Girona' } });
    expect(descripcionTraspaso({ equipoOrigen: 'Girona', equipoDestino: 'Girona' }))
      .toEqual({ clave: 'transfer_same_club', params: { club: 'Girona' } });
  });

  it('funciona con un solo extremo conocido', () => {
    expect(descripcionTraspaso({ equipoOrigen: 'Real Madrid' }))
      .toEqual({ clave: 'transfer_leaves', params: { club: 'Real Madrid' } });
    expect(descripcionTraspaso({ equipoDestino: 'Getafe' }))
      .toEqual({ clave: 'transfer_joins', params: { club: 'Getafe' } });
  });

  it('no deja la linea vacia cuando no hay ningun equipo', () => {
    expect(descripcionTraspaso({})).toEqual({ clave: 'transfer_no_teams' });
  });

  it('todas las claves de descripcion estan en los dos catalogos', () => {
    const casos = [
      { equipoOrigen: 'A', equipoDestino: 'B' },
      { equipoOrigen: 'A', equipoDestino: 'A', tipo: 'PROMOTION' },
      { equipoOrigen: 'A', equipoDestino: 'A' },
      { equipoOrigen: 'A' },
      { equipoDestino: 'B' },
      {},
    ];
    for (const caso of casos) {
      const { clave } = descripcionTraspaso(caso);
      expect(claveExiste(clave), `falta ${clave} en algun catalogo`).toBe(true);
    }
  });
});
