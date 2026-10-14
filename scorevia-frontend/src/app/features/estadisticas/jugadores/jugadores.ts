import { Component, OnInit, OnDestroy, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { JugadorService } from '../../../core/services/jugador.service';
import { LigaService } from '../../../core/services/liga.service';
import { EquipoService } from '../../../core/services/equipo.service';
import { Jugador, Liga, Equipo } from '../../../core/models/estadisticas.model';
import { agruparLigasPorCategoria } from '../../../core/utils/liga-categoria.util';
import { ImagenFallbackDirective } from '../../../core/directives/imagen-fallback.directive';
import { PaginadorComponent } from '../../../shared/paginador/paginador';
import { TranslocoPipe } from '@jsverse/transloco';
import { ErroresService } from '../../../core/services/errores.service';

@Component({
  selector: 'app-jugadores',
  standalone: true,
  imports: [CommonModule, RouterLink, ImagenFallbackDirective, PaginadorComponent, TranslocoPipe],
  templateUrl: './jugadores.html',
  styleUrls: ['./jugadores.scss']
})
export class JugadoresComponent implements OnInit, OnDestroy {
  private errores = inject(ErroresService);
  private jugadorService = inject(JugadorService);
  private ligaService = inject(LigaService);
  private equipoService = inject(EquipoService);

  jugadores = signal<Jugador[]>([]);
  pagina = signal(0);
  totalPaginas = signal(0);
  error = signal<string | null>(null);

  // Cascada país -> liga -> equipo, igual que en Equipos: una lista plana de todas las
  // ligas/competiciones sería imposible de buscar. "Todos los países"/"Todas las ligas"
  // (null) vuelve al listado completo paginado.
  ligas = signal<Liga[]>([]);
  equiposDeLiga = signal<Equipo[]>([]);
  paisSeleccionado = signal<string | null>(null);
  ligaSeleccionada = signal<number | null>(null);
  equipoSeleccionado = signal<number | null>(null);
  private temporadaLigaSeleccionada: number | null = null;

  // Búsqueda global por nombre (backend, no client-side): antes solo filtraba sobre la
  // página de 20 ya cargada, así que buscar "Cristiano" no encontraba nada si no estaba
  // en esa página. Con texto de búsqueda, la búsqueda gana sobre el filtro país/liga/equipo.
  filtroNombre = signal('');
  private debounceId?: ReturnType<typeof setTimeout>;

  paises = computed(() => [...new Set(this.ligas().map(l => l.pais).filter((p): p is string => !!p))].sort());
  ligasDelPais = computed(() =>
    this.ligas()
      .filter(l => l.pais === this.paisSeleccionado())
      .sort((a, b) => (a.nombre ?? '').localeCompare(b.nombre ?? ''))
  );
  gruposLigasDelPais = computed(() => agruparLigasPorCategoria(this.ligasDelPais()));

  ngOnInit(): void {
    this.ligaService.getListado().subscribe({ next: ligas => this.ligas.set(ligas) });
    this.cargarPagina(0);
  }

  ngOnDestroy(): void {
    clearTimeout(this.debounceId);
  }

  cargarPagina(page: number): void {
    const nombre = this.filtroNombre().trim();
    const idEquipo = this.equipoSeleccionado();
    const idLiga = this.ligaSeleccionada();

    const fuente = nombre
      ? this.jugadorService.buscar(nombre, page)
      : idEquipo
      ? this.jugadorService.getPorEquipo(idEquipo, page)
      : idLiga
      ? this.jugadorService.getPorLiga(idLiga, this.temporadaLigaSeleccionada ?? new Date().getFullYear(), page)
      : this.jugadorService.getListado(page);

    fuente.subscribe({
      next: pag => {
        this.jugadores.set(pag.content);
        this.pagina.set(pag.number);
        this.totalPaginas.set(pag.totalPages);
      },
      error: err => this.error.set(this.errores.mensaje(err))
    });
  }

  buscarPorNombre(texto: string): void {
    this.filtroNombre.set(texto);
    clearTimeout(this.debounceId);
    this.debounceId = setTimeout(() => this.cargarPagina(0), 350);
  }

  seleccionarPais(pais: string): void {
    this.paisSeleccionado.set(pais || null);
    this.ligaSeleccionada.set(null);
    this.equipoSeleccionado.set(null);
    this.equiposDeLiga.set([]);
    this.temporadaLigaSeleccionada = null;
    this.cargarPagina(0);
  }

  seleccionarLiga(idLiga: number): void {
    this.ligaSeleccionada.set(idLiga || null);
    this.equipoSeleccionado.set(null);

    const liga = this.ligas().find(l => l.idLiga === idLiga);
    this.temporadaLigaSeleccionada = liga?.temporadaActual ?? null;
    this.cargarPagina(0);

    if (!idLiga || !liga) {
      this.equiposDeLiga.set([]);
      return;
    }
    this.equipoService.getPorLiga(idLiga, this.temporadaLigaSeleccionada ?? new Date().getFullYear()).subscribe({
      next: equipos => this.equiposDeLiga.set(equipos),
      error: () => this.equiposDeLiga.set([])
    });
  }

  seleccionarEquipo(idEquipo: number): void {
    this.equipoSeleccionado.set(idEquipo || null);
    this.cargarPagina(0);
  }

  limpiarFiltro(): void {
    this.paisSeleccionado.set(null);
    this.ligaSeleccionada.set(null);
    this.equipoSeleccionado.set(null);
    this.equiposDeLiga.set([]);
    this.temporadaLigaSeleccionada = null;
    this.cargarPagina(0);
  }
}
