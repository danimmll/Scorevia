import { Component, ElementRef, HostListener, computed, effect, inject, input, output, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

/**
 * Selector de país con buscador.
 *
 * <p>Antes esto era un `<select>` con la lista entera de países del catálogo (más de cien en
 * cuanto hay competiciones internacionales): para llegar a Países Bajos había que abrir el
 * desplegable y bajar a mano. Aquí se escribe y se filtra, y lo más buscado está arriba del
 * todo sin escribir nada.</p>
 *
 * <p>No se queda con una lista fija de países: recibe la que haya en los datos, y de esa
 * marca como destacadas las que también estén en {@link PAISES_DESTACADOS}. Así una liga
 * nueva aparece sola y ninguna desaparece por no estar en una lista escrita a mano.</p>
 */
@Component({
  imports: [TranslocoPipe],
  selector: 'app-selector-pais',
  standalone: true,
  templateUrl: './selector-pais.html',
  styleUrls: ['./selector-pais.scss']
})
export class SelectorPaisComponent {
  /** Todos los países que hay en los datos, tal cual llegan. */
  paises = input.required<string[]>();
  /** El seleccionado ahora mismo. */
  seleccionado = input<string | null>(null);
  /** Etiqueta de la opción "sin filtrar"; si no se pasa, no se ofrece esa opción. */
  etiquetaTodos = input<string | null>(null);

  cambio = output<string>();

  private host = inject<ElementRef<HTMLElement>>(ElementRef);

  abierto = signal(false);
  filtro = signal('');
  /** Índice resaltado con las flechas del teclado. */
  activo = signal(0);

  /**
   * Los que casi siempre se buscan. Se comparan tal y como los nombra API-Football, que es
   * de donde salen los datos. "World" agrupa las competiciones internacionales.
   */
  private static readonly PAISES_DESTACADOS = [
    'Spain', 'England', 'Italy', 'Germany', 'France', 'Portugal', 'Netherlands',
    'Brazil', 'Argentina', 'World'
  ];

  destacados = computed(() => {
    if (this.filtro().trim()) return [];
    const disponibles = new Set(this.paises());
    return SelectorPaisComponent.PAISES_DESTACADOS.filter(p => disponibles.has(p));
  });

  resto = computed(() => {
    const texto = this.filtro().trim().toLowerCase();
    if (texto) {
      return this.paises().filter(p => p.toLowerCase().includes(texto));
    }
    const destacados = new Set(this.destacados());
    return this.paises().filter(p => !destacados.has(p));
  });

  /** La lista completa en el orden en que se pinta, para que las flechas la recorran igual. */
  private navegables = computed(() => [...this.destacados(), ...this.resto()]);

  hayResultados = computed(() => this.navegables().length > 0);

  texto = computed(() => this.seleccionado() || this.etiquetaTodos() || 'País');

  constructor() {
    // Al cambiar el filtro, el resaltado vuelve arriba: si no, se quedaría apuntando a una
    // posición que ya no existe y Enter elegiría un país al azar.
    effect(() => {
      this.filtro();
      this.activo.set(0);
    });
  }

  alternar(): void {
    const seAbre = !this.abierto();
    this.abierto.set(seAbre);
    if (seAbre) {
      this.filtro.set('');
      // El input se monta con el panel, así que hay que esperar a que exista.
      queueMicrotask(() => this.host.nativeElement.querySelector<HTMLInputElement>('.selector-pais-buscar')?.focus());
    }
  }

  elegir(pais: string): void {
    this.cambio.emit(pais);
    this.abierto.set(false);
  }

  elegirTodos(): void {
    this.cambio.emit('');
    this.abierto.set(false);
  }

  alTeclear(evento: KeyboardEvent): void {
    const opciones = this.navegables();
    switch (evento.key) {
      case 'ArrowDown':
        evento.preventDefault();
        this.activo.update(i => Math.min(i + 1, opciones.length - 1));
        break;
      case 'ArrowUp':
        evento.preventDefault();
        this.activo.update(i => Math.max(i - 1, 0));
        break;
      case 'Enter':
        evento.preventDefault();
        if (opciones[this.activo()]) this.elegir(opciones[this.activo()]);
        break;
      case 'Escape':
        this.abierto.set(false);
        break;
      default:
        break;
    }
  }

  /** Posición dentro de la lista navegable, para saber cuál va resaltado. */
  indiceDe(pais: string): number {
    return this.navegables().indexOf(pais);
  }

  @HostListener('document:click', ['$event'])
  cerrarSiClicFuera(evento: MouseEvent): void {
    if (!this.abierto()) return;
    if (this.host.nativeElement.contains(evento.target as Node)) return;
    this.abierto.set(false);
  }
}
