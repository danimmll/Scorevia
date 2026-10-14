import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

/**
 * El indicador de "esto se está cargando", igual en toda la web.
 *
 * Antes era un `<p>Cargando...</p>` suelto repetido por una docena de pantallas. El problema
 * no es solo que sea feo: un texto quieto no distingue "está tardando" de "se ha colgado", y
 * además la página pega un salto cuando el contenido real aparece y empuja todo hacia abajo.
 *
 * Con `filas` mayor que cero pinta un esqueleto —barras del alto de las filas que van a
 * llegar— y así el hueco ya está reservado: cuando llegan los datos, ocupan el sitio que ya
 * había en vez de desplazar la página.
 */
@Component({
  selector: 'sv-cargando',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (filas > 0) {
      <div class="cg-esqueleto" role="status" [attr.aria-label]="texto">
        @for (fila of relleno; track $index) {
          <div class="cg-barra"></div>
        }
      </div>
    } @else {
      <p class="cg-simple" role="status">
        <span class="cg-ruedita" aria-hidden="true"></span>
        {{ texto }}
      </p>
    }
  `,
  styleUrl: './cargando.scss',
})
export class CargandoComponent {
  /** Cuántas barras de esqueleto. 0 (por defecto) enseña la ruedita con el texto. */
  @Input() set filas(valor: number) {
    this._filas = valor;
    this.relleno = Array.from({ length: Math.max(0, valor) });
  }
  get filas(): number { return this._filas; }
  private _filas = 0;

  @Input() texto = 'Cargando...';

  protected relleno: unknown[] = [];
}
