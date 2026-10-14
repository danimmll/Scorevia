import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { map, catchError, of } from 'rxjs';

/**
 * Exige sesión Y rol ADMIN. Se combina con authGuard en la ruta (canActivate: [authGuard, adminGuard])
 * -- este guard asume que ya hay sesión resuelta y solo comprueba el rol.
 */
export const adminGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.currentUser()) {
    if (authService.esAdmin()) return true;
    router.navigate(['/']);
    return false;
  }

  return authService.checkAuthStatus().pipe(
    map(() => {
      if (authService.esAdmin()) return true;
      router.navigate(['/']);
      return false;
    }),
    catchError(() => {
      router.navigate(['/login']);
      return of(false);
    })
  );
};
