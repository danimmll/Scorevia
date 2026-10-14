import { Component, HostListener, OnInit, OnDestroy, PLATFORM_ID, inject, signal, computed } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { ThemeService } from '../../core/services/theme.service'; // <-- Importación del tema recuperada
import { PartidoService } from '../../core/services/partido.service';
import { EquipoService } from '../../core/services/equipo.service';
import { JugadorService } from '../../core/services/jugador.service';
import { LigaService } from '../../core/services/liga.service';
import { Partido, Equipo, Jugador, Liga } from '../../core/models/estadisticas.model';
import { ImagenFallbackDirective } from '../../core/directives/imagen-fallback.directive';
import { etiquetaMinuto } from '../../core/utils/minuto-partido.util';
import { competicionesDestacadas as elegirDestacadas } from '../../core/utils/liga-categoria.util';
import { TranslocoPipe } from '@jsverse/transloco';
import { IdiomaService, IDIOMAS } from '../../core/i18n/idioma.service';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [CommonModule, RouterModule, ImagenFallbackDirective, TranslocoPipe],
  templateUrl: './main-layout.html',
  styleUrls: ['./main-layout.scss']
})
export class MainLayoutComponent implements OnInit, OnDestroy {
  public authService = inject(AuthService);
  public themeService = inject(ThemeService);
  public idiomaService = inject(IdiomaService);
  readonly idiomas = IDIOMAS;
  private partidoService = inject(PartidoService);
  private equipoService = inject(EquipoService);
  private jugadorService = inject(JugadorService);
  private ligaService = inject(LigaService);

  private router = inject(Router);

  // Todos los tabs viven arriba ahora; el riel izquierdo se repropone para lo que
  // interesa "de un vistazo" sin navegar: partidos en vivo. No es contenido de
  // navegación -- por eso no compite con la barra superior.
  partidosEnVivo = signal<Partido[]>([]);

  /**
   * Cada cuánto se vuelven a pedir los partidos en vivo.
   *
   * Treinta segundos porque es la cadencia del scheduler que los actualiza en el backend
   * (`scorevia.ingesta.vivo.cadencia-ms`): pedir más a menudo no traería nada nuevo, solo
   * más peticiones.
   */
  private static readonly REFRESCO_EN_VIVO_MS = 30_000;

  private readonly enNavegador = isPlatformBrowser(inject(PLATFORM_ID));
  private refrescoEnVivoId?: ReturnType<typeof setInterval>;

  /** El minuto o la fase ("67'", "Descanso", "Penaltis"). Null si el partido no está en juego. */
  protected readonly etiquetaMinuto = etiquetaMinuto;

  /**
   * Cuántos partidos en vivo caben en el riel antes de mandar a la pantalla completa.
   *
   * Una tarde de sábado hay cientos a la vez: la lista entera convertiría la barra en un
   * scroll infinito y taparía los accesos directos de competición que van debajo.
   */
  private static readonly VIVOS_EN_EL_RIEL = 5;

  /** Los primeros en empezar, que son los que van más avanzados: el backend ya los ordena. */
  vivosDestacados = computed(() => this.partidosEnVivo().slice(0, MainLayoutComponent.VIVOS_EN_EL_RIEL));

  /**
   * Accesos directos a las competiciones más seguidas.
   *
   * Llevan a la ficha de la competición (clasificación y partidos), que es a lo que se entra
   * cuando alguien piensa "a ver cómo va la Premier". Antes había que ir a Competiciones,
   * buscar el país y elegir de una rejilla de más de mil escudos.
   */
  competicionesDestacadas = computed(() => elegirDestacadas(this.ligas(), 8));
  // Un único desplegable abierto a la vez: con un booleano por menú, abrir uno dejaba el
  // anterior desplegado encima. null = todos cerrados.
  menuAbierto = signal<'admin' | null>(null);

  // Buscador único: antes había un buscador de equipo/jugador/liga distinto en cada
  // página, cada uno limitado a su propio contexto. Uno solo aquí arriba, que busca en
  // los tres a la vez y navega directo al resultado.
  busqueda = signal('');
  buscando = signal(false);
  resultadosEquipos = signal<Equipo[]>([]);
  resultadosJugadores = signal<Jugador[]>([]);
  /** El catálogo de competiciones. Signal y no campo normal: de él cuelgan dos computed. */
  private ligas = signal<Liga[]>([]);
  private debounceId?: ReturnType<typeof setTimeout>;

