import { describe, expect, it } from 'vitest';
import en from './en.json';
import es from './es.json';

/**
 * Los códigos de error que manda el backend tienen que tener traducción aquí.
 *
 * <p>El backend pone un código estable en el `type` del ProblemDetail
 * (`nickname-en-uso`, `correo-en-uso`...) y ErroresService traduce por él. Los dos lados están
 * en repositorios distintos del mismo proyecto y nada obliga a que vayan a la vez: si
 * alguien añade `throw new ConflictException("...", "codigo-nuevo")` en Java y no toca los
 * json, el usuario inglés se queda viendo la frase en español.</p>
 *
 * <p>Esta lista se copia a mano de los `throw` del backend a propósito. Es un espejo, y su
 * gracia es justo que haya que actualizarlo: si se pudiera deducir solo, no avisaría de
 * nada. Sacar el listado del backend:</p>
 *
 * <pre>
 * grep -rhoE 'Exception\("[^"]*", "([a-z-]+)"\)' scorevia-backend/Microservicio-* \
 *   --include=*.java | grep -oE '"[a-z-]+"\)$' | tr -d '")' | sort -u
 * </pre>
 */
const CODIGOS_DEL_BACKEND = [
  // Los 11 genéricos de GlobalExceptionHandler
  'not-found', 'bad-request', 'conflict', 'conflict-duplicate', 'forbidden',
  'method-not-allowed', 'missing-parameter', 'internal-server-error',
  'config-problem', 'external-api-error', 'payload-serialization-error',
  // Los finos, los que el usuario lee y le obligan a hacer algo
  'codigo-restablecimiento-invalido', 'codigo-verificacion-invalido', 'correo-en-uso',
  'correo-ya-verificado', 'demasiados-intentos', 'id-invalido', 'nickname-en-uso',
  'no-autenticado', 'partido-no-encontrado', 'rol-no-encontrado', 'usuario-inactivo',
  'usuario-no-encontrado', 'ya-en-favoritos',
];

const EN = en as Record<string, string>;
const ES = es as Record<string, string>;

const claveDe = (codigo: string) => 'error_' + codigo.replace(/-/g, '_');

describe('códigos de error del backend', () => {
  it('todos tienen traducción en los dos idiomas', () => {
    const sinClave = CODIGOS_DEL_BACKEND.filter(c => !(claveDe(c) in EN) || !(claveDe(c) in ES));
    expect(sinClave, `sin traducir: ${sinClave.join(', ')}`).toEqual([]);
  });

  it('ninguna traducción de error se ha quedado vacía', () => {
    for (const codigo of CODIGOS_DEL_BACKEND) {
      const clave = claveDe(codigo);
      expect(EN[clave]?.trim(), `${clave} vacío en inglés`).toBeTruthy();
      expect(ES[clave]?.trim(), `${clave} vacío en español`).toBeTruthy();
    }
  });

  it('el mensaje inglés de un error no es el español', () => {
    // Si alguien copia el texto español en en.json, el usuario inglés no se entera de nada.
    const copiados = CODIGOS_DEL_BACKEND.filter(c => {
      const clave = claveDe(c);
      return EN[clave] === ES[clave] && EN[clave].trim().split(/\s+/).length >= 3;
    });
    expect(copiados, `parecen sin traducir: ${copiados.join(', ')}`).toEqual([]);
  });
});
