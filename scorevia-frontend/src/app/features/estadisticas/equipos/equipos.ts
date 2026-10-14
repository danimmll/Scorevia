import { Component, OnInit, OnDestroy, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { LigaService } from '../../../core/services/liga.service';
import { EquipoService } from '../../../core/services/equipo.service';
import { UsuarioService } from '../../../core/services/usuario.service';
import { AuthService } from '../../../core/services/auth.service';
import { Liga, Equipo, TemporadaLiga } from '../../../core/models/estadisticas.model';
import { agruparLigasPorCategoria, ligaDestacadaPorDefecto } from '../../../core/utils/liga-categoria.util';
import { ImagenFallbackDirective } from '../../../core/directives/imagen-fallback.directive';
import { SelectorPaisComponent } from '../../../shared/selector-pais/selector-pais';
import { PaginadorComponent } from '../../../shared/paginador/paginador';
import { paginarEnCliente } from '../../../core/utils/paginacion-cliente';
import { TranslocoPipe } from '@jsverse/transloco';
import { ErroresService } from '../../../core/services/errores.service';

@Component({
  selector: 'app-equipos',
  standalone: true,
  imports: [CommonModule, RouterLink, ImagenFallbackDirective, SelectorPaisComponent, PaginadorComponent, TranslocoPipe],
  templateUrl: './equipos.html',
  styleUrls: ['./equipos.scss']
})
export class EquiposComponent implements OnInit, OnDestroy {
  private errores = inject(ErroresService);
  private ligaService = inject(LigaService);
  private equipoService = inject(EquipoService);
  private usuarioService = inject(UsuarioService);
  public authService = inject(AuthService);

  ligas = signal<Liga[]>([]);
  ligaSeleccionada = signal<number | null>(null);
  equipos = signal<Equipo[]>([]);

  /** Hay ligas con más de 20 equipos y el país entero son cientos. */
  paginados = paginarEnCliente(this.equipos, 24);
  // Temporada que se está viendo, con su selector. Antes se cogía liga.temporadaActual sin
  // más, y ese campo apuntaba a 2010 en las grandes ligas: la página de equipos de "La Liga"
  // enseñaba la plantilla de clubes de 2010-11 (con el Hércules) creyendo que era la actual.
  temporada = signal<number | null>(null);
  temporadasDisponibles = signal<TemporadaLiga[]>([]);
  error = signal<string | null>(null);

  // País -> liga es un filtro en cascada: una lista plana de todas las ligas/competiciones
  // sería imposible de buscar (cientos de entradas), así que primero se acota por país.
  paisSeleccionado = signal<string | null>(null);

  // Búsqueda global por nombre (backend, no client-side): antes solo filtraba sobre los
  // equipos de la liga ya cargada, así que buscar un equipo de otra liga/país no encontraba
  // nada. Con texto de búsqueda, la búsqueda gana sobre el filtro país/liga.
  filtroNombre = signal('');
  buscando = signal(false);
  private debounceId?: ReturnType<typeof setTimeout>;

  paises = computed(() => [...new Set(this.ligas().map(l => l.pais).filter((p): p is string => !!p))].sort());
  ligasDelPais = computed(() =>
    this.ligas()
      .filter(l => l.pais === this.paisSeleccionado())
      .sort((a, b) => (a.nombre ?? '').localeCompare(b.nombre ?? ''))
  );
  gruposLigasDelPais = computed(() => agruparLigasPorCategoria(this.ligasDelPais()));

  // Cada fila enlaza a /estadisticas/equipos/:id (equipo-detail), así que el estado de
  // favorito se guarda como un set de ids en vez de un único booleano -- cada fila consulta
  // si su idEquipo está en el set.
  favoritosEquiposIds = signal<Set<number>>(new Set());

  ngOnInit(): void {
    this.ligaService.getListado().subscribe({
      next: ligas => {
        this.ligas.set(ligas);
        const destacada = ligaDestacadaPorDefecto(ligas);
        if (destacada) {
          this.paisSeleccionado.set(destacada.pais ?? null);
          this.seleccionarLiga(destacada.idLiga);
        }
      },
      error: err => this.error.set(this.errores.mensaje(err))
    });

    this.cargarFavoritos();
  }

  ngOnDestroy(): void {
    clearTimeout(this.debounceId);
  }

  buscarPorNombre(texto: string): void {
    this.filtroNombre.set(texto);
    clearTimeout(this.debounceId);
    this.debounceId = setTimeout(() => {
      const nombre = this.filtroNombre().trim();
      if (!nombre) {
        this.buscando.set(false);
        if (this.ligaSeleccionada()) this.seleccionarLiga(this.ligaSeleccionada()!);
        return;
      }
      this.buscando.set(true);
      this.equipoService.buscar(nombre).subscribe({
        next: equipos => this.equipos.set(equipos),
        error: err => this.error.set(this.errores.mensaje(err))
      });
    }, 350);
  }

  seleccionarPais(pais: string): void {
    this.paisSeleccionado.set(pais);
    const primeraLigaDelPais = this.ligasDelPais()[0];
    if (primeraLigaDelPais) this.seleccionarLiga(primeraLigaDelPais.idLiga);
  }

  esFavorito(idEquipo: number): boolean {
    return this.favoritosEquiposIds().has(idEquipo);
  }

  toggleFavorito(idEquipo: number, event: Event): void {
    // La fila entera ya tiene (click)="verTraspasos(...)" -- sin esto, tocar la estrella
    // también expandiría/colapsaría los traspasos del equipo.
    event.stopPropagation();

    const idUsuario = this.authService.currentUser()?.idUsuario;
    if (!idUsuario) return;

    const yaEsFavorito = this.esFavorito(idEquipo);
    const accion$ = yaEsFavorito
      ? this.usuarioService.quitarFavorito(idUsuario, 'equipos', idEquipo)
      : this.usuarioService.addFavorito(idUsuario, 'equipos', idEquipo);

    accion$.subscribe({
      next: () => {
        const nuevo = new Set(this.favoritosEquiposIds());
        if (yaEsFavorito) nuevo.delete(idEquipo);
        else nuevo.add(idEquipo);
        this.favoritosEquiposIds.set(nuevo);
      },
      error: err => this.error.set(this.errores.mensaje(err, 'No se pudo actualizar el favorito.'))
    });
  }

  private cargarFavoritos(): void {
    const idUsuario = this.authService.currentUser()?.idUsuario;
    if (!idUsuario) return;

    this.usuarioService.getFavoritos(idUsuario, 'equipos').subscribe({
      next: favoritos => this.favoritosEquiposIds.set(new Set(favoritos.map(f => f.idReferenciaApi))),
      error: () => this.favoritosEquiposIds.set(new Set())
    });
  }

  seleccionarLiga(idLiga: number): void {
    this.ligaSeleccionada.set(idLiga);
    this.temporadasDisponibles.set([]);

    const liga = this.ligas().find(l => l.idLiga === idLiga);
    const respaldo = liga?.temporadaActual ?? new Date().getFullYear();

    // Se abre en la última temporada CON PARTIDOS JUGADOS: la más reciente suele ser una
    // recién sorteada, sin nada jugado todavía.
    this.ligaService.getTemporadas(idLiga).subscribe({
      next: temporadas => {
        this.temporadasDisponibles.set(temporadas);
        const porDefecto = temporadas.find(t => t.conDatos)?.temporada ?? temporadas[0]?.temporada ?? respaldo;
        this.cargarEquipos(idLiga, porDefecto);
      },
      error: () => this.cargarEquipos(idLiga, respaldo)
    });
  }

  seleccionarTemporada(temporada: number): void {
    const idLiga = this.ligaSeleccionada();
    if (idLiga != null) this.cargarEquipos(idLiga, temporada);
  }

  private cargarEquipos(idLiga: number, temporada: number): void {
    this.temporada.set(temporada);
    this.equipoService.getPorLiga(idLiga, temporada).subscribe({
      next: equipos => this.equipos.set(equipos),
      error: err => this.error.set(this.errores.mensaje(err))
    });
  }
}
