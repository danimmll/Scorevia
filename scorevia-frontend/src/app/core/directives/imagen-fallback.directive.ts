import { Directive, ElementRef, Input, OnChanges, Renderer2, inject } from '@angular/core';

/**
 * Sustituye una imagen que no carga por un dibujo propio en vez de dejar el icono de
 * "papel roto" del navegador.
 *
 * Hacía falta porque la base de datos SIEMPRE tiene URL de imagen: API-Football compone
 * la ruta a partir del id (.../coachs/1192.png) exista o no el archivo, así que
 * `foto IS NULL` es 0 de 27.123 entrenadores y 0 de 13.241 estadios, y aun así muchas
 * devuelven 404. Comprobar el campo en la plantilla no sirve de nada: el único momento en
 * que se sabe que no hay foto es cuando el navegador falla al descargarla.
 *
 * Los sustitutos van como SVG en un data URI, no como archivos en assets/: así no hay una
 * segunda petición que a su vez pueda dar 404, funcionan con el SSR y no dependen de que
 * nadie se acuerde de copiar una carpeta al desplegar.
 *
 * Se usa `@Input`/`ngOnChanges` y no `input()`/`effect()` a propósito: los efectos no
 * tienen garantizado ejecutarse mientras el servidor pinta la página, y la imagen se
 * habría quedado sin `src` en el HTML del SSR.
 */
export type TipoImagen = 'jugador' | 'entrenador' | 'equipo' | 'estadio' | 'liga';

/** Gris neutro, legible tanto sobre fondo claro como oscuro. */
const TRAZO = '#94a3b8';
const FONDO = '#e2e8f0';

function svg(contenido: string, redondo = false): string {
  const marco = redondo
    ? `<circle cx="32" cy="32" r="32" fill="${FONDO}"/>`
    : `<rect width="64" height="64" rx="8" fill="${FONDO}"/>`;
  const doc =
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" width="64" height="64">` +
    `${marco}<g fill="none" stroke="${TRAZO}" stroke-width="3" stroke-linecap="round" ` +
    `stroke-linejoin="round">${contenido}</g></svg>`;
  // encodeURIComponent y no btoa: btoa revienta con caracteres no latinos y además no
  // existe en el servidor cuando la página se pinta con SSR.
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(doc)}`;
}

const SILUETA = '<circle cx="32" cy="25" r="10"/><path d="M14 52c0-10 8-16 18-16s18 6 18 16"/>';

const PLACEHOLDERS: Record<TipoImagen, string> = {
  jugador: svg(SILUETA, true),
  entrenador: svg(SILUETA, true),
  equipo: svg('<path d="M32 13l16 6v14c0 10-7 17-16 20-9-3-16-10-16-20V19z"/>'),
  estadio: svg('<ellipse cx="32" cy="34" rx="20" ry="12"/><ellipse cx="32" cy="34" rx="10" ry="6"/><path d="M12 34v6c0 5 9 9 20 9s20-4 20-9v-6"/>'),
  liga: svg('<path d="M22 16h20v10a10 10 0 0 1-20 0z"/><path d="M22 19h-5v4a5 5 0 0 0 5 5M42 19h5v4a5 5 0 0 1-5 5M28 36h8M26 48h12M32 36v12"/>'),
};

@Directive({
  selector: 'img[svImagen]',
  standalone: true,
})
export class ImagenFallbackDirective implements OnChanges {
  /** Qué dibujar cuando no hay imagen. Por defecto, un escudo. */
  @Input() svImagen: TipoImagen | '' = 'equipo';

  /**
   * La URL que se quiere mostrar; puede llegar vacía, nula o rota.
   *
   * Al declararla aquí, `[src]="..."` en la plantilla pasa por la directiva en vez de
   * ir directo al DOM, que es lo que permite pintar el sustituto sin esperar al fallo.
   */
  @Input() src: string | null | undefined;

  private el = inject(ElementRef<HTMLImageElement>).nativeElement as HTMLImageElement;
  private renderer = inject(Renderer2);

  /** Corta el bucle si el propio sustituto fallara al pintarse. */
  private yaSustituida = false;

  constructor() {
    this.renderer.listen(this.el, 'error', () => this.sustituir());
  }

  /**
   * Carga diferida y decodificacion asincrona, para las 79 imagenes que pasan por aqui.
   *
   * <p>Va en la directiva y no en las plantillas por lo mismo que el resto: de las 86
   * imagenes del sitio, 79 llevan `svImagen`, asi que ponerlo aqui las cubre todas de una
   * vez y no depende de que nadie se acuerde en la siguiente. Solo 5 tenian `loading="lazy"`
   * a mano.</p>
   *
   * <p>Importa de verdad en las paginas de listado: la de jugadores pinta 20 fichas con su
   * foto, la de competiciones 24 escudos. Sin diferir, el navegador se pone a descargarlas
   * todas a la vez —incluidas las que estan por debajo del pliegue— y compiten con los datos
   * que si se estan viendo.</p>
   *
   * <p>Se respeta lo que ya venga puesto en la plantilla: una foto de cabecera puede querer
   * `eager` a proposito, y eso no lo tiene que decidir la directiva.</p>
   */
  private aplicarCargaDiferida(): void {
    const img = this.el;
    if (!img.hasAttribute('loading')) {
      this.renderer.setAttribute(img, 'loading', 'lazy');
    }
    if (!img.hasAttribute('decoding')) {
      this.renderer.setAttribute(img, 'decoding', 'async');
    }
  }

  ngOnChanges(): void {
    this.yaSustituida = false;
    this.marcarTipo();
    this.aplicarCargaDiferida();
    const url = this.src?.trim();

    // Sin URL no hace falta esperar a que falle la descarga. Además un src="" o
    // src="undefined" dispara una petición a la propia página, que no es gratis.
    if (!url || url === 'undefined' || url === 'null') {
      this.sustituir();
      return;
    }
    this.renderer.setAttribute(this.el, 'src', url);
  }

  /**
   * Deja en la imagen una clase con su tipo (`sv-img--liga`, `sv-img--jugador`...).
   *
   * Sirve para poder peinar desde los estilos globales todas las imágenes de una clase a la
   * vez, sin ir componente por componente. El caso que lo motivó: hay escudos de competición
   * (el de la Champions es el ejemplo claro) dibujados en azul muy oscuro sobre fondo
   * transparente. En tema claro se ven perfectos y en oscuro desaparecen contra el fondo.
   */
  private marcarTipo(): void {
    const tipo: TipoImagen = this.svImagen || 'equipo';
    if (this.tipoMarcado === tipo) return;
    if (this.tipoMarcado) this.renderer.removeClass(this.el, `sv-img--${this.tipoMarcado}`);
    this.renderer.addClass(this.el, `sv-img--${tipo}`);
    this.tipoMarcado = tipo;
  }

  private tipoMarcado?: TipoImagen;

  private sustituir(): void {
    if (this.yaSustituida) return;
    this.yaSustituida = true;
    const tipo: TipoImagen = this.svImagen || 'equipo';
    this.renderer.setAttribute(this.el, 'src', PLACEHOLDERS[tipo]);
    this.renderer.addClass(this.el, 'img-sustituida');
  }
}
