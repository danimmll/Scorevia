import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { PartidoService } from '../../../core/services/partido.service';
import { PrediccionService } from '../../../core/services/prediccion.service';
import { AuthService } from '../../../core/services/auth.service';
import { UsuarioService } from '../../../core/services/usuario.service';
import {
  PartidoDetalleCompleto, EventoPartido, EquipoAlineacion, JugadorAlineacion,
  CuotasPartido, BajaPartido
} from '../../../core/models/estadisticas.model';
import { ProbabilidadesPrediccion, LivePrediction } from '../../../core/models/prediccion.model';
import { porcentajeLadoA } from '../../../core/utils/comparativa.util';
import { rondaLegible } from '../../../core/utils/ronda.util';
import { etiquetaMinuto } from '../../../core/utils/minuto-partido.util';
import { ImagenFallbackDirective } from '../../../core/directives/imagen-fallback.directive';
import { PaginadorComponent } from '../../../shared/paginador/paginador';
import { CargandoComponent } from '../../../shared/cargando/cargando';
import { refrescarEnVivo } from '../../../core/utils/refresco-en-vivo';
import { TranslocoPipe } from '@jsverse/transloco';
import { ErroresService } from '../../../core/services/errores.service';

/** Las cuatro líneas de un campo, en el orden en que se pintan de arriba abajo. */
const ORDEN_LINEAS = ['G', 'D', 'M', 'F'];

@Component({
  selector: 'app-partido-detail',
  standalone: true,
  imports: [CommonModule, RouterModule, ImagenFallbackDirective, PaginadorComponent, CargandoComponent, TranslocoPipe],
  templateUrl: './partido-detail.html',
  styleUrls: ['./partido-detail.scss']
})
export class PartidoDetailComponent implements OnInit {
  private errores = inject(ErroresService);
  private route = inject(ActivatedRoute);
  private partidoService = inject(PartidoService);
  private prediccionService = inject(PrediccionService);
  private usuarioService = inject(UsuarioService);
  private cuotasService = inject(PartidoService);
  public authService = inject(AuthService);

  /** Cada cuánto se vuelve a pedir un partido en juego (lo mismo que tarda el backend). */
  private static readonly REFRESCO_VIVO_MS = 30_000;

  detalle = signal<PartidoDetalleCompleto | null>(null);
  cargando = signal(true);
  error = signal<string | null>(null);

  prediccionPrePartido = signal<ProbabilidadesPrediccion | null>(null);
  prediccionEnVivo = signal<LivePrediction | null>(null);
  errorPrediccion = signal<string | null>(null);

  // Los ids de sch_estadisticas son directamente los de la API externa (ver
  // docs/back/modelo-datos.md), así que estadísticas y predicciones comparten el mismo id.
  private idPartido = 0;
  private idEquipoLocal = 0;
  private idEquipoVisitante = 0;

  // El H2H se pagina aparte del resto (que llega todo junto en un forkJoin): un historial
  // entre dos clubes con años de enfrentamientos puede ser larguísimo.
  h2h = signal<PartidoDetalleCompleto['h2h']['content']>([]);
  paginaH2h = signal(0);
  totalPaginasH2h = signal(0);

  esFavorito = signal(false);
  cargandoFavorito = signal(false);

  // ------------------------------------------------------------------- cuotas y bajas ----
  //
  // Las dos se piden aparte del forkJoin del resto: cada una cuesta una petición a
  // API-Football del lado del backend (cacheada allí), y ninguna es imprescindible para
  // pintar la pantalla. Si tardan o fallan, el partido se ve igual.

  cuotas = signal<CuotasPartido | null>(null);
  bajas = signal<BajaPartido[]>([]);

  bajasLocal = computed(() => this.bajas().filter(b => b.idEquipo === this.detalle()?.info.idEquipoLocal));
  bajasVisitante = computed(() => this.bajas().filter(b => b.idEquipo === this.detalle()?.info.idEquipoVisitante));

  /** true si el partido se decidió en la tanda de penaltis. */
  /** El minuto (o la fase con el reloj parado) si el partido se esta jugando. */
  minutoEnVivo = computed(() => {
    const info = this.detalle()?.info;
    return info ? etiquetaMinuto(info) : null;
  });