  resultadosLigas = computed(() => {
    const texto = this.busqueda().trim().toLowerCase();
    if (!texto) return [];
    return this.ligas()
      .filter(l => (l.nombre ?? '').toLowerCase().includes(texto) || (l.pais ?? '').toLowerCase().includes(texto))
      .slice(0, 5);
  });
  hayResultados = computed(() =>
    this.resultadosEquipos().length > 0 || this.resultadosJugadores().length > 0 || this.resultadosLigas().length > 0
  );

  ngOnInit(): void {
    this.cargarPartidosEnVivo();
    this.ligaService.getListado().subscribe({ next: ligas => this.ligas.set(ligas) });

    // El marcador se pedía UNA vez al arrancar y no se volvía a pedir nunca: quien dejara la
    // pestaña abierta veía el resultado congelado de cuando entró, y los partidos que iban
    // terminando no se caían nunca de la lista. Con la recarga periódica el riel se mantiene
    // solo, porque el backend ya deja de devolver un partido en cuanto deja de estar EN_JUEGO.
    //
    // Solo en el navegador: en SSR un intervalo abierto impide que Angular considere la
    // aplicación estable y el renderizado del servidor se quedaría esperándolo.
    if (this.enNavegador) {
      this.refrescoEnVivoId = setInterval(
        () => this.cargarPartidosEnVivo(), MainLayoutComponent.REFRESCO_EN_VIVO_MS);
    }
  }

  ngOnDestroy(): void {
    clearTimeout(this.debounceId);
    clearInterval(this.refrescoEnVivoId);
  }

  private cargarPartidosEnVivo(): void {
    this.partidoService.getPartidosEnVivo().subscribe({
      next: partidos => this.partidosEnVivo.set(partidos),
      // Se deja lo que hubiera: un fallo puntual de red no tiene por qué vaciar el marcador
      // que el usuario está mirando. Si de verdad ya no hay partidos, la siguiente respuesta
      // buena llegará vacía y lo limpiará.
      error: () => { /* se conserva la última lista buena */ }
    });
  }

  buscar(texto: string): void {
    this.busqueda.set(texto);
    clearTimeout(this.debounceId);
    const consulta = texto.trim();
    if (!consulta) {
      this.resultadosEquipos.set([]);
      this.resultadosJugadores.set([]);
      this.buscando.set(false);
      return;
    }
    this.buscando.set(true);
    this.debounceId = setTimeout(() => {
      this.equipoService.buscar(consulta).subscribe({
        next: r => this.resultadosEquipos.set(r.slice(0, 5)),
        error: () => this.resultadosEquipos.set([])
      });
      this.jugadorService.buscar(consulta, 0, 5).subscribe({
        next: r => { this.resultadosJugadores.set(r.content); this.buscando.set(false); },
        error: () => { this.resultadosJugadores.set([]); this.buscando.set(false); }
      });
    }, 300);
  }

  irAResultado(ruta: (string | number)[], idLiga?: number): void {
    if (idLiga != null) {
      this.router.navigate(ruta, { queryParams: { liga: idLiga } });
    } else {
      this.router.navigate(ruta);
    }
    this.busqueda.set('');
    this.resultadosEquipos.set([]);
    this.resultadosJugadores.set([]);
  }

  alternarMenu(menu: 'admin'): void {
    this.menuAbierto.set(this.menuAbierto() === menu ? null : menu);
  }

  // Un desplegable abierto tiene que cerrarse al pinchar fuera; si no, se queda colgado
  // encima del contenido y hay que volver a darle a su propio botón para quitarlo.
  // El clic en el botón que lo abre también llega aquí, por eso se ignora todo lo que
  // caiga dentro de un .topnav-dropdown.
  @HostListener('document:click', ['$event'])
  cerrarMenuSiClicFuera(evento: MouseEvent): void {
    if (this.menuAbierto() === null) return;
    const destino = evento.target as HTMLElement | null;
    if (destino?.closest('.topnav-dropdown')) return;
    this.menuAbierto.set(null);
  }

  @HostListener('document:keydown.escape')
  cerrarMenuConEscape(): void {
    this.menuAbierto.set(null);
  }

  logout() {
    this.authService.logout().subscribe(() => {
      this.router.navigate(['/']);
    });
  }

  planActual(): string {
    const roles = this.authService.currentUser()?.roles ?? [];
    if (roles.includes('ROLE_ADMIN')) return 'Admin';
    if (roles.includes('ROLE_PREMIUM')) return 'Premium';
    return 'Plan FREE';
  }
}