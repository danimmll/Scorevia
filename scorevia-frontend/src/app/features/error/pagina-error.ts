import { Component, OnDestroy, OnInit, RESPONSE_INIT, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { Location } from '@angular/common';

/**
 * Lo que se le enseña a alguien cuando algo se rompe o la dirección no existe.
 *
 * <h2>Por qué hace falta</h2>
 * La ruta comodín era {@code { path: '**', redirectTo: '' }}: cualquier dirección mal
 * escrita —o un enlace viejo que alguien tenga guardado— te dejaba en el marcador sin decir
 * nada. Desde fuera es indistinguible de "he pulsado mal": no sabes si la página no existe,
 * si te has equivocado de letra o si el sitio está roto. Y de cara a Google, una URL
 * inventada respondía 200 con contenido válido, que es la receta para que indexe basura.
 *
 * <h2>Qué enseña</h2>
 * El error de verdad, no una frase genérica:
 *
 * <ul>
 *   <li>En un 404, <b>la dirección concreta</b> que se ha intentado abrir. Es lo único que
 *       permite darse cuenta de que sobra una letra.</li>
 *   <li>En un error propagado, el <b>código</b> y el <b>mensaje</b> que venga, que quien
 *       navegue hasta aquí habrá pasado ya por {@code ErroresService} y estará traducido.</li>
 * </ul>
 *
 * <h2>La cuenta atrás</h2>
 * Vuelve sola al marcador a los 15 segundos, pero <b>solo en el 404</b>. Un error de verdad
 * no se auto-descarta: si algo ha fallado, quien lo está mirando querrá leerlo, copiarlo o
 * enseñárselo a alguien, y una página que se va sola mientras la lees es peor que no tener
 * página. La cuenta se para en cuanto tocas cualquier cosa.
 */
@Component({
  selector: 'app-pagina-error',
  standalone: true,
  imports: [CommonModule, RouterLink, TranslocoPipe],
  templateUrl: './pagina-error.html',
  styleUrls: ['./pagina-error.scss'],
})
export class PaginaErrorComponent implements OnInit, OnDestroy {
  private ruta = inject(ActivatedRoute);
  private router = inject(Router);
  private location = inject(Location);

  /** Segundos antes de volver solo al marcador. Solo corre en el 404. */
  private static readonly SEGUNDOS_REDIRECCION = 15;

  codigo = signal<string>('404');
  mensaje = signal<string | null>(null);
  detalle = signal<string | null>(null);
  /** La dirección que se intentó abrir. Vacía cuando no es un 404 de ruta. */
  rutaIntentada = signal<string>('');

  restantes = signal<number>(PaginaErrorComponent.SEGUNDOS_REDIRECCION);
  cuentaAtrasActiva = signal<boolean>(false);

  private temporizador?: ReturnType<typeof setInterval>;

  /**
   * La respuesta HTTP que va a devolver el servidor cuando esta pagina se pinta con SSR.
   *
   * <p>Sin tocarla, una direccion inventada respondia <b>200 OK</b> con una pagina de error
   * dentro: para un buscador eso es "esta URL existe y tiene contenido", que es como se acaba
   * con direcciones basura indexadas. Es opcional porque en el navegador no existe.</p>
   */
  private respuesta = inject(RESPONSE_INIT, { optional: true });

  /** Un 404 de ruta es el único caso que se auto-redirige. */
  esNoEncontrada = computed(() => this.codigo() === '404');

  ngOnInit(): void {
    // El estado de navegación es como llega un error propagado desde otra pantalla:
    // `router.navigate(['/error'], { state: { codigo, mensaje } })`.
    const estado = this.router.getCurrentNavigation()?.extras?.state
      ?? (typeof history !== 'undefined' ? history.state : null);

    const codigo = estado?.['codigo'] ?? this.ruta.snapshot.queryParamMap.get('codigo');
    const mensaje = estado?.['mensaje'] ?? this.ruta.snapshot.queryParamMap.get('mensaje');

    if (codigo) {
      this.codigo.set(String(codigo));
    }
    if (mensaje) {
      this.mensaje.set(String(mensaje));
    }
    this.detalle.set(estado?.['detalle'] ?? null);

    if (this.esNoEncontrada() && this.respuesta) {
      this.respuesta.status = 404;
    }

    if (this.esNoEncontrada()) {
      // `location.path()` y no `router.url`: cuando el comodín captura la navegación, la url
      // del router ya es la de esta página y se habría perdido justo el dato que interesa.
      this.rutaIntentada.set(this.location.path() || '/');
      this.arrancarCuentaAtras();
    }
  }

  ngOnDestroy(): void {
    this.pararCuentaAtras();
  }

  /** Cualquier interacción cancela la vuelta automática: mandas tú, no el temporizador. */
  cancelarRedireccion(): void {
    this.pararCuentaAtras();
  }

  volver(): void {
    this.pararCuentaAtras();
    this.location.back();
  }

  private arrancarCuentaAtras(): void {
    // El SSR pinta esta página en el servidor: allí no hay a quién redirigir y el intervalo
    // dejaría el renderizado esperando para siempre.
    if (typeof window === 'undefined') {
      return;
    }
    this.cuentaAtrasActiva.set(true);
    this.temporizador = setInterval(() => {
      const quedan = this.restantes() - 1;
      this.restantes.set(quedan);
      if (quedan <= 0) {
        this.pararCuentaAtras();
        void this.router.navigate(['/partidos']);
      }
    }, 1000);
  }

  private pararCuentaAtras(): void {
    if (this.temporizador) {
      clearInterval(this.temporizador);
      this.temporizador = undefined;
    }
    this.cuentaAtrasActiva.set(false);
  }
}