  /** El estado traducido, para cuando no hay minuto que ensenar. */
  claveEstado = computed(() => {
    switch (this.detalle()?.info?.estado) {
      case 'EN_JUEGO': return 'match_status_live';
      case 'FINALIZADO': return 'match_status_finished';
      case 'PROGRAMADO': return 'match_status_scheduled';
      case 'APLAZADO': return 'match_status_postponed';
      default: return 'match_status_scheduled';
    }
  });

  huboPenaltis = computed(() => {
    const info = this.detalle()?.info;
    return info?.penaltisLocal != null && info?.penaltisVisitante != null;
  });

  /**
   * "Jornada 12", "Cuartos de final", "Fase de grupos · A"... o null si no consta.
   *
   * <p>Antes la cabecera pintaba "Jornada {{ jornada }}" sin condición, así que un partido de
   * eliminatoria salía como "Mundial · Jornada " con el hueco vacío: en copa no hay número
   * que sacar de "Quarter-finals".</p>
   */
  fase = computed(() => rondaLegible(this.detalle()?.info.ronda, this.detalle()?.info.jornada));

  // ------------------------------------------------------------------ alineaciones ----

  alineaciones = computed(() => this.detalle()?.alineaciones ?? null);

  /**
   * El once repartido en filas de campo, de portero a delanteros.
   *
   * <p>Se agrupa aquí y no en el backend porque el DTO manda una lista plana ya ordenada: la
   * fila es cosa de cómo se pinta, no del dato.</p>
   */
  lineasDe(equipo: EquipoAlineacion | undefined | null, invertir = false): { linea: string; jugadores: JugadorAlineacion[] }[] {
    if (!equipo) return [];
    const filas = ORDEN_LINEAS
      .map(linea => ({ linea, jugadores: equipo.titulares.filter(j => j.linea === linea) }))
      .filter(fila => fila.jugadores.length > 0);
    // El visitante se pinta desde su portería hacia el centro del campo, o sea con las
    // líneas al revés: los dos equipos quedan enfrentados como en un campo de verdad en
    // vez de mirando los dos hacia el mismo lado.
    return invertir ? filas.reverse() : filas;
  }

  /** Titulares sin línea reconocible: se listan aparte para no perderlos del campo. */
  titularesSinLinea(equipo: EquipoAlineacion | undefined | null): JugadorAlineacion[] {
    if (!equipo) return [];
    return equipo.titulares.filter(j => !j.linea || !ORDEN_LINEAS.includes(j.linea));
  }

  /**
   * true cuando NINGÚN titular trae línea, o sea que no hay dibujo que pintar.
   *
   * <p>Pasa en los partidos antiguos: API-Football da los once y el banquillo desde 2015,
   * pero la posición de cada jugador (y el dibujo, y el sitio en el campo) solo a partir de
   * 2021 más o menos. Con esto, en vez de amontonar once fichas en una sola fila fingiendo
   * un campo, se enseñan como lista y se dice por qué.</p>
   */
  sinDibujo(equipo: EquipoAlineacion | undefined | null): boolean {
    if (!equipo || equipo.titulares.length === 0) return false;
    return this.lineasDe(equipo).length === 0;
  }

  // El array statsEquipos trae una entrada por equipo sin distinguir local/visitante -- se
  // empareja aquí por nombre (no por idEquipo: en filas antiguas esa FK quedó sin enlazar en
  // la ingesta y llega null desde el backend, así que emparejar por id fallaría en silencio).
  statsLocal = computed(() => {
    const d = this.detalle();
    return d?.statsEquipos.find(s => s.nombreEquipo === d.info.equipoLocal) ?? null;
  });
  statsVisitante = computed(() => {
    const d = this.detalle();
    return d?.statsEquipos.find(s => s.nombreEquipo === d.info.equipoVisitante) ?? null;
  });

