import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ArbitroService } from '../../../../core/services/arbitro.service';
import { ArbitroDetalle, Partido } from '../../../../core/models/estadisticas.model';
import { ImagenFallbackDirective } from '../../../../core/directives/imagen-fallback.directive';
import { PaginadorComponent } from '../../../../shared/paginador/paginador';
import { CargandoComponent } from '../../../../shared/cargando/cargando';
import { TranslocoPipe } from '@jsverse/transloco';
import { ErroresService } from '../../../../core/services/errores.service';

/**
 * Ficha de árbitro.
 *
 * <p>Desde un partido se podía llegar al equipo, al jugador y al entrenador; el árbitro era
 * un nombre suelto sin ningún sitio al que ir. No hay foto ni biografía porque API-Football
 * no lo trata como entidad propia (llega como texto dentro del fixture), así que la ficha se
 * apoya en lo que sí se puede contar: cuánto ha dirigido y con qué mano va de tarjetas.</p>
 */
@Component({
  selector: 'app-arbitro-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, ImagenFallbackDirective, PaginadorComponent, CargandoComponent, TranslocoPipe],
  templateUrl: './arbitro-detail.html',
  styleUrls: ['./arbitro-detail.scss']
})
export class ArbitroDetailComponent implements OnInit {
  private errores = inject(ErroresService);
  private route = inject(ActivatedRoute);
  private arbitroService = inject(ArbitroService);

  arbitro = signal<ArbitroDetalle | null>(null);
  partidos = signal<Partido[]>([]);
  pagina = signal(0);
  totalPaginas = signal(0);
  cargando = signal(true);
  cargandoPartidos = signal(false);
  error = signal<string | null>(null);

  private idArbitro = 0;

  ngOnInit(): void {
    this.idArbitro = Number(this.route.snapshot.paramMap.get('idArbitro'));

    // Mismo criterio que en las otras fichas: un id no numérico se corta aquí y no se
    // gasta el viaje de red para que el backend devuelva el 400 que ya sabemos.
    if (!Number.isFinite(this.idArbitro)) {
      this.error.set('Árbitro no válido.');
      this.cargando.set(false);
      return;
    }

    this.arbitroService.getArbitro(this.idArbitro).subscribe({
      next: arbitro => {
        this.arbitro.set(arbitro);
        this.cargando.set(false);
      },
      error: err => {
        this.error.set(this.errores.mensaje(err, 'No se pudo cargar el árbitro.'));
        this.cargando.set(false);
      }
    });

    this.cargarPartidos(0);
  }

  cargarPartidos(page: number): void {
    this.cargandoPartidos.set(true);
    this.arbitroService.getPartidos(this.idArbitro, page).subscribe({
      next: pag => {
        this.partidos.set(pag.content);
        this.pagina.set(pag.number);
        this.totalPaginas.set(pag.totalPages);
        this.cargandoPartidos.set(false);
      },
      // Best-effort: la cabecera con sus números ya se ve aunque la lista de partidos falle.
      error: () => this.cargandoPartidos.set(false)
    });
  }
}
