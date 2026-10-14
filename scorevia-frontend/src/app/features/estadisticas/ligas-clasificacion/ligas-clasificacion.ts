import { Component, DestroyRef, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LigaService } from '../../../core/services/liga.service';
import { ClasificacionService } from '../../../core/services/clasificacion.service';
import { UsuarioService } from '../../../core/services/usuario.service';
import { AuthService } from '../../../core/services/auth.service';
import { Liga, Clasificacion, Partido, RankingJugador, TemporadaLiga, TipoRanking } from '../../../core/models/estadisticas.model';
import { agruparLigasPorCategoria, compararCompeticiones } from '../../../core/utils/liga-categoria.util';
import { Zona, clasificarZona, hayPlayoffPorElTitulo, zonasPresentes } from '../../../core/utils/zona-clasificacion.util';
import { ImagenFallbackDirective } from '../../../core/directives/imagen-fallback.directive';
import { SelectorPaisComponent } from '../../../shared/selector-pais/selector-pais';
import { PaginadorComponent } from '../../../shared/paginador/paginador';
import { CargandoComponent } from '../../../shared/cargando/cargando';
import { FilaPartidoComponent } from '../../../shared/fila-partido/fila-partido';
import { PartidoService } from '../../../core/services/partido.service';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslocoPipe } from '@jsverse/transloco';
import { ErroresService } from '../../../core/services/errores.service';

@Component({
  selector: 'app-ligas-clasificacion',
  standalone: true,
  imports: [CommonModule, RouterLink, ImagenFallbackDirective, SelectorPaisComponent, PaginadorComponent, CargandoComponent, FilaPartidoComponent, TranslocoPipe],
  templateUrl: './ligas-clasificacion.html',
  styleUrls: ['./ligas-clasificacion.scss']
})
export class LigasClasificacionComponent implements OnInit {
  private errores = inject(ErroresService);
  private route = inject(ActivatedRoute);
  private destroyRef = inject(DestroyRef);
  private ligaService = inject(LigaService);
  private clasificacionService = inject(ClasificacionService);
  private partidoService = inject(PartidoService);
  private usuarioService = inject(UsuarioService);
  public authService = inject(AuthService);

  ligas = signal<Liga[]>([]);
  ligaSeleccionada = signal<Liga | null>(null);
  temporada = signal<number>(new Date().getFullYear());
  // Temporadas realmente disponibles para la liga elegida. Antes esto era un <input type=
  // "number"> a pelo y la temporada por defecto salía de liga.temporadaActual, un campo que
  // mentía: en La Liga valía 2010 (por los backfills históricos), así que la página abría en
  // la clasificación 2010-11 -- donde el Hércules SÍ estaba en Primera.
  temporadasDisponibles = signal<TemporadaLiga[]>([]);
  tabla = signal<Clasificacion[]>([]);

  /**
   * Si la tabla se está pidiendo ahora mismo.
   *
   * No existía: mientras la clasificación cargaba, la pantalla no decía nada y se quedaba con
   * la tabla anterior (o vacía) sin ninguna señal. Y no era un instante — la consulta tardaba
   * 11,4 segundos la primera vez por un N+1 en el cálculo de la racha, ya arreglado. Aun así
   * el aviso se queda: la primera carga sigue yendo a la base, y una conexión lenta no
   * debería parecer una página rota.
   */
  cargandoTabla = signal(false);

  // ------------------------------------------------------- partidos de la competición ----
  //
  // La ficha de una competición enseñaba su tabla y sus goleadores, pero no sus partidos, que
  // es la otra mitad de lo que se viene a mirar ("¿cómo va la Premier?" y "¿cuándo juegan?").
  // Para verlos había que irse a Partidos y volver a filtrar por la misma liga a mano.
  partidos = signal<Partido[]>([]);
  paginaPartidos = signal(0);
  totalPaginasPartidos = signal(0);
  cargandoPartidos = signal(false);

