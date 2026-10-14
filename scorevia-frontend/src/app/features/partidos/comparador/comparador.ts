import { Component, OnDestroy, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { EquipoService } from '../../../core/services/equipo.service';
import { PartidoService } from '../../../core/services/partido.service';
import { Equipo, Partido } from '../../../core/models/estadisticas.model';
import { FilaComparativa, porcentajeLadoA, valorComparativa } from '../../../core/utils/comparativa.util';
import { ImagenFallbackDirective } from '../../../core/directives/imagen-fallback.directive';
import { PaginadorComponent } from '../../../shared/paginador/paginador';
import { CargandoComponent } from '../../../shared/cargando/cargando';
import { TranslocoPipe } from '@jsverse/transloco';
import { ErroresService } from '../../../core/services/errores.service';

@Component({
  selector: 'app-comparador',
  standalone: true,
  imports: [CommonModule, RouterLink, ImagenFallbackDirective, PaginadorComponent, CargandoComponent, TranslocoPipe],
  templateUrl: './comparador.html',
  styleUrls: ['./comparador.scss']
})
export class ComparadorComponent implements OnDestroy {
  private errores = inject(ErroresService);
  private equipoService = inject(EquipoService);
  private partidoService = inject(PartidoService);

  equipoA = signal<Equipo | null>(null);
  equipoB = signal<Equipo | null>(null);
  busquedaA = signal('');
  busquedaB = signal('');
  resultadosA = signal<Equipo[]>([]);
  resultadosB = signal<Equipo[]>([]);

  private debounceA?: ReturnType<typeof setTimeout>;
  private debounceB?: ReturnType<typeof setTimeout>;

  // Historial completo (hasta 50 encuentros) para calcular el resumen V/E/D -- la lista
  // visible se pagina en el cliente sobre este mismo conjunto para no volcarlo todo de golpe.
  historial = signal<Partido[]>([]);
  cargando = signal(false);
  error = signal<string | null>(null);
  private readonly TAMANIO_PAGINA = 5;
  pagina = signal(0);

  historialVisible = computed(() => {
    const inicio = this.pagina() * this.TAMANIO_PAGINA;
    return this.historial().slice(inicio, inicio + this.TAMANIO_PAGINA);
  });
  totalPaginas = computed(() => Math.max(1, Math.ceil(this.historial().length / this.TAMANIO_PAGINA)));

  resumen = computed(() => {
    const a = this.equipoA();
    const b = this.equipoB();
    if (!a || !b) return null;
    let victoriasA = 0, victoriasB = 0, empates = 0;
    for (const p of this.historial()) {
      if (p.resultadoLocal == null || p.resultadoVisitante == null) continue;
      const esALocal = p.idEquipoLocal === a.idEquipo;
      const golesA = esALocal ? p.resultadoLocal : p.resultadoVisitante;
      const golesB = esALocal ? p.resultadoVisitante : p.resultadoLocal;
      if (golesA > golesB) victoriasA++;
      else if (golesA < golesB) victoriasB++;
      else empates++;
    }
    return { victoriasA, victoriasB, empates, total: victoriasA + victoriasB + empates };
  });

  /**
   * Comparativa de los enfrentamientos directos, en las mismas barras que la ficha de
   * partido. Todo sale del historial que ya está cargado: no hace falta ningún endpoint
   * nuevo ni una segunda petición.
   */
  estadisticas = computed<FilaComparativa[]>(() => {
    const a = this.equipoA();
    const b = this.equipoB();
    if (!a || !b) return [];

    let golesA = 0, golesB = 0, sinEncajarA = 0, sinEncajarB = 0, jugados = 0;

    for (const p of this.historial()) {
      if (p.resultadoLocal == null || p.resultadoVisitante == null) continue;
      const esALocal = p.idEquipoLocal === a.idEquipo;
      const marcadorA = esALocal ? p.resultadoLocal : p.resultadoVisitante;
      const marcadorB = esALocal ? p.resultadoVisitante : p.resultadoLocal;

      golesA += marcadorA;
      golesB += marcadorB;
      if (marcadorB === 0) sinEncajarA++;
      if (marcadorA === 0) sinEncajarB++;
      jugados++;
    }

    if (jugados === 0) return [];

    return [
      { label: 'Goles a favor', a: golesA, b: golesB },
      { label: 'Goles en contra', a: golesB, b: golesA },
      { label: 'Media de goles por partido', a: golesA / jugados, b: golesB / jugados, decimales: 2 },
      { label: 'Partidos sin encajar', a: sinEncajarA, b: sinEncajarB },
    ];
  });

  // Se exponen a la plantilla para no repetir el cálculo en cada componente.
  porcentajeLadoA = porcentajeLadoA;
  valorComparativa = valorComparativa;

  ngOnDestroy(): void {
    clearTimeout(this.debounceA);
    clearTimeout(this.debounceB);
  }

  buscarA(texto: string): void {
    this.busquedaA.set(texto);
    clearTimeout(this.debounceA);
    if (!texto.trim()) { this.resultadosA.set([]); return; }
    this.debounceA = setTimeout(() => {
      this.equipoService.buscar(texto.trim()).subscribe({ next: r => this.resultadosA.set(r.slice(0, 8)), error: () => this.resultadosA.set([]) });
    }, 300);
  }

  buscarB(texto: string): void {
    this.busquedaB.set(texto);
    clearTimeout(this.debounceB);
    if (!texto.trim()) { this.resultadosB.set([]); return; }
    this.debounceB = setTimeout(() => {
      this.equipoService.buscar(texto.trim()).subscribe({ next: r => this.resultadosB.set(r.slice(0, 8)), error: () => this.resultadosB.set([]) });
    }, 300);
  }

  elegirA(equipo: Equipo): void {
    this.equipoA.set(equipo);
    this.busquedaA.set('');
    this.resultadosA.set([]);
    this.cargarHistorial();
  }

  elegirB(equipo: Equipo): void {
    this.equipoB.set(equipo);
    this.busquedaB.set('');
    this.resultadosB.set([]);
    this.cargarHistorial();
  }

  quitarA(): void { this.equipoA.set(null); this.historial.set([]); }
  quitarB(): void { this.equipoB.set(null); this.historial.set([]); }

  private cargarHistorial(): void {
    const a = this.equipoA();
    const b = this.equipoB();
    if (!a || !b) return;

    this.cargando.set(true);
    this.pagina.set(0);
    this.partidoService.getEnfrentamientos(a.idEquipo, b.idEquipo, 0, 50).subscribe({
      next: pag => {
        this.historial.set(pag.content);
        this.cargando.set(false);
      },
      error: err => {
        this.error.set(this.errores.mensaje(err, 'No se pudo cargar el historial de enfrentamientos.'));
        this.cargando.set(false);
      }
    });
  }
}
