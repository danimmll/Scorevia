import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ErroresService } from './errores.service';
import { IdiomaService } from '../i18n/idioma.service';
import { importarTranslocoDePrueba } from '../i18n/testing';

/**
 * Que texto ve el usuario cuando algo falla.
 *
 * <p>El backend manda su mensaje en `detail` y ese mensaje esta en espanol. Con la interfaz
 * en ingles, todo iba en ingles hasta que algo fallaba y saltaba una frase en espanol.</p>
 */
describe('ErroresService', () => {
  let servicio: ErroresService;
  let idioma: IdiomaService;

  /** Un error HTTP de Angular envolviendo el ProblemDetail del backend. */
  const httpError = (type?: string, detail?: string) => ({ error: { type, detail } });

  beforeEach(async () => {
    // cambiar() persiste el idioma; sin limpiar, se lo lleva el siguiente spec.
    localStorage.clear();
    TestBed.configureTestingModule({ imports: [importarTranslocoDePrueba()] });
    servicio = TestBed.inject(ErroresService);
    idioma = TestBed.inject(IdiomaService);
    await idioma.iniciar();
  });

  describe('en ingles', () => {
    it('traduce por el codigo generico en vez de ensenar el espanol del backend', () => {
      const msg = servicio.mensaje(httpError('not-found', 'Jugador no encontrado'));
      expect(msg).toBe("We couldn't find that.");
    });

    it('con un codigo fino, el ingles es igual de especifico', () => {
      const msg = servicio.mensaje(httpError('nickname-en-uso', 'Ese nickname ya está en uso.'));
      expect(msg).toBe('That name is already taken.');
    });

    it('un codigo que no conocemos no se pinta en crudo', () => {
      // Sin la comprobacion contra el catalogo, aqui saldria "error_codigo_marciano".
      const msg = servicio.mensaje(httpError('codigo-marciano', 'Detalle del backend'));
      expect(msg).toBe('Detalle del backend');
    });

    it('sin codigo ni detalle, la frase generica', () => {
      expect(servicio.mensaje({})).toBe('Something unexpected went wrong.');
      expect(servicio.mensaje(null)).toBe('Something unexpected went wrong.');
    });

    it('el type puede venir como URI completa', () => {
      const msg = servicio.mensaje(httpError('https://scorevia.net/errores/forbidden', 'x'));
      expect(msg).toBe("You don't have permission to do that.");
    });
  });

  describe('en espanol', () => {
    beforeEach(() => idioma.cambiar('es'));

    it('manda el detalle del backend, que es mas especifico', () => {
      // "Jugador no encontrado" dice mas que "No se ha encontrado.", y ya viene escrito.
      const msg = servicio.mensaje(httpError('not-found', 'Jugador no encontrado'));
      expect(msg).toBe('Jugador no encontrado');
    });

    it('sin detalle, cae a la traduccion del codigo', () => {
      expect(servicio.mensaje(httpError('not-found'))).toBe('No se ha encontrado.');
    });

    it('sin nada, la frase generica en espanol', () => {
      expect(servicio.mensaje({})).toBe('Ha ocurrido un error inesperado.');
    });
  });

  it('los 11 codigos que manda GlobalExceptionHandler tienen traduccion', () => {
    const codigos = ['not-found', 'bad-request', 'conflict', 'conflict-duplicate', 'forbidden',
                     'method-not-allowed', 'missing-parameter', 'internal-server-error',
                     'config-problem', 'external-api-error', 'payload-serialization-error'];
    for (const codigo of codigos) {
      const msg = servicio.mensaje(httpError(codigo));
      expect(msg, `falta la clave de ${codigo}`).not.toContain('error_');
      expect(msg.length, `${codigo} sin texto`).toBeGreaterThan(3);
    }
  });
});
