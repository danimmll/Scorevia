import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { PartidoService } from '../../../core/services/partido.service';
import { ActivatedRoute } from '@angular/router';
import { LigaService } from '../../../core/services/liga.service';
import { Partido, Liga } from '../../../core/models/estadisticas.model';
import { compararCompeticiones, agruparLigasPorCategoria } from '../../../core/utils/liga-categoria.util';
import { ImagenFallbackDirective } from '../../../core/directives/imagen-fallback.directive';
import { SelectorPaisComponent } from '../../../shared/selector-pais/selector-pais';
import { FilaPartidoComponent } from '../../../shared/fila-partido/fila-partido';
import { PaginadorComponent } from '../../../shared/paginador/paginador';
import { CargandoComponent } from '../../../shared/cargando/cargando';
import { refrescarEnVivo } from '../../../core/utils/refresco-en-vivo';
import { TranslocoPipe } from '@jsverse/transloco';
import { ErroresService } from '../../../core/services/errores.service';

@Component({
  selector: 'app-partidos-listado',
  standalone: true,
  imports: [CommonModule, RouterLink, ImagenFallbackDirective, SelectorPaisComponent, FilaPartidoComponent, PaginadorComponent, CargandoComponent, TranslocoPipe],
  templateUrl: './partidos-listado.html',
  styleUrls: ['./partidos-listado.scss']
})
export class PartidosListadoComponent implements OnInit {
  private errores = inject(ErroresService);
  private partidoService = inject(PartidoService);
  private route = inject(ActivatedRoute);
  private ligaService = inject(LigaService);

  partidosEnVivo = signal<Partido[]>([]);
  partidos = signal<Partido[]>([]);

  /**
   * Cada cuánto se vuelven a pedir los partidos en directo.
   *
   * Treinta segundos porque es lo que tarda el backend en volver a mirar la API
   * (`scorevia.ingesta.vivo.cadencia-ms`): pedir más a menudo solo devolvería lo mismo.
   */
  private static readonly REFRESCO_VIVO_MS = 30_000;

  /**
   * Y cada cuánto se rehace la lista del día.
   *
   * Menos seguido porque cambia menos: lo que aporta es que un partido que acaba de empezar
   * salte de "20:00" a "3'" y aparezca arriba en el directo. Dos minutos basta y evita
   * rehacer una lista de cientos de filas cada medio minuto.
   */
  private static readonly REFRESCO_DIA_MS = 120_000;
  pagina = signal(0);
  totalPaginas = signal(0);

  // ------------------------------------------------------------------ paginación ----
  //
  // Las tres listas de esta pantalla se paginan, y por el mismo motivo: un día cualquiera
  // trae más de 400 partidos y una tarde de sábado pasa de 1.500. Pintarlos todos de golpe
  // es un DOM enorme para ver, como mucho, los diez primeros.
  //
  // Cada lista se pagina donde puede: la de una liga la pagina el servidor (se le piden 5),
  // y el directo y el día agrupado se paginan aquí, porque sus endpoints devuelven la lista
  // entera de una vez.

  /** Partidos por página en el directo y en cada competición del día. */
  static readonly POR_PAGINA = 5;

  /** Competiciones por página en la vista del día. */
  static readonly COMPETICIONES_POR_PAGINA = 5;

  paginaEnVivo = signal(0);
  paginaCompeticiones = signal(0);

  totalPaginasEnVivo = computed(() =>
    Math.ceil(this.partidosEnVivo().length / PartidosListadoComponent.POR_PAGINA));

  enVivoPaginados = computed(() => {
    const desde = this.paginaEnVivo() * PartidosListadoComponent.POR_PAGINA;
    return this.partidosEnVivo().slice(desde, desde + PartidosListadoComponent.POR_PAGINA);
  });

  totalPaginasCompeticiones = computed(() =>
    Math.ceil(this.gruposPorCompeticion().length / PartidosListadoComponent.COMPETICIONES_POR_PAGINA));

  /**
   * Las competiciones de la página actual.
   *
   * <p>Se pagina por competición y no por partido para no partir un grupo por la mitad: la
   * gracia de esta vista es ver una jornada entera junta, y "los 5 primeros partidos" de un
   * día son siempre los de la zona horaria más madrugadora.</p>
   */
  competicionesPaginadas = computed(() => {
    const desde = this.paginaCompeticiones() * PartidosListadoComponent.COMPETICIONES_POR_PAGINA;
    return this.gruposPorCompeticion().slice(desde, desde + PartidosListadoComponent.COMPETICIONES_POR_PAGINA);
  });

  cambiarPaginaEnVivo(pagina: number): void {
    if (pagina < 0 || pagina >= this.totalPaginasEnVivo()) return;
    this.paginaEnVivo.set(pagina);
  }

