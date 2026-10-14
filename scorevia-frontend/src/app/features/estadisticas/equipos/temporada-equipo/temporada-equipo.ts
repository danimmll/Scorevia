import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EquipoService } from '../../../../core/services/equipo.service';
import { TemporadaEquipo, JugadorTemporada } from '../../../../core/models/estadisticas.model';
import { ImagenFallbackDirective } from '../../../../core/directives/imagen-fallback.directive';
import { CargandoComponent } from '../../../../shared/cargando/cargando';
import { PaginadorComponent } from '../../../../shared/paginador/paginador';
import { paginarEnCliente } from '../../../../core/utils/paginacion-cliente';
import { TranslocoPipe } from '@jsverse/transloco';
import { ErroresService } from '../../../../core/services/errores.service';

/** Cómo se ordena la tabla de la plantilla. */
type ColumnaOrden = 'minutos' | 'goles' | 'asistencias' | 'partidos' | 'valoracionMedia';

/**
 * La temporada de un equipo, entera: quién la jugó, quién la dirigió y cómo acabó.
 *
 * Es la pantalla que faltaba para poder mirar hacia atrás. La ficha de equipo enseña la
 * plantilla de HOY y sus últimos partidos; para saber quién ganó la Liga de 2015 con ese club,
 * cuántos goles metió cada uno o quién los entrenaba, no había ningún sitio donde ir.
 *
 * Todo sale de datos ya ingeridos —partidos, clasificaciones e historial de entrenadores—,
 * así que **no cuesta ninguna petición** a API-Football.
 */
@Component({
  selector: 'app-temporada-equipo',
  standalone: true,
  imports: [RouterLink, ImagenFallbackDirective, CargandoComponent, PaginadorComponent, TranslocoPipe],
  templateUrl: './temporada-equipo.html',
  styleUrl: './temporada-equipo.scss',
})
export class TemporadaEquipoComponent implements OnInit {
  private errores = inject(ErroresService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private equipoService = inject(EquipoService);

  detalle = signal<TemporadaEquipo | null>(null);
  temporadasDisponibles = signal<number[]>([]);
  cargando = signal(true);
  error = signal<string | null>(null);

  private idEquipo = 0;
  temporada = signal(0);

  /** Por qué columna se ordena la plantilla. Por minutos de salida: es quien más jugó. */
  orden = signal<ColumnaOrden>('minutos');

  plantillaOrdenada = computed(() => {
    const columna = this.orden();
    return [...(this.detalle()?.plantilla ?? [])].sort((a, b) => valor(b, columna) - valor(a, columna));
  });

  /** Una plantilla de temporada pasa de 40 jugadores (el Real Madrid 2024 tiene 44). */
  plantillaPaginada = paginarEnCliente(this.plantillaOrdenada, 12);

  ngOnInit(): void {
    this.idEquipo = Number(this.route.snapshot.paramMap.get('id'));
    this.temporada.set(Number(this.route.snapshot.paramMap.get('temporada')));

    this.equipoService.getTemporadas(this.idEquipo).subscribe({
      next: t => this.temporadasDisponibles.set(t),
      // El selector de temporada es una comodidad, no el contenido: si falla, la pantalla sigue.
      error: () => this.temporadasDisponibles.set([]),
    });

    this.cargar();
  }

  private cargar(): void {
    this.cargando.set(true);
    this.equipoService.getTemporada(this.idEquipo, this.temporada()).subscribe({
      next: d => { this.detalle.set(d); this.cargando.set(false); },
      error: err => { this.error.set(this.errores.mensaje(err)); this.cargando.set(false); },
    });
  }

  cambiarTemporada(temporada: number): void {
    if (temporada === this.temporada()) return;
    this.temporada.set(temporada);
    // Se cambia también la URL para que la temporada que se está viendo se pueda compartir y
    // el botón de atrás del navegador haga lo que se espera.
    this.router.navigate(['/estadisticas/equipos', this.idEquipo, 'temporada', temporada]);
    this.cargar();
  }

  ordenarPor(columna: ColumnaOrden): void {
    this.orden.set(columna);
  }
}

/** Los nulos cuentan como cero al ordenar: si no, se colocarían por delante de un 0 real. */
function valor(jugador: JugadorTemporada, columna: ColumnaOrden): number {
  return jugador[columna] ?? 0;
}
