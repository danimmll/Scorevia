import { Provider } from '@angular/core';
import { TranslocoTestingModule, TranslocoTestingOptions } from '@jsverse/transloco';
import en from './en.json';
import es from './es.json';

/**
 * Transloco para los tests, con los catalogos DE VERDAD.
 *
 * Se cargan el en.json y el es.json reales en vez de un diccionario de mentira porque asi
 * un test que compruebe un texto de la pantalla comprueba de paso que la clave existe: si
 * alguien borra o renombra una clave del catalogo, el test se cae en vez de pasar contra
 * un doble que siempre dice que si.
 *
 * El idioma por defecto es el ingles, igual que en la aplicacion.
 */
export function importarTranslocoDePrueba(opciones: TranslocoTestingOptions = {}) {
  return TranslocoTestingModule.forRoot({
    langs: { en, es },
    translocoConfig: {
      availableLangs: ['en', 'es'],
      defaultLang: 'en',
    },
    preloadLangs: true,
    ...opciones,
  });
}

/** Azucar para los specs que solo necesitan que la tuberia no reviente. */
export const PROVEEDORES_I18N_PRUEBA: Provider[] = [];