  // % de ancho de la barra que le corresponde al valor "local" en una comparativa de dos
  // valores (el resto, hasta 100, es del visitante). 0-0 se reparte al 50% para no dejar
  // una barra colapsada sin información.
  // El mismo reparto que usa el comparador de equipos, en un solo sitio.
  pctBarraLocal = porcentajeLadoA;

  gradientePosesion(posesionLocal?: number | null): string {
    const p = posesionLocal ?? 50;
    return `conic-gradient(var(--sv-accent) 0% ${p}%, var(--sv-border-strong) ${p}% 100%)`;
  }

  // La API separa "tipo" ("Card"/"Goal"/"subst") del color/detalle real ("Yellow Card",
  // "Red Card") en el campo "detalle" -- comprobar solo "tipo" nunca distinguía roja de
  // amarilla, las dos caían siempre en el mismo caso.
  iconoEvento(tipo?: string, detalle?: string): string {
    const t = (tipo ?? '').toLowerCase();
    const d = (detalle ?? '').toLowerCase();
    if (t.includes('gol') || t.includes('goal')) return '⚽';
    if (t.includes('card')) {
      if (d.includes('red') || d.includes('second yellow')) return '🟥';
      return '🟨';
    }
    if (t.includes('sust') || t.includes('subst')) return '🔁';
    if (t.includes('var')) return '📺';
    return '•';
  }

  // No hay un campo "parte" en los eventos, solo el minuto -- 45 como corte es la
  // convención estándar (el descuento del primer tiempo sigue reportándose como minuto 45
  // + tiempoExtra, no como 46+, así que el corte no se mueve por el añadido).
  private esPrimeraParte(e: EventoPartido): boolean {
    return (e.tiempoMinuto ?? 0) <= 45;
  }
  gruposEventos = computed(() => {
    const eventos = this.detalle()?.eventos ?? [];
    const primera = eventos.filter(e => this.esPrimeraParte(e));
    const segunda = eventos.filter(e => !this.esPrimeraParte(e));
    const grupos: { titulo: string; eventos: EventoPartido[] }[] = [];
    if (primera.length > 0) grupos.push({ titulo: 'Primera parte', eventos: primera });
    if (segunda.length > 0) grupos.push({ titulo: 'Segunda parte', eventos: segunda });
    return grupos;
  });

  // Empareja por nombre de equipo (no por id -- ver statsLocal/statsVisitante más arriba,
  // mismo motivo) para saber en qué lado del timeline va cada evento.
  esEventoLocal(e: EventoPartido): boolean {
    return e.nombreEquipo === this.detalle()?.info.equipoLocal;
  }

  constructor() {
    // Un partido en juego se refresca solo. Antes había que recargar la página a mano para ver
    // un gol o una tarjeta, que es justo lo contrario de lo que se viene a hacer aquí. Va en
    // el constructor porque refrescarEnVivo usa inject(); el primer turno no llega hasta 30
    // segundos después, con ngOnInit ya ejecutado.
    refrescarEnVivo(PartidoDetailComponent.REFRESCO_VIVO_MS, () => this.refrescarSiEstaEnJuego());
  }

  ngOnInit(): void {
    this.idPartido = Number(this.route.snapshot.paramMap.get('idPartido'));
    this.idEquipoLocal = Number(this.route.snapshot.queryParamMap.get('local') ?? 0);
    this.idEquipoVisitante = Number(this.route.snapshot.queryParamMap.get('visitante') ?? 0);

    this.partidoService.getPartidoDetalleCompleto(this.idPartido, this.idEquipoLocal, this.idEquipoVisitante).subscribe({
      next: detalle => {
        this.detalle.set(detalle);
        this.h2h.set(detalle.h2h.content);
        this.paginaH2h.set(detalle.h2h.number);
        this.totalPaginasH2h.set(detalle.h2h.totalPages);
        this.cargando.set(false);
        this.cargarPrediccion(detalle.info.estado);
      },
      error: err => {
        this.error.set(this.errores.mensaje(err, 'No se pudo cargar el partido.'));
        this.cargando.set(false);
      }
    });

    this.cargarEstadoFavorito();
    this.cargarCuotas();
    this.cargarBajas();
  }

