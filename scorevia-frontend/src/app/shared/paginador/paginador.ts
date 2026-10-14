import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, computed, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

/**
 * La paginación de toda la web, en un solo sitio.
 *
 * Estaba copiada **dieciocho veces** repartidas por doce pantallas, siempre igual: dos
 * botones de texto con «Anterior y Siguiente» y un "Página 3 de 12" en medio. Además de
 * feo, tenía el problema de todo lo copiado: para llegar a la página 9 había que pulsar
 * ocho veces, y nadie iba a arreglar eso en doce ficheros.
 *
 * Aquí los números son pinchables, con una ventana alrededor de la página actual y elipsis
 * cuando hay muchas. El contrato es el mismo que ya usaban todas: página en base 0 y un
 * evento con la página nueva.
 */
@Component({
  imports: [TranslocoPipe],
  selector: 'sv-paginador',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './paginador.html',
  styleUrl: './paginador.scss',
})
export class PaginadorComponent {
  /** Página actual, **en base 0** (como la manda el backend de Spring). */
  @Input({ required: true }) set pagina(valor: number) { this.paginaActual.set(valor); }

  @Input({ required: true }) set totalPaginas(valor: number) { this.total.set(valor); }

  /** Qué se está paginando, para el lector de pantalla: "jugadores", "partidos"... */
  @Input() etiqueta = 'resultados';

  /**
   * Bloquea la paginación mientras se está cargando algo.
   *
   * Hace falta donde cada página es una llamada al servidor: sin esto se pueden encadenar
   * varios clics y llegan las respuestas desordenadas, dejando en pantalla una página que
   * no es la que marca el paginador.
   */
  @Input() deshabilitado = false;

  @Output() cambio = new EventEmitter<number>();

  protected readonly paginaActual = signal(0);
  protected readonly total = signal(0);

  /**
   * Los números que se pintan: siempre la primera, la última y una ventana de dos a cada
   * lado de la actual. Los huecos se marcan con null y salen como elipsis.
   *
   * Con 200 páginas pintarlas todas sería una tira ilegible que además se lleva por delante
   * el ancho del contenedor; y con 3 páginas no hay que esconder nada.
   */
  protected readonly numeros = computed<(number | null)[]>(() => {
    const total = this.total();
    const actual = this.paginaActual();
    if (total <= 1) return [];

    const VECINAS = 2;
    const visibles = new Set<number>([0, total - 1]);
    for (let i = actual - VECINAS; i <= actual + VECINAS; i++) {
      if (i >= 0 && i < total) visibles.add(i);
    }

    const ordenadas = [...visibles].sort((a, b) => a - b);
    const resultado: (number | null)[] = [];
    let anterior: number | null = null;
    for (const n of ordenadas) {
      if (anterior !== null) {
        const hueco = n - anterior - 1;
        // Esconder UNA sola página detrás de unos puntos suspensivos es peor que enseñarla:
        // ocupa lo mismo en pantalla y encima deja de poder pincharse.
        if (hueco === 1) resultado.push(anterior + 1);
        else if (hueco > 1) resultado.push(null);
      }
      resultado.push(n);
      anterior = n;
    }
    return resultado;
  });

  protected readonly hayAnterior = computed(() => this.paginaActual() > 0);
  protected readonly haySiguiente = computed(() => this.paginaActual() + 1 < this.total());

  protected ir(pagina: number): void {
    if (this.deshabilitado) return;
    if (pagina < 0 || pagina >= this.total() || pagina === this.paginaActual()) return;
    this.cambio.emit(pagina);
  }
}
