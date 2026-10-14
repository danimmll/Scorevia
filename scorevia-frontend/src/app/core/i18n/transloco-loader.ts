import { Injectable } from '@angular/core';
import { Translation, TranslocoLoader } from '@jsverse/transloco';

/**
 * Carga los catalogos de traduccion.
 *
 * <b>Por import dinamico y no por HTTP</b>, que es lo que trae Transloco de serie. El
 * loader normal pide `/assets/i18n/es.json` con HttpClient, y en este proyecto eso no vale:
 * el render de servidor corre dentro del contenedor del frontend, donde una ruta relativa
 * no apunta a nada (es el mismo problema que documenta `ssr-api-url-interceptor.ts` para la
 * API). Con una peticion fallida, Transloco renderiza las claves en crudo, asi que el HTML
 * que llega al navegador diria "nav.partidos" en vez de "Partidos" y luego la hidratacion
 * lo cambiaria de golpe.
 *
 * Con `import()` el catalogo es un chunk mas del bundle: existe igual en servidor y en
 * navegador, no hay peticion que pueda fallar, y Angular lo separa en su propio fichero
 * para que el idioma que no se usa no pese en la carga inicial.
 */
@Injectable({ providedIn: 'root' })
export class TraduccionesLoader implements TranslocoLoader {
  getTranslation(idioma: string): Promise<Translation> {
    switch (idioma) {
      case 'es':
        return import('./es.json').then(m => m.default as Translation);
      case 'en':
      default:
        return import('./en.json').then(m => m.default as Translation);
    }
  }
}
