import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { UsuarioService } from '../../../core/services/usuario.service';
import { AuthService } from '../../../core/services/auth.service';
import { Favorito, TipoFavorito } from '../../../core/models/usuario.model';
import { TranslocoPipe } from '@jsverse/transloco';
import { ErroresService } from '../../../core/services/errores.service';

@Component({
  selector: 'app-favoritos',
  standalone: true,
  imports: [CommonModule, TranslocoPipe],
  templateUrl: './favoritos.html',
  styleUrls: ['./favoritos.scss']
})
export class FavoritosComponent implements OnInit {
  private errores = inject(ErroresService);
  private usuarioService = inject(UsuarioService);
  private authService = inject(AuthService);

  tipos: TipoFavorito[] = ['equipos', 'ligas', 'partidos'];
  pestanaActiva = signal<TipoFavorito>('equipos');

  favoritosEquipos = signal<Favorito[]>([]);
  favoritosLigas = signal<Favorito[]>([]);
  favoritosPartidos = signal<Favorito[]>([]);
  error = signal<string | null>(null);

  ngOnInit(): void {
    this.tipos.forEach(tipo => this.cargar(tipo));
  }

  cambiarPestana(tipo: TipoFavorito): void {
    this.pestanaActiva.set(tipo);
  }

  favoritosDe(tipo: TipoFavorito): Favorito[] {
    if (tipo === 'equipos') return this.favoritosEquipos();
    if (tipo === 'ligas') return this.favoritosLigas();
    return this.favoritosPartidos();
  }

  quitar(tipo: TipoFavorito, idReferenciaApi: number): void {
    const idUsuario = this.authService.currentUser()?.idUsuario;
    if (!idUsuario) return;

    this.usuarioService.quitarFavorito(idUsuario, tipo, idReferenciaApi).subscribe({
      next: () => this.cargar(tipo),
      error: err => this.error.set(this.errores.mensaje(err, 'No se pudo quitar el favorito.'))
    });
  }

  private cargar(tipo: TipoFavorito): void {
    const idUsuario = this.authService.currentUser()?.idUsuario;
    if (!idUsuario) return;

    this.usuarioService.getFavoritos(idUsuario, tipo).subscribe({
      next: favoritos => {
        if (tipo === 'equipos') this.favoritosEquipos.set(favoritos);
        else if (tipo === 'ligas') this.favoritosLigas.set(favoritos);
        else this.favoritosPartidos.set(favoritos);
      },
      error: err => this.error.set(this.errores.mensaje(err, 'No se pudieron cargar los favoritos.'))
    });
  }
}
