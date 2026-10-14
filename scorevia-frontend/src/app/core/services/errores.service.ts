import { Injectable, inject } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { IdiomaService } from '../i18n/idioma.service';
import { ApiError } from '../models/api-error.model';

/**
 * El texto que se le enseña al usuario cuando algo falla.
 *
 * <h2>Por qué existe</h2>
 * El backend manda su mensaje en `detail`, y ese mensaje está escrito en español ("Ese
 * nickname ya está en uso."). Con la interfaz en inglés, el usuario veía todo en inglés
 * hasta que algo fallaba, y entonces le saltaba una frase en español.
 *
 * Traducirlo en el backend (Spring MessageSource) se descartó a propósito: el idioma aquí
 * lo elige un botón que se guarda en `localStorage`, mientras que Spring resuelve el idioma
 * por la cabecera `Accept-Language` del navegador. Alguien con Chrome en español que pulse
 * EN tendría la interfaz en inglés y los errores en español, que es el mismo problema pero
 * más difícil de ver. Además serían cinco catálogos más (uno por microservicio) sin el test
 * que aquí ya vigila que no falte ninguna clave.
 *
 * <h2>Cómo decide</h2>
 * El `ProblemDetail` del backend trae un `type` que es un código estable y sin idioma
 * (`not-found`, `nickname-en-uso`...). De ahí sale la clave de traducción.
 *
 * En **español** manda el `detail` del backend, porque es más específico y está bien
 * escrito. En **inglés** manda la traducción del código. Cuando el código es fino
 * (`nickname-en-uso`) el inglés es igual de específico; cuando es genérico (`conflict`) se
 * pierde detalle, pero se gana estar en el idioma correcto, que es lo que importa.
 */
@Injectable({ providedIn: 'root' })
export class ErroresService {
  private transloco = inject(TranslocoService);
  private idioma = inject(IdiomaService);

  /** Prefijo de las claves de error en los catálogos. */
  private static readonly PREFIJO = 'error_';

  mensaje(err: unknown, claveFallback = 'unexpected_error'): string {
    const cuerpo = (err as { error?: ApiError })?.error;
    const detalle = cuerpo?.detail?.trim();

    // En español el detalle del backend gana: dice exactamente qué ha pasado.
    if (this.idioma.actual() === 'es' && detalle) {
      return detalle;
    }

    const clave = this.claveDe(cuerpo?.type);
    if (clave) {
      return this.transloco.translate(clave);
    }

    // Sin código conocido: si al menos hay detalle se enseña, que dice más que una frase
    // genérica aunque esté en el idioma equivocado. Si no, la frase de siempre.
    return detalle || this.transloco.translate(claveFallback);
  }

  /** `not-found` -> `error_not_found`, y null si esa clave no está en el catálogo. */
  private claveDe(tipo: string | undefined): string | null {
    if (!tipo) {
      return null;
    }
    // El `type` es una URI relativa; nos quedamos con el último segmento por si algún día
    // se escribe como "https://scorevia.net/errores/not-found".
    const codigo = tipo.split('/').filter(Boolean).pop() ?? tipo;
    const clave = ErroresService.PREFIJO + codigo.replace(/-/g, '_');

    // translate() devuelve la clave tal cual cuando no existe, así que sirve para saber si
    // el código está contemplado. Sin esto, un código nuevo del backend acabaría pintando
    // "error_algo_raro" en pantalla.
    const traduccion = this.transloco.translate(clave);
    return traduccion && traduccion !== clave ? clave : null;
  }
}
