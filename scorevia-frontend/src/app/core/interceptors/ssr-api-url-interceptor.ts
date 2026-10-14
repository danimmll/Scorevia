import { HttpInterceptorFn } from '@angular/common/http';
import { PLATFORM_ID, inject } from '@angular/core';
import { isPlatformServer } from '@angular/common';
import { environment } from '../../../environments/environment';

/**
 * Convierte las llamadas a la API en absolutas cuando el render corre en el servidor.
 *
 * <p><b>Por que.</b> En SSR el codigo corre dentro del contenedor del frontend: ni "localhost"
 * es el Gateway ni una ruta relativa tiene origen contra el que resolverse. Sin esto, toda
 * peticion durante el render de servidor fallaba en silencio y cada servicio la contaba como
 * "no se pudo cargar X". Dentro de la red docker el Gateway si es alcanzable por su nombre,
 * que es lo que trae {@code API_URL_INTERNAL}.</p>
 *
 * <p><b>Solo se tocan las rutas de la API.</b> Antes la condicion era
 * {@code req.url.startsWith(environment.apiUrl)}, que con el apiUrl vacio de produccion es
 * cierta para TODAS las URLs -- incluidas las de recursos ajenos -- y les habria pegado
 * delante el host del Gateway. Se mira {@code /api/} explicitamente, y se sigue admitiendo el
 * apiUrl absoluto que usa la configuracion de desarrollo.</p>
 *
 * <p>En el navegador este interceptor no hace nada.</p>
 */
export const ssrApiUrlInterceptor: HttpInterceptorFn = (req, next) => {
  const platformId = inject(PLATFORM_ID);

  if (isPlatformServer(platformId)) {
    const urlInterna = typeof process !== 'undefined' ? process.env?.['API_URL_INTERNAL'] : undefined;

    // Dos formas validas de pedir la API: relativa (produccion y docker) o absoluta contra el
    // apiUrl de la configuracion de desarrollo.
    const base = environment.apiUrl && req.url.startsWith(environment.apiUrl)
      ? environment.apiUrl
      : (req.url.startsWith('/api/') ? '' : null);

    if (urlInterna && base !== null && urlInterna !== environment.apiUrl) {
      req = req.clone({ url: urlInterna + req.url.slice(base.length) });
    }
  }

  return next(req);
};