  cambiarPaginaCompeticiones(pagina: number): void {
    if (pagina < 0 || pagina >= this.totalPaginasCompeticiones()) return;
    this.paginaCompeticiones.set(pagina);
  }
  cargando = signal(true);
  error = signal<string | null>(null);

  // País -> liga en cascada, mismo patrón que Equipos/Jugadores: sin país seleccionado
  // se ve el listado general (más recientes primero, orden que ya fija el backend).
  ligas = signal<Liga[]>([]);
  paisSeleccionado = signal<string | null>(null);
  ligaSeleccionada = signal<number | null>(null);
  temporadaSeleccionada = signal<number | null>(null);

  paises = computed(() => [...new Set(this.ligas().map(l => l.pais).filter((p): p is string => !!p))].sort());
  ligasDelPais = computed(() =>
    this.ligas()
      .filter(l => l.pais === this.paisSeleccionado())
      .sort((a, b) => (a.nombre ?? '').localeCompare(b.nombre ?? ''))
  );
  gruposLigasDelPais = computed(() => agruparLigasPorCategoria(this.ligasDelPais()));
  // Rango simple de temporadas recientes para el selector -- no hay un endpoint que liste
  // qué temporadas tienen datos reales para una liga concreta.
  temporadasDisponibles = computed(() => {
    const actual = new Date().getFullYear();
    return Array.from({ length: 6 }, (_, i) => actual - i);
  });

  // Sin filtro de liga se ven SOLO los partidos de hoy, agrupados por competición (tipo
  // Flashscore). Antes había debajo una sección "Más recientes" con partidos de otros días:
  // se quitó porque a una página de partidos se viene a ver qué pasa hoy, y esa tabla plana
  // mezclaba días sin que se entendiera de dónde salían.
  // Ya no hace falta filtrar: getPartidosHoy pide al servidor solo los de hoy. Cuando el
  // filtro se hacía aquí, sobre la primera página del listado general, dejó de funcionar en
  // cuanto se ingirió el calendario hasta 2027 (esa página son partidos de dentro de meses).
  partidosDeHoy = computed(() => this.partidos());
  // Los grupos salen ordenados por importancia de la competición, no por volumen ni por
  // orden de llegada: un día cualquiera lo que más partidos tiene es lo que menos interesa
  // (el 2026-09-04, "Paulista - U20" con 17 y el juvenil ruso con 8, mientras la FA Cup
  // quedaba enterrada). Ver compararCompeticiones en liga-categoria.util.
  gruposPorCompeticion = computed(() => {
    const mapa = new Map<string, {
      liga: string; idLiga?: number; pais?: string; logo?: string; bandera?: string; partidos: Partido[];
    }>();

    for (const p of this.partidosDeHoy()) {
      const clave = p.liga ?? 'Otras competiciones';
      // La bandera va en la cabecera del grupo por el mismo motivo que en la fila: hay
      // decenas de "Primera División" y de "Copa" repartidas por el mundo, y el nombre solo
      // no basta para saber cuál es cuál.
      const grupo = mapa.get(clave)
        ?? { liga: clave, idLiga: p.idLiga, pais: p.paisLiga, logo: p.logoLiga, bandera: p.banderaLiga, partidos: [] };
      grupo.partidos.push(p);
      mapa.set(clave, grupo);
    }

    return [...mapa.values()]
      .map(g => ({
        ...g,
        // Dentro de cada competición, por hora: es como se sigue una jornada.
        partidos: g.partidos.sort((a, b) => (a.fecha ?? '').localeCompare(b.fecha ?? ''))
      }))
      .sort((a, b) => compararCompeticiones(
        { idLiga: a.idLiga, nombre: a.liga, pais: a.pais },
        { idLiga: b.idLiga, nombre: b.liga, pais: b.pais },
      ));
  });

  constructor() {
    // La pantalla se mantiene sola: el directo cada 30 segundos y la lista del día cada dos
    // minutos, y las dos se paran mientras la pestaña esté en segundo plano. Va en el
    // constructor porque refrescarEnVivo usa inject().
    refrescarEnVivo(PartidosListadoComponent.REFRESCO_VIVO_MS, () => this.cargarEnVivo(false));
    refrescarEnVivo(PartidosListadoComponent.REFRESCO_DIA_MS, () => this.refrescarDia());
  }

  ngOnInit(): void {
    // El `?liga=` de la URL: es lo que hace que "Ver todos →" desde la ficha de una
    // competición llegue aquí ya filtrado por ella, en vez de soltar al usuario en los
    // partidos de hoy de todo el mundo y obligarle a repetir el filtro a mano.
    const idLigaPedida = Number(this.route.snapshot.queryParamMap.get('liga'));

    this.ligaService.getListado().subscribe({
      next: ligas => {
        this.ligas.set(ligas);
        if (Number.isFinite(idLigaPedida) && idLigaPedida > 0) {
          const liga = ligas.find(l => l.idLiga === idLigaPedida);
          if (liga) {
            this.paisSeleccionado.set(liga.pais ?? null);
            this.seleccionarLiga(idLigaPedida);
          }
        }
      }
    });

    this.cargarEnVivo(true);
    this.cargarPagina(0);

  }

