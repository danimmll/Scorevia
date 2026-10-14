import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EquipoService } from '../../../../core/services/equipo.service';
import { JugadorService } from '../../../../core/services/jugador.service';
import { EntrenadorService } from '../../../../core/services/entrenador.service';
import { PartidoService } from '../../../../core/services/partido.service';
import { Equipo, Jugador, Traspaso, Entrenador, Partido, HistorialEntrenador } from '../../../../core/models/estadisticas.model';
import { descripcionTraspaso, etiquetaTipoTraspaso } from '../../../../core/utils/traspaso.util';
import { ImagenFallbackDirective } from '../../../../core/directives/imagen-fallback.directive';
import { PaginadorComponent } from '../../../../shared/paginador/paginador';
import { CargandoComponent } from '../../../../shared/cargando/cargando';
import { paginarEnCliente } from '../../../../core/utils/paginacion-cliente';
import { TranslocoPipe } from '@jsverse/transloco';
import { ErroresService } from '../../../../core/services/errores.service';

@Component({
  selector: 'app-equipo-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, ImagenFallbackDirective, PaginadorComponent, CargandoComponent, TranslocoPipe],
  templateUrl: './equipo-detail.html',
  styleUrls: ['./equipo-detail.scss']
})
export class EquipoDetailComponent implements OnInit {
  private errores = inject(ErroresService);
  // El backend guarda un código estable en `tipo` (no el texto crudo de la API, que venía con
  // redacciones distintas para la misma operación); la etiqueta visible se decide en el
  // cliente, que es donde tendrá que traducirse el día que la interfaz sea multiidioma.
  readonly etiquetaTipo = etiquetaTipoTraspaso;
  readonly descripcion = descripcionTraspaso;

  private route = inject(ActivatedRoute);
  private equipoService = inject(EquipoService);
  private jugadorService = inject(JugadorService);
  private entrenadorService = inject(EntrenadorService);
  private partidoService = inject(PartidoService);

  equipo = signal<Equipo | null>(null);
  entrenador = signal<Entrenador | null>(null);
  historialEntrenadores = signal<HistorialEntrenador[]>([]);

  /**
   * El banquillo, paginado.
   *
   * Es la unica lista de esta ficha que quedaba sin paginar, y no es corta: la tabla tiene
   * una fila por entrenador Y temporada, asi que un club con historia larga la abria con
   * decenas de filas seguidas — el Real Madrid, 43. De ocho en ocho cabe sin empujar hacia
   * abajo el resto de la ficha.
   */
  banquilloPaginado = paginarEnCliente(this.historialEntrenadores, 8);
  traspasos = signal<Traspaso[]>([]);
  // Dos listas separadas: lo ya jugado y lo que viene. Antes era una sola mezclada en la
  // que los partidos futuros (sin marcador) salían intercalados entre los resultados.
  jugados = signal<Partido[]>([]);
  paginaJugados = signal(0);
  totalPaginasJugados = signal(0);

  programados = signal<Partido[]>([]);
  paginaProgramados = signal(0);
  totalPaginasProgramados = signal(0);
  plantilla = signal<Jugador[]>([]);
  paginaPlantilla = signal(0);
  totalPaginasPlantilla = signal(0);
  cargando = signal(true);
  error = signal<string | null>(null);

  // Lista acotada por naturaleza, paginada en el cliente -- ya llega ordenada por fecha
  // descendente desde el backend (más reciente primero).
  private readonly TAMANIO_PAGINA_TRASPASOS = 5;
  paginaTraspasos = signal(0);
  traspasosVisibles = computed(() => {
    const inicio = this.paginaTraspasos() * this.TAMANIO_PAGINA_TRASPASOS;
    return this.traspasos().slice(inicio, inicio + this.TAMANIO_PAGINA_TRASPASOS);
  });
  totalPaginasTraspasos = computed(() => Math.max(1, Math.ceil(this.traspasos().length / this.TAMANIO_PAGINA_TRASPASOS)));

  private idEquipo = 0;

  /**
   * Las temporadas que este equipo llego a jugar, para poder mirar hacia atras.
   *
   * La ficha ensena la plantilla de HOY y los ultimos partidos; para saber quien ganaba la
   * Liga con este club en 2015 no habia ningun sitio donde ir. Solo se pintan unas cuantas,
   * porque hay equipos con veinte temporadas y esto es un acceso, no un listado.
   */
  temporadas = signal<number[]>([]);
  temporadasVisibles = computed(() => this.temporadas().slice(0, 8));

  ngOnInit(): void {
    this.idEquipo = Number(this.route.snapshot.paramMap.get('idEquipo'));

    if (!Number.isFinite(this.idEquipo)) {
      this.error.set('Equipo no válido.');
      this.cargando.set(false);
      return;
    }

    this.equipoService.getTemporadas(this.idEquipo).subscribe({
      next: t => this.temporadas.set(t),
      // Un acceso de mas, no el contenido: si falla, la ficha se ve igual.
      error: () => this.temporadas.set([])
    });

    this.equipoService.getPorId(this.idEquipo).subscribe({
      next: equipo => {
        this.equipo.set(equipo);
        this.cargando.set(false);
      },
      error: err => {
        this.error.set(this.errores.mensaje(err, 'No se pudo cargar el equipo.'));
        this.cargando.set(false);
      }
    });

    // Best-effort, mismo patrón que jugador-detail: sin entrenador registrado o sin
    // traspasos no debe tumbar la ficha del equipo, esas secciones simplemente quedan vacías.
    this.entrenadorService.getPorEquipo(this.idEquipo).subscribe({
      next: entrenador => this.entrenador.set(entrenador),
      error: () => this.entrenador.set(null)
    });
    this.entrenadorService.getHistorialPorEquipo(this.idEquipo).subscribe({
      next: historial => this.historialEntrenadores.set(historial),
      error: () => this.historialEntrenadores.set([])
    });
    this.equipoService.getTraspasos(this.idEquipo).subscribe({
      next: traspasos => this.traspasos.set(traspasos),
      error: () => this.traspasos.set([])
    });
    this.cargarJugados(0);
    this.cargarProgramados(0);
    this.cargarPlantilla(0);
  }

  // Páginas de 5 a propósito: el historial de un equipo puede ser larguísimo, y volcarlo
  // entero en una sola pantalla era justo la queja de "scroll impresionante".
  cargarJugados(page: number): void {
    this.partidoService.getPartidosPorEquipo(this.idEquipo, page, 5, 'JUGADOS').subscribe({
      next: pag => {
        this.jugados.set(pag.content);
        this.paginaJugados.set(pag.number);
        this.totalPaginasJugados.set(pag.totalPages);
      },
      error: () => this.jugados.set([])
    });
  }

  cargarProgramados(page: number): void {
    this.partidoService.getPartidosPorEquipo(this.idEquipo, page, 5, 'PROGRAMADOS').subscribe({
      next: pag => {
        this.programados.set(pag.content);
        this.paginaProgramados.set(pag.number);
        this.totalPaginasProgramados.set(pag.totalPages);
      },
      error: () => this.programados.set([])
    });
  }

  cargarPlantilla(page: number): void {
    this.jugadorService.getPorEquipo(this.idEquipo, page).subscribe({
      next: pag => {
        this.plantilla.set(pag.content);
        this.paginaPlantilla.set(pag.number);
        this.totalPaginasPlantilla.set(pag.totalPages);
      },
      error: () => this.plantilla.set([])
    });
  }
}
