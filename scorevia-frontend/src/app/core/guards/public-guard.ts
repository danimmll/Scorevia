import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { map, catchError, of } from 'rxjs';

export const publicGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  // Si ya tiene sesión cargada, no pinta nada en el Login. A la portada.
  if (authService.currentUser()) {
    router.navigate(['/']);
    return false;
  }

  // Si mete la URL "/login" a mano, comprobamos si tiene una cookie válida
  return authService.checkAuthStatus().pipe(
    map(() => {
      // Si el backend dice que SÍ hay cookie, le echamos a la portada (no hay ruta '/dashboard' -- eso es '/admin/dashboard', gateado por adminGuard aparte)
      router.navigate(['/']);
      return false;
    }),
    catchError(() => {
      // Si falla (no hay cookie), le permitimos ver la pantalla de Login/Registro
      return of(true);
    })
  );
};