  /**
   * Trae los partidos en directo.
   *
   * @param inicial en la primera carga se vuelve a la página 1; en los refrescos NO, porque
   *        devolver al usuario al principio de la lista cada medio minuto mientras está
   *        leyendo la página 3 es peor que no refrescar.
   */
  private cargarEnVivo(inicial: boolean): void {
    this.partidoService.getPartidosEnVivo().subscribe({
      next: partidos => {
        this.partidosEnVivo.set(partidos);
        if (inicial) this.paginaEnVivo.set(0);
      },
      // Un refresco que falla no borra lo que ya había en pantalla: preferible un marcador de
      // hace 30 segundos que una lista vacía por un corte de red de un instante.
      error: () => { if (inicial) this.partidosEnVivo.set([]); }
    });
  }

  /**
   * Rehace la lista del día sin tocar el estado de la pantalla.
   *
   * No usa `cargarPagina` a propósito: ese enciende el indicador de carga y reinicia la
   * paginación de competiciones, o sea que cada dos minutos la página parpadearía entera y
   * volvería al principio. Aquí solo se cambian los datos.
   */
  private refrescarDia(): void {
    if (this.ligaSeleccionada()) return;   // con filtro de liga manda el paginador del servidor

    this.partidoService.getPartidosHoy().subscribe({
      next: partidos => this.partidos.set(partidos),
      error: () => { /* se conserva lo que hubiera */ }
    });
  }

  cargarPagina(page: number): void {
    this.cargando.set(true);
    const idLiga = this.ligaSeleccionada();
    // Sin liga elegida se ve el día completo (agrupado por competición), y con liga
    // elegida el listado paginado de esa liga. Son dos formas distintas de leer la página,
    // así que también son dos peticiones distintas.
    if (!idLiga) {
      this.partidoService.getPartidosHoy().subscribe({
        next: partidos => {
          this.partidos.set(partidos);
          this.pagina.set(0);
          this.totalPaginas.set(1);
          // Cambiar de filtro devuelve a la primera página: si no, se podía quedar en la
          // página 7 de una lista que ahora tiene dos.
          this.paginaCompeticiones.set(0);
          this.cargando.set(false);
        },
        error: err => {
          this.error.set(this.errores.mensaje(err, 'No se pudieron cargar los partidos.'));
          this.cargando.set(false);
        }
      });
      return;
    }

    // size = 5: la paginación de una liga la hace el servidor, así que aquí se pide poco y
    // se navega, en vez de traer 20 y enseñar 5.
    this.partidoService.getPartidosPorLiga(
      idLiga, this.temporadaSeleccionada() ?? undefined, page, PartidosListadoComponent.POR_PAGINA).subscribe({
      next: pag => {
        this.partidos.set(pag.content);
        this.pagina.set(pag.number);
        this.totalPaginas.set(pag.totalPages);
        this.cargando.set(false);
      },
      error: err => {
        this.error.set(this.errores.mensaje(err, 'No se pudieron cargar los partidos.'));
        this.cargando.set(false);
      }
    });
  }

  seleccionarPais(pais: string): void {
    this.paisSeleccionado.set(pais || null);
    this.ligaSeleccionada.set(null);
    this.cargarPagina(0);
  }

  seleccionarLiga(idLiga: number): void {
    this.ligaSeleccionada.set(idLiga || null);
    if (!idLiga) {
      this.temporadaSeleccionada.set(null);
      this.cargarPagina(0);
      return;
    }

    // La temporada por defecto NO sale de liga.temporadaActual: ese campo lo dejaron en
    // 2010 los backfills históricos en 14 competiciones grandes, y es lo que hacía que
    // "La Liga" abriera con la clasificación 2010-11 (el Hércules en Primera). El endpoint
    // /temporadas dice cuáles tienen partidos jugados de verdad.
    const respaldo = this.ligas().find(l => l.idLiga === idLiga)?.temporadaActual ?? new Date().getFullYear();
    this.ligaService.getTemporadaPorDefecto(idLiga, respaldo).subscribe({
      next: temporada => {
        this.temporadaSeleccionada.set(temporada);
        this.cargarPagina(0);
      },
      error: () => {
        this.temporadaSeleccionada.set(respaldo);
        this.cargarPagina(0);
      }
    });
  }

  seleccionarTemporada(temporada: number): void {
    this.temporadaSeleccionada.set(temporada || null);
    this.cargarPagina(0);
  }

  limpiarFiltro(): void {
    this.paisSeleccionado.set(null);
    this.ligaSeleccionada.set(null);
    this.temporadaSeleccionada.set(null);
    this.cargarPagina(0);
  }
}