  /**
   * La clasificación partida por grupos, o un único bloque sin título si la competición no
   * los tiene.
   *
   * Una liga normal trae una sola tabla y esto no cambia nada. Pero un Mundial o una fase
   * de grupos europea trae varias, y sin separarlas se veían todas juntas: la Europa League
   * 2016 son 47 filas con las posiciones del 1 al 4 repetidas doce veces, o sea doce
   * equipos "primeros" seguidos, que no hay forma de leer como una clasificación.
   */
  grupos = computed(() => {
    const filas = this.tabla();
    const mapa = new Map<string, Clasificacion[]>();
    for (const fila of filas) {
      const clave = fila.grupo?.trim() || '';
      const lista = mapa.get(clave) ?? [];
      lista.push(fila);
      mapa.set(clave, lista);
    }

    return [...mapa.entries()]
      .map(([grupo, filasGrupo]) => ({
        grupo,
        filas: filasGrupo.sort((a, b) => (a.posicion ?? 0) - (b.posicion ?? 0))
      }))
      .sort((a, b) => a.grupo.localeCompare(b.grupo));
  });

  /** Solo hay que pintar títulos de grupo si de verdad hay más de uno. */
  hayGrupos = computed(() => this.grupos().length > 1);
  error = signal<string | null>(null);

  esFavorito = signal(false);
  cargandoFavorito = signal(false);

  // País -> liga en cascada, mismo patrón que Equipos/Jugadores/Partidos: una lista plana de
  // todas las ligas/competiciones es imposible de buscar.
  paisSeleccionado = signal<string | null>(null);

  // Búsqueda GLOBAL (sobre this.ligas() entero, no sobre ligasDelPais): buscar "Champions"
  // debe encontrarla aunque el país seleccionado ahora mismo sea otro -- ese era justo el bug,
  // el filtro de texto solo miraba dentro del país ya elegido.
  busqueda = signal('');
  resultadosBusqueda = computed(() => {
    const texto = this.busqueda().trim().toLowerCase();
    if (!texto) return [];
    return this.ligas()
      .filter(l => (l.nombre ?? '').toLowerCase().includes(texto) || (l.pais ?? '').toLowerCase().includes(texto))
      .slice(0, 15);
  });

  /**
   * Las que se enseñan cuando no hay país elegido.
   *
   * <p>El catálogo tiene más de 1.200 competiciones y la mayoría no le dice nada a nadie
   * (juveniles, reservas, regionales). Se reaprovecha el mismo criterio de importancia que
   * ordena los partidos del día.</p>
   */
  ligasDestacadas = computed(() =>
    [...this.ligas()]
      .sort((a, b) => compararCompeticiones(
        { idLiga: a.idLiga, nombre: a.nombre, pais: a.pais },
        { idLiga: b.idLiga, nombre: b.nombre, pais: b.pais }))
      .slice(0, 48));

  // ------------------------------------------------------- escaparate de ligas ----
  //
  // La pantalla arranca aquí: una rejilla con el escudo de cada competición para elegir,
  // en vez de abrir una tabla de oficio. Paginada porque el catálogo son más de 1.200
  // competiciones y ni caben ni se recorren de una sentada.

  /** Competiciones por página del escaparate. */
  static readonly LIGAS_POR_PAGINA = 12;

  paginaLigas = signal(0);

  /** Lo que se ofrece a elegir: las del país filtrado, o las destacadas si no hay filtro. */
  ligasParaElegir = computed(() => {
    const delPais = this.ligasDelPais();
    if (delPais.length > 0) return delPais;
    return this.ligasDestacadas();
  });

  totalPaginasLigas = computed(() =>
    Math.ceil(this.ligasParaElegir().length / LigasClasificacionComponent.LIGAS_POR_PAGINA));

  ligasPaginadas = computed(() => {
    const desde = this.paginaLigas() * LigasClasificacionComponent.LIGAS_POR_PAGINA;
    return this.ligasParaElegir().slice(desde, desde + LigasClasificacionComponent.LIGAS_POR_PAGINA);
  });

  cambiarPaginaLigas(pagina: number): void {
    if (pagina < 0 || pagina >= this.totalPaginasLigas()) return;
    this.paginaLigas.set(pagina);
  }

  /** Vuelve al escaparate sin recargar nada. */
  volverAlEscaparate(): void {
    this.ligaSeleccionada.set(null);
    this.tabla.set([]);
    this.ranking.set([]);
  }

  paises = computed(() => [...new Set(this.ligas().map(l => l.pais).filter((p): p is string => !!p))].sort());
  ligasDelPais = computed(() =>
    this.ligas()
      .filter(l => l.pais === this.paisSeleccionado())
      .sort((a, b) => (a.nombre ?? '').localeCompare(b.nombre ?? ''))
  );
  // "World" mezcla Champions/Mundial con ~150 competiciones menores (juveniles, femeninas,
  // clasificatorios) sin ningún campo de categoría en el backend -- se agrupan por heurística
  // de nombre para que el desplegable no sea una lista plana imposible de recorrer.
  gruposLigasDelPais = computed(() => agruparLigasPorCategoria(this.ligasDelPais()));

