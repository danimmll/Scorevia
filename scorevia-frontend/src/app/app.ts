import { Component, OnInit, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AuthService } from './core/services/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: '<router-outlet></router-outlet>'
})
export class App implements OnInit {
  private authService = inject(AuthService);

  ngOnInit() {
    this.authService.checkAuthStatus().subscribe({
      // Sin console.log: esto corre en cada carga de cada visitante y lo unico que hacia era
      // anunciar en la consola del navegador si hay sesion o no. El resultado ya se ve en el
      // estado de AuthService, que es donde lo mira la aplicacion.
      next: () => undefined,
      error: () => undefined
    });
  }
}