  /**
   * Vuelve a pedir el partido, pero solo mientras se está jugando.
   *
   * Un partido terminado ya no cambia, y uno que empieza dentro de tres horas tampoco: seguir
   * pidiéndolos sería gastar peticiones para recibir lo mismo. Cuando el partido acaba, el
   * propio refresco trae el estado FINALIZADO y a partir de ahí deja de hacer nada.
   *
   * No toca `cargando` ni la paginación del H2H: encender el esqueleto de carga cada medio
   * minuto haría parpadear la ficha entera mientras se está leyendo.
   */
  private refrescarSiEstaEnJuego(): void {
    if (this.detalle()?.info.estado !== 'EN_JUEGO') return;

    this.partidoService.getPartidoDetalleCompleto(this.idPartido, this.idEquipoLocal, this.idEquipoVisitante)
      .subscribe({
        next: detalle => {
          this.detalle.set(detalle);
          this.cargarPrediccion(detalle.info.estado);
        },
        // Un refresco fallido deja lo que ya había: mejor el marcador de hace 30 segundos que
        // un mensaje de error encima de una ficha que se estaba leyendo.
        error: () => { /* se conserva lo anterior */ }
      });
  }

  private cargarCuotas(): void {
    this.partidoService.getCuotas(this.idPartido).subscribe({
      next: cuotas => this.cuotas.set(cuotas),
      // Best-effort: sin cuotas la ficha se pinta igual, solo sin esa tarjeta.
      error: () => this.cuotas.set(null)
    });
  }

  private cargarBajas(): void {
    this.partidoService.getBajas(this.idPartido).subscribe({
      next: bajas => this.bajas.set(bajas),
      error: () => this.bajas.set([])
    });
  }

  cargarH2h(page: number): void {
    this.partidoService.getEnfrentamientos(this.idEquipoLocal, this.idEquipoVisitante, page).subscribe({
      next: pag => {
        this.h2h.set(pag.content);
        this.paginaH2h.set(pag.number);
        this.totalPaginasH2h.set(pag.totalPages);
      }
    });
  }

  toggleFavorito(): void {
    const idUsuario = this.authService.currentUser()?.idUsuario;
    if (!idUsuario || this.cargandoFavorito()) return;

    this.cargandoFavorito.set(true);
    const accion$ = this.esFavorito()
      ? this.usuarioService.quitarFavorito(idUsuario, 'partidos', this.idPartido)
      : this.usuarioService.addFavorito(idUsuario, 'partidos', this.idPartido);

    accion$.subscribe({
      next: () => {
        this.esFavorito.set(!this.esFavorito());
        this.cargandoFavorito.set(false);
      },
      error: err => {
        this.error.set(this.errores.mensaje(err, 'No se pudo actualizar el favorito.'));
        this.cargandoFavorito.set(false);
      }
    });
  }

  private cargarEstadoFavorito(): void {
    const idUsuario = this.authService.currentUser()?.idUsuario;
    if (!idUsuario) return;

    this.usuarioService.getFavoritos(idUsuario, 'partidos').subscribe({
      next: favoritos => this.esFavorito.set(favoritos.some(f => f.idReferenciaApi === this.idPartido)),
      // Best-effort: si falla la carga inicial, la estrella arranca en "no favorito" -- el
      // usuario puede seguir marcándolo/desmarcándolo igual, solo pierde el estado inicial.
      error: () => this.esFavorito.set(false)
    });
  }

  private cargarPrediccion(estado?: string): void {
    if (!this.authService.currentUser()) return;

    if (estado === 'EN_JUEGO') {
      this.prediccionService.getPrediccionEnVivo(this.idPartido, 0).subscribe({
        next: pred => this.prediccionEnVivo.set(pred),
        error: err => this.manejarErrorPrediccion(err)
      });
    } else {
      this.prediccionService.getPrediccionPrePartido(this.idPartido).subscribe({
        next: pred => this.prediccionPrePartido.set(pred),
        error: err => this.manejarErrorPrediccion(err)
      });
    }
  }

  private manejarErrorPrediccion(err: unknown): void {
    this.errorPrediccion.set(this.errores.mensaje(err, 'Predicción no disponible.'));
  }
}
