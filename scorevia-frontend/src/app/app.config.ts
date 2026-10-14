import { ApplicationConfig, provideAppInitializer, inject, isDevMode } from '@angular/core';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { provideClientHydration } from '@angular/platform-browser';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideTransloco } from '@jsverse/transloco';
import { authInterceptor } from './core/interceptors/auth-interceptor';
import { ssrApiUrlInterceptor } from './core/interceptors/ssr-api-url-interceptor';
import { TraduccionesLoader } from './core/i18n/transloco-loader';
import { IdiomaService } from './core/i18n/idioma.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideClientHydration(),
    provideHttpClient(
      withFetch(),
      withInterceptors([ssrApiUrlInterceptor, authInterceptor])
    ),
    provideTransloco({
      config: {
        availableLangs: ['en', 'es'],
        // English-first: el ingles es el idioma del sitio, el espanol una traduccion.
        defaultLang: 'en',
        // Si una clave falta en espanol se cae al ingles en vez de pintar la clave cruda.
        fallbackLang: 'en',
        missingHandler: { useFallbackTranslation: true },
        reRenderOnLangChange: true,
        prodMode: !isDevMode(),
      },
      loader: TraduccionesLoader,
    }),
    // El idioma se decide antes del primer render: si se hiciera en el constructor del
    // layout, la primera pintada saldria en ingles y cambiaria sola un instante despues.
    provideAppInitializer(() => inject(IdiomaService).iniciar()),
  ]
};
