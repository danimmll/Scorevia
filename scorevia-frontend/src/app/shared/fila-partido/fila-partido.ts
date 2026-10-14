import { ChangeDetectionStrategy, Component, DestroyRef, Input, ViewEncapsulation, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Partido } from '../../core/models/estadisticas.model';
import { ImagenFallbackDirective } from '../../core/directives/imagen-fallback.directive';
import { etiquetaMinuto, EtiquetaMinuto } from '../../core/utils/minuto-partido.util';
import { TranslocoPipe } from '@jsverse/transloco';

/**
 * Una fila de partido, igual en todas las pantallas donde se lista un partido.
 *
 * Nace de dos problemas que iban juntos:
 *
 * 1. **El marcador bailaba.** La fila era una única celda en línea: escudo, nombre del local,
 *    resultado, nombre del visitante, escudo. Con nombres de largo distinto —y los hay de
 *    "Ath" a "Borussia Mönchengladbach"— el marcador caía en una posición distinta en cada
 *    fila, y una tabla de veinte partidos se leía como una escalera. Aquí el encuentro es una
 *    rejilla de tres columnas (`1fr auto 1fr`): el marcador está SIEMPRE en el mismo sitio,
 *    el local crece hacia la izquierda y el visitante hacia la derecha.
 *
 * 2. **Estaba copiado y pegado.** El mismo bloque aparecía tres veces solo en el listado de
 *    partidos (en vivo, por liga, y los de hoy), y otra vez en la ficha de equipo. Cualquier
 *    arreglo había que hacerlo cuatro veces, y por eso ninguno estaba igual que los demás.
 *
 * Va como selector de atributo sobre un `<tr>` para poder seguir usándose dentro de las
 * tablas que ya existen, sin reescribirlas.
 */
@Component({
  selector: 'tr[svFilaPartido]',
  standalone: true,
  imports: [DatePipe, ImagenFallbackDirective, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  // Los <td> los pinta este componente, así que las reglas de tabla del padre no les llegan
  // (encapsulación de estilos). Por eso la fila se peina entera aquí, incluido el hover, que
  // cuelga de :host — que es el propio <tr>.
  encapsulation: ViewEncapsulation.Emulated,
  templateUrl: './fila-partido.html',
  styleUrl: './fila-partido.scss',
})
export class FilaPartidoComponent {
  /** Cuánto dura el destello de un gol, en ms. */
  private static readonly DESTELLO_MS = 4000;

  private readonly destroyRef = inject(DestroyRef);
  private avisoDestello: ReturnType<typeof setTimeout> | null = null;

  /**
   * Marca que en este partido acaba de cambiar el marcador.
   *
   * La lista se recarga sola cada 30 segundos (ver refrescarEnVivo), pero sin esto un gol
   * cambiaba un dígito en silencio: si no estabas mirando esa fila exacta en ese instante, no
   * te enterabas. El destello es lo que convierte el refresco en algo que se nota.
   */
  protected readonly hayGol = signal(false);

  private marcadorAnterior: string | null = null;

  @Input({ required: true }) set partido(valor: Partido) {
    this.datos = valor;

    const marcador = `${valor.resultadoLocal ?? ''}-${valor.resultadoVisitante ?? ''}`;
    // Solo destella un cambio de verdad. La primera vez que llega el partido no: si no, al
    // entrar en la página parpadearían todas las filas a la vez.
    const cambio = this.marcadorAnterior !== null && this.marcadorAnterior !== marcador;
    this.marcadorAnterior = marcador;

    if (cambio && valor.estado === 'EN_JUEGO') {
      this.destellar();
    }
  }

  get partido(): Partido {
    return this.datos;
  }

  private datos!: Partido;

  private destellar(): void {
    this.hayGol.set(true);
    if (this.avisoDestello !== null) {
      clearTimeout(this.avisoDestello);
    }
    this.avisoDestello = setTimeout(
      () => this.hayGol.set(false), FilaPartidoComponent.DESTELLO_MS);
    this.destroyRef.onDestroy(() => {
      if (this.avisoDestello !== null) clearTimeout(this.avisoDestello);
    });
  }

  /** La columna de competición. Sobra cuando la tabla ya va agrupada por competición. */
  @Input() mostrarLiga = false;

  /**
   * `hora` enseña solo HH:mm (listas de un único día), `fecha` añade el día (listas que
   * cruzan varias jornadas, como los partidos de un equipo).
   */
  @Input() formatoFecha: 'hora' | 'fecha' = 'hora';

  protected get enJuego(): boolean {
    return this.partido.estado === 'EN_JUEGO';
  }

  protected get minuto(): EtiquetaMinuto | null {
    return etiquetaMinuto(this.partido);
  }

  protected get hayMarcador(): boolean {
    return this.partido.resultadoLocal != null && this.partido.resultadoVisitante != null;
  }

  /**
   * Qué se enseña en la columna de la izquierda.
   *
   * Antes había una columna aparte con el estado en mayúsculas ("PROGRAMADO", "FINALIZADO"),
   * que ni se lee bien ni dice nada que no se deduzca del propio marcador. Se cambia por lo
   * único que aporta en cada caso: la hora si no ha empezado, el minuto si se está jugando,
   * y "Final" si ya terminó.
   */
  protected get etiquetaEstado(): string | null {
    switch (this.partido.estado) {
      case 'FINALIZADO': return 'Final';
      case 'APLAZADO': return 'Aplazado';
      case 'EN_JUEGO': return null;   // lo cubre el minuto
      default: return null;           // lo cubre la hora
    }
  }

  /** Resalta al que va ganando, para no tener que comparar los dos números. */
  protected get ganaLocal(): boolean {
    return this.hayMarcador && this.partido.resultadoLocal! > this.partido.resultadoVisitante!;
  }

  protected get ganaVisitante(): boolean {
    return this.hayMarcador && this.partido.resultadoVisitante! > this.partido.resultadoLocal!;
  }
}