  ngOnInit(): void {
    this.ligaService.getListado().subscribe({
      next: ligas => {
        this.ligas.set(ligas);
        this.abrirLigaDeLaUrl(this.route.snapshot.queryParamMap.get('liga'));
      },
      error: err => this.error.set(this.errores.mensaje(err))
    });

    // Suscripción y no `snapshot` a secas: Angular reutiliza el componente cuando se navega
    // a la MISMA ruta con otro `?liga=`, así que `ngOnInit` no se vuelve a ejecutar. Con el
    // snapshot solo, los accesos directos de competición de la barra lateral cambiaban la URL
    // y dejaban en pantalla la tabla de la liga anterior.
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      if (this.ligas().length > 0) {
        this.abrirLigaDeLaUrl(params.get('liga'));
      }
    });
  }

  /**
   * Abre la competición que pide la URL, si pide alguna.
   *
   * Sin `?liga=` no se abre nada a propósito: se enseña el escaparate con los escudos para
   * elegir. Abrir una competición por defecto era una tabla y varias peticiones que nadie
   * había pedido.
   */
  private abrirLigaDeLaUrl(valor: string | null): void {
    const id = Number(valor);
    if (!Number.isFinite(id) || id <= 0) return;
    if (this.ligaSeleccionada()?.idLiga === id) return;

    const liga = this.ligas().find(l => l.idLiga === id);
    if (liga) {
      this.paisSeleccionado.set(liga.pais ?? null);
      this.seleccionarLiga(liga);
    }
  }

  seleccionarPais(pais: string): void {
    this.paisSeleccionado.set(pais || null);
    this.paginaLigas.set(0);
    // Cambiar de país ya no abre la primera liga que haya: enseña las de ese país para
    // elegir. Abrir una sin pedirlo era una tabla y dos peticiones que nadie quería.
    this.volverAlEscaparate();
  }

  // Por id de liga, no por índice de <option>: con <optgroup> el índice de la opción
  // seleccionada ya no coincide de forma fiable con la posición en ligasDelPais().
  seleccionarLigaPorId(idLiga: number): void {
    const liga = this.ligas().find(l => l.idLiga === idLiga);
    if (liga) this.seleccionarLiga(liga);
  }

  seleccionarLiga(liga: Liga): void {
    this.ligaSeleccionada.set(liga);
    this.temporadasDisponibles.set([]);
    this.cargarEstadoFavorito(liga.idLiga);

    // La temporada por defecto es la última CON PARTIDOS JUGADOS, no la más reciente a secas:
    // la API publica la clasificación de la temporada siguiente en cuanto hay calendario, con
    // la tabla entera a cero (111 competiciones estaban así).
    this.ligaService.getTemporadas(liga.idLiga).subscribe({
      next: temporadas => {
        this.temporadasDisponibles.set(temporadas);
        const porDefecto = temporadas.find(t => t.conDatos)?.temporada
          ?? temporadas[0]?.temporada
          ?? liga.temporadaActual
          ?? new Date().getFullYear();
        this.temporada.set(porDefecto);
        this.cargarTabla();
      },
      error: () => {
        this.temporada.set(liga.temporadaActual ?? new Date().getFullYear());
        this.cargarTabla();
      }
    });
  }

  // Clic en un resultado de la búsqueda global: selecciona esa liga directamente (ajustando
  // también el país del desplegable de al lado) y cierra la lista de resultados.
  seleccionarDesdeBusqueda(liga: Liga): void {
    this.paisSeleccionado.set(liga.pais ?? null);
    this.seleccionarLiga(liga);
    this.busqueda.set('');
  }

  toggleFavorito(): void {
    const idUsuario = this.authService.currentUser()?.idUsuario;
    const idLiga = this.ligaSeleccionada()?.idLiga;
    if (!idUsuario || idLiga == null || this.cargandoFavorito()) return;

    this.cargandoFavorito.set(true);
    const accion$ = this.esFavorito()
      ? this.usuarioService.quitarFavorito(idUsuario, 'ligas', idLiga)
      : this.usuarioService.addFavorito(idUsuario, 'ligas', idLiga);

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

  private cargarEstadoFavorito(idLiga: number): void {
    const idUsuario = this.authService.currentUser()?.idUsuario;
    if (!idUsuario) {
      this.esFavorito.set(false);
      return;
    }

    this.usuarioService.getFavoritos(idUsuario, 'ligas').subscribe({
      next: favoritos => this.esFavorito.set(favoritos.some(f => f.idReferenciaApi === idLiga)),
      error: () => this.esFavorito.set(false)
    });
  }

  // -------------------------------------------------- zonas, leyenda y campeón ----
  //
  // Nada de esto cuesta una petición extra ni hace falta definirlo liga por liga: la API
  // manda, fila por fila, qué está en juego en esa posición ("Promotion - Champions League
  // (League phase)", "Relegation"...) y ya se guardaba en `estadoCompeticion` sin pintarse.

  /** La zona de una fila, para el color del borde y de la posición. */
  zonaDe(fila: Clasificacion): Zona | null {
    return clasificarZona(fila.estadoCompeticion);
  }

  /** Solo las zonas que existen en esta tabla: en una Segunda no pinta explicar la Champions. */
  leyenda = computed(() => zonasPresentes(this.tabla().map(f => f.estadoCompeticion)));

  /**
   * ¿Es una temporada ya terminada?
   *
   * <p>Se deduce de la lista de temporadas que devuelve el backend: si hay alguna posterior
   * con datos, la que se está mirando ya acabó. Es más fiable que mirar `temporadaActual` de
   * la liga, que quedó a 2010 en catorce competiciones por culpa de los backfills antiguos.</p>
   */
  temporadaAcabada = computed(() => {
    const temporadas = this.temporadasDisponibles();
    if (temporadas.length === 0) return false;
    const masReciente = Math.max(...temporadas.map(t => t.temporada));
    return this.temporada() < masReciente;
  });

  /** En una copa por grupos el primero de un grupo no es campeón de nada. */
  private tablaUnica = computed(() => !this.hayGrupos() && this.tabla().length > 0);

  /** El título se decide en playoff (MLS, Argentina...): el primero es líder, no campeón. */
  private tituloPorPlayoff = computed(() => hayPlayoffPorElTitulo(this.tabla().map(f => f.estadoCompeticion)));

  primero = computed(() => this.tabla().find(f => f.posicion === 1) ?? null);

  /**
   * Lo que se enseña arriba de la tabla: el campeón si la temporada acabó, y si está en curso,
   * quién manda ahora y quién ganó la anterior.
   */
  tituloTemporada = computed<{ etiqueta: string; equipo: string; idEquipo?: number } | null>(() => {
    const primero = this.primero();
    if (!this.tablaUnica() || !primero?.equipo?.nombre) return null;

    const etiqueta = !this.temporadaAcabada()
      ? 'Líder'
      : (this.tituloPorPlayoff() ? '1º de la fase regular' : 'Campeón');

    return { etiqueta, equipo: primero.equipo.nombre, idEquipo: primero.equipo.idEquipo };
  });

  /** Campeón de la temporada anterior, solo mientras la actual está por decidir. */
  campeonAnterior = signal<{ temporada: number; equipo: string; idEquipo?: number } | null>(null);

  /**
   * Busca quién ganó la temporada anterior.
   *
   * <p>Se pide la clasificación de esa temporada a nuestro propio backend (donde ya está
   * cacheada) y se coge el primero. No cuesta ninguna petición a API-Football.</p>
   */
  private cargarCampeonAnterior(): void {
    this.campeonAnterior.set(null);
    const liga = this.ligaSeleccionada();
    if (!liga || this.temporadaAcabada()) return;

    const anteriores = this.temporadasDisponibles()
      .map(t => t.temporada)
      .filter(t => t < this.temporada())
      .sort((a, b) => b - a);
    const anterior = anteriores[0];
    if (anterior == null) return;

    this.clasificacionService.getTabla(liga.idLiga, anterior).subscribe({
      next: tabla => {
        // Con grupos no hay un campeón que sacar de la tabla.
        const hayGrupos = new Set(tabla.map(f => f.grupo ?? '')).size > 1;
        const primero = tabla.find(f => f.posicion === 1);
        if (!hayGrupos && primero?.equipo?.nombre) {
          this.campeonAnterior.set({
            temporada: anterior,
            equipo: primero.equipo.nombre,
            idEquipo: primero.equipo.idEquipo
          });
        }
      },
      // Best-effort: es un adorno de la cabecera, no la pantalla.
      error: () => this.campeonAnterior.set(null)
    });
  }

  cargarTabla(): void {
    const liga = this.ligaSeleccionada();
    if (!liga) return;
    this.cargandoTabla.set(true);
    this.clasificacionService.getTabla(liga.idLiga, this.temporada()).subscribe({
      next: tabla => {
        this.tabla.set(tabla);
        this.cargandoTabla.set(false);
        this.cargarCampeonAnterior();
      },
      error: err => {
        this.error.set(this.errores.mensaje(err));
        this.cargandoTabla.set(false);
      }
    });
    this.cargarRanking();
    this.cargarPartidos(0);
  }

  /** Los partidos de la competición y temporada elegidas, de 8 en 8. */
  cargarPartidos(pagina: number): void {
    const liga = this.ligaSeleccionada();
    if (!liga) return;

    this.paginaPartidos.set(pagina);
    this.cargandoPartidos.set(true);
    this.partidoService.getPartidosPorLiga(liga.idLiga, this.temporada(), pagina, 8).subscribe({
      next: pag => {
        this.partidos.set(pag.content);
        this.totalPaginasPartidos.set(pag.totalPages);
        this.cargandoPartidos.set(false);
      },
      // Sin romper la pantalla: la tabla y los goleadores siguen ahí aunque esto falle.
      error: () => {
        this.partidos.set([]);
        this.totalPaginasPartidos.set(0);
        this.cargandoPartidos.set(false);
      }
    });
  }

  // --------------------------------------------------------------------- rankings ----
  //
  // Goleadores, asistentes y tarjetas de la competición. Cada tipo es una petición distinta
  // a API-Football del lado del backend (cacheada 6h allí), así que solo se pide el que se
  // está mirando: cambiar de pestaña carga el suyo y no los cuatro de golpe.

  tipoRanking = signal<TipoRanking>('GOLES');
  ranking = signal<RankingJugador[]>([]);
  cargandoRanking = signal(false);

  /** Jugadores por página del ranking: un top 10 y se navega. */
  static readonly RANKING_POR_PAGINA = 10;

  paginaRanking = signal(0);

  totalPaginasRanking = computed(() =>
    Math.ceil(this.ranking().length / LigasClasificacionComponent.RANKING_POR_PAGINA));

  rankingPaginado = computed(() => {
    const desde = this.paginaRanking() * LigasClasificacionComponent.RANKING_POR_PAGINA;
    return this.ranking().slice(desde, desde + LigasClasificacionComponent.RANKING_POR_PAGINA);
  });

  cambiarPaginaRanking(pagina: number): void {
    if (pagina < 0 || pagina >= this.totalPaginasRanking()) return;
    this.paginaRanking.set(pagina);
  }

  readonly tiposRanking: { clave: TipoRanking; etiqueta: string; columna: string }[] = [
    { clave: 'GOLES', etiqueta: 'Goleadores', columna: 'Goles' },
    { clave: 'ASISTENCIAS', etiqueta: 'Asistentes', columna: 'Asist.' },
    { clave: 'AMARILLAS', etiqueta: 'Amarillas', columna: 'TA' },
    { clave: 'ROJAS', etiqueta: 'Rojas', columna: 'TR' }
  ];

  columnaRanking = computed(() =>
    this.tiposRanking.find(t => t.clave === this.tipoRanking())?.columna ?? 'Total');

  cambiarTipoRanking(tipo: TipoRanking): void {
    if (this.tipoRanking() === tipo) return;
    this.tipoRanking.set(tipo);
    this.paginaRanking.set(0);
    this.cargarRanking();
  }

  private cargarRanking(): void {
    const liga = this.ligaSeleccionada();
    if (!liga) return;

    this.cargandoRanking.set(true);
    this.ranking.set([]);
    this.ligaService.getRanking(liga.idLiga, this.temporada(), this.tipoRanking()).subscribe({
      next: filas => {
        this.ranking.set(filas);
        this.paginaRanking.set(0);
        this.cargandoRanking.set(false);
      },
      // Best-effort: hay competiciones que no publican estas estadísticas. La clasificación
      // se ve igual y esta sección simplemente no aparece.
      error: () => {
        this.ranking.set([]);
        this.cargandoRanking.set(false);
      }
    });
  }
}
