import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { map, catchError, of } from 'rxjs';

export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  // Si ya tenemos los datos en memoria, pasa.
  if (authService.currentUser()) return true;

  // Si pulsamos F5, preguntamos al backend si la cookie sigue ahí
  return authService.checkAuthStatus().pipe(
    map(() => true), // Si responde 200 OK, le dejamos pasar a la ruta protegida
    catchError(() => {
      // Si la cookie no existe o ha caducado (Error 401), al login
      router.navigate(['/login']);
      return of(false);
    })
  );
};