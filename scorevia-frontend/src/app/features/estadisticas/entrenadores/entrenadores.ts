import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { LigaService } from '../../../core/services/liga.service';
import { EntrenadorService } from '../../../core/services/entrenador.service';
import { Liga, Entrenador } from '../../../core/models/estadisticas.model';
import { agruparLigasPorCategoria, ligaDestacadaPorDefecto } from '../../../core/utils/liga-categoria.util';
import { ImagenFallbackDirective } from '../../../core/directives/imagen-fallback.directive';
import { CargandoComponent } from '../../../shared/cargando/cargando';
import { PaginadorComponent } from '../../../shared/paginador/paginador';
import { paginarEnCliente } from '../../../core/utils/paginacion-cliente';
import { TranslocoPipe } from '@jsverse/transloco';
import { ErroresService } from '../../../core/services/errores.service';

@Component({
  selector: 'app-entrenadores',
  standalone: true,
  imports: [CommonModule, RouterLink, ImagenFallbackDirective, CargandoComponent, PaginadorComponent, TranslocoPipe],
  templateUrl: './entrenadores.html',
  styleUrls: ['./entrenadores.scss']
})
export class EntrenadoresComponent implements OnInit {
  private errores = inject(ErroresService);
  private ligaService = inject(LigaService);
  private entrenadorService = inject(EntrenadorService);

  ligas = signal<Liga[]>([]);
  ligaSeleccionada = signal<number | null>(null);
  entrenadores = signal<Entrenador[]>([]);

  /** Una liga entera son decenas de técnicos; sin paginar la rejilla no acaba nunca. */
  paginados = paginarEnCliente(this.entrenadores, 24);
  error = signal<string | null>(null);
  cargando = signal(true);

  paisSeleccionado = signal<string | null>(null);

  paises = computed(() => [...new Set(this.ligas().map(l => l.pais).filter((p): p is string => !!p))].sort());
  ligasDelPais = computed(() =>
    this.ligas()
      .filter(l => l.pais === this.paisSeleccionado())
      .sort((a, b) => (a.nombre ?? '').localeCompare(b.nombre ?? ''))
  );
  gruposLigasDelPais = computed(() => agruparLigasPorCategoria(this.ligasDelPais()));

  ngOnInit(): void {
    this.ligaService.getListado().subscribe({
      next: ligas => {
        this.ligas.set(ligas);
        const destacada = ligaDestacadaPorDefecto(ligas);
        if (destacada) {
          this.paisSeleccionado.set(destacada.pais ?? null);
          this.seleccionarLiga(destacada.idLiga);
        } else {
          this.cargando.set(false);
        }
      },
      error: err => {
        this.error.set(this.errores.mensaje(err));
        this.cargando.set(false);
      }
    });
  }

  seleccionarPais(pais: string): void {
    this.paisSeleccionado.set(pais);
    const primeraLigaDelPais = this.ligasDelPais()[0];
    if (primeraLigaDelPais) this.seleccionarLiga(primeraLigaDelPais.idLiga);
  }

  seleccionarLiga(idLiga: number): void {
    this.ligaSeleccionada.set(idLiga);
    this.cargando.set(true);

    const liga = this.ligas().find(l => l.idLiga === idLiga);
    const temporada = liga?.temporadaActual ?? new Date().getFullYear();

    this.entrenadorService.getPorLiga(idLiga, temporada).subscribe({
      next: entrenadores => {
        this.entrenadores.set(entrenadores);
        this.cargando.set(false);
      },
      error: err => {
        this.error.set(this.errores.mensaje(err, 'No se pudieron cargar los entrenadores.'));
        this.cargando.set(false);
      }
    });
  }
}
