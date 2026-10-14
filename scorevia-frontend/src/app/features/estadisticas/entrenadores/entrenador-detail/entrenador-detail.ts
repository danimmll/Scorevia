import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EntrenadorService } from '../../../../core/services/entrenador.service';
import { Entrenador, Trofeo, HistorialEntrenador } from '../../../../core/models/estadisticas.model';
import { ImagenFallbackDirective } from '../../../../core/directives/imagen-fallback.directive';
import { CargandoComponent } from '../../../../shared/cargando/cargando';
import { LineaTrofeosComponent } from '../../../../shared/linea-trofeos/linea-trofeos';
import { PaginadorComponent } from '../../../../shared/paginador/paginador';
import { paginarEnCliente } from '../../../../core/utils/paginacion-cliente';
import { TranslocoPipe } from '@jsverse/transloco';
import { ErroresService } from '../../../../core/services/errores.service';

@Component({
  selector: 'app-entrenador-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, ImagenFallbackDirective, CargandoComponent, LineaTrofeosComponent, PaginadorComponent, TranslocoPipe],
  templateUrl: './entrenador-detail.html',
  styleUrls: ['./entrenador-detail.scss']
})
export class EntrenadorDetailComponent implements OnInit {
  private errores = inject(ErroresService);
  private route = inject(ActivatedRoute);
  private entrenadorService = inject(EntrenadorService);

  entrenador = signal<Entrenador | null>(null);
  trofeos = signal<Trofeo[]>([]);
  historialClubes = signal<HistorialEntrenador[]>([]);

  /** Un entrenador veterano tiene decenas de etapas; sin paginar la ficha era una tira. */
  clubesPaginados = paginarEnCliente(this.historialClubes, 8);
  cargando = signal(true);
  error = signal<string | null>(null);

  // Mismo motivo que en jugador-detail: "Champions League x5" en vez de 5 tarjetas idénticas.
  // La clave incluye el país -- una "Supercopa" de España y una de Arabia son trofeos
  // distintos aunque compartan nombre, agruparlas solo por nombre mentiría sobre el conteo.
  // FIX: solo cuentan las filas "Winner" -- un subcampeonato ("2nd Place") no es un título.
  private esVictoria(resultado?: string): boolean {
    return (resultado ?? '').toLowerCase().includes('winner');
  }
  trofeosAgrupados = computed(() => {
    const grupos = new Map<string, { competicion: string; pais?: string; temporadas: string[] }>();
    for (const t of this.trofeos()) {
      if (!this.esVictoria(t.resultado)) continue;
      const clave = `${t.competicion ?? 'Otro'}__${t.pais ?? ''}`;
      const existente = grupos.get(clave);
      if (existente) {
        if (t.temporada) existente.temporadas.push(t.temporada);
      } else {
        grupos.set(clave, { competicion: t.competicion ?? 'Otro', pais: t.pais, temporadas: t.temporada ? [t.temporada] : [] });
      }
    }
    return [...grupos.values()];
  });

  ngOnInit(): void {
    const idEntrenador = Number(this.route.snapshot.paramMap.get('idEntrenador'));

    // Sin esto, navegar a /estadisticas/entrenadores/abc (o cualquier segmento no numérico)
    // igual dispara dos peticiones HTTP con ".../entrenadores/NaN" -- el backend las rechaza
    // igual (400), pero no tiene sentido pagar el viaje de red para un id que ya sabemos
    // inválido del lado del cliente.
    if (!Number.isFinite(idEntrenador)) {
      this.error.set('Entrenador no válido.');
      this.cargando.set(false);
      return;
    }

    this.entrenadorService.getPorId(idEntrenador).subscribe({
      next: entrenador => {
        this.entrenador.set(entrenador);
        this.cargando.set(false);
      },
      error: err => {
        this.error.set(this.errores.mensaje(err, 'No se pudo cargar el entrenador.'));
        this.cargando.set(false);
      }
    });

    // Best-effort: si falla, el palmarés simplemente queda vacío -- no es motivo para que
    // falle toda la página de perfil.
    this.entrenadorService.getTrofeos(idEntrenador).subscribe({
      next: trofeos => this.trofeos.set(trofeos),
      error: () => this.trofeos.set([])
    });
    this.entrenadorService.getHistorialClubes(idEntrenador).subscribe({
      next: historial => this.historialClubes.set(historial),
      error: () => this.historialClubes.set([])
    });
  }
}
