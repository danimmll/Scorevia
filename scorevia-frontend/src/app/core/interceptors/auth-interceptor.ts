import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Endpoints de auth públicos: no llevan Authorization ni disparan el flujo de refresh-en-401
 * (evita, entre otras cosas, un bucle si /api/v1/auth/refresh en sí devolviera 401 por cookie
 * inválida).
 */
const RUTAS_AUTH_PUBLICAS = [
  '/api/v1/auth/register',
  '/api/v1/auth/login',
  '/api/v1/auth/verificar-email',
  '/api/v1/auth/solicitar-reset-password',
  '/api/v1/auth/reset-password',
  '/api/v1/auth/google',
  '/api/v1/auth/refresh'
];

function esRutaAuthPublica(url: string): boolean {
  return RUTAS_AUTH_PUBLICAS.some(ruta => url.includes(ruta));
}

/**
 * Quien necesita que viaje la cookie.
 *
 * Solo el flujo de sesion: la cookie de refresco se pone y se lee en `/api/v1/auth/*`. El
 * resto de la API se autentica con el `Authorization: Bearer`, que no es una cookie y no
 * necesita `withCredentials`.
 *
 * <b>Esto no es cosmetico.</b> `withCredentials: true` iba en TODAS las peticiones, y
 * Angular descarta del estado transferido cualquier peticion con credenciales salientes
 * (`hasOutgoingCredentials` en `@angular/common/fesm2022/http.mjs`). Resultado: el render de
 * servidor pedia los datos, los pintaba, y el navegador VOLVIA A PEDIRLOS TODOS al hidratar,
 * porque no habia nada guardado que reutilizar. Medido: el bloque `ng-state` de /partidos
 * pesaba 2,6 kB y no contenia ni una sola respuesta.
 */
function necesitaCookie(url: string): boolean {
  return url.includes('/api/v1/auth/');
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const esPublica = esRutaAuthPublica(req.url);
  const token = authService.accessToken();

  const clonedRequest = req.clone({
    withCredentials: necesitaCookie(req.url),
    setHeaders: !esPublica && token ? { Authorization: `Bearer ${token}` } : {}
  });

  return next(clonedRequest).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status !== 401 || esPublica) {
        return throwError(() => error);
      }

      // Un solo refresh compartido (ver AuthService.refresh) + un único reintento de la
      // petición original con el token nuevo. Si el refresh también falla, no hay sesión.
      return authService.refresh().pipe(
        switchMap(nuevoToken =>
          next(req.clone({
            withCredentials: true,
            setHeaders: { Authorization: `Bearer ${nuevoToken}` }
          }))
        ),
        catchError(refreshError => {
          authService.limpiarSesion();
          router.navigate(['/login']);
          return throwError(() => refreshError);
        })
      );
    })
  );
};
