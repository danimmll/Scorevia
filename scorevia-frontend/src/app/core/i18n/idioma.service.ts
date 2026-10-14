import { Injectable, inject, signal, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { firstValueFrom } from 'rxjs';
import { TranslocoService } from '@jsverse/transloco';

export type Idioma = 'en' | 'es';

export const IDIOMAS: ReadonlyArray<{ codigo: Idioma; nombre: string }> = [
  { codigo: 'en', nombre: 'English' },
  { codigo: 'es', nombre: 'Español' },
];

const CLAVE = 'scorevia-idioma';

/**
 * Que idioma se ve, y como se recuerda.
 *
 * El sitio es english-first: el ingles es el idioma por defecto y el que se usa cuando no
 * hay nada guardado ni el navegador pide otra cosa. El espanol solo aparece si el visitante
 * lo elige o si su navegador lo tiene configurado.
 *
 * <b>El orden importa:</b> primero lo que el visitante eligio a mano (si volvio a entrar,
 * su decision manda sobre la configuracion del sistema), y solo si nunca eligio nada se
 * mira el idioma del navegador.
 *
 * En servidor no se toca `localStorage` ni `navigator`: no existen, y leerlos revienta el
 * render. Ahi se responde siempre el idioma por defecto, que es justo lo que hace que el
 * HTML del servidor y el primer render del navegador coincidan.
 */
@Injectable({ providedIn: 'root' })
export class IdiomaService {
  private transloco = inject(TranslocoService);
  private platformId = inject(PLATFORM_ID);

  readonly actual = signal<Idioma>('en');

  /**
   * Se llama una vez al arrancar la app (ver app.config.ts).
   *
   * <b>Devuelve una promesa y hay que esperarla.</b> `setActiveLang` solo apunta cual es el
   * idioma activo; el catalogo se carga despues, y de forma perezosa. Cuando esto devolvia
   * `void`, Angular no esperaba a nadie y pasaba esto:
   *
   *   1. la app arrancaba sin traducciones;
   *   2. la hidratacion pintaba las CLAVES en crudo ("game_higher_lower_name");
   *   3. eso no cuadraba con el HTML que habia mandado el servidor, que si tenia el texto
   *      bueno -> error de hidratacion -> Angular abortaba y la pagina se quedaba muerta:
   *      se veia bien pero no respondia a un solo clic, ni en la barra de navegacion.
   *
   * Esperando aqui, el catalogo esta cargado antes del primer render y cliente y servidor
   * pintan exactamente lo mismo.
   */
  iniciar(): Promise<unknown> {
    const idioma = this.detectar();
    this.aplicar(idioma);
    return firstValueFrom(this.transloco.load(idioma));
  }

  cambiar(idioma: Idioma): void {
    if (idioma === this.actual()) return;
    this.aplicar(idioma);
    this.guardar(idioma);
  }

  private aplicar(idioma: Idioma): void {
    this.actual.set(idioma);
    this.transloco.setActiveLang(idioma);
    // El atributo lang del <html> no es decoracion: es lo que usa el lector de pantalla
    // para elegir la voz, y el navegador para el corrector y el guionado.
    if (isPlatformBrowser(this.platformId)) {
      document.documentElement.lang = idioma;
    }
  }

  private detectar(): Idioma {
    if (!isPlatformBrowser(this.platformId)) return 'en';

    const guardado = this.leerGuardado();
    if (guardado) return guardado;

    // navigator.language llega como "es", "es-ES", "es-419"... solo interesa lo de delante.
    const delNavegador = navigator.language?.split('-')[0];
    return delNavegador === 'es' ? 'es' : 'en';
  }

  private leerGuardado(): Idioma | null {
    try {
      const valor = localStorage.getItem(CLAVE);
      return valor === 'es' || valor === 'en' ? valor : null;
    } catch {
      // Navegador con el almacenamiento bloqueado: no es un error, es que no hay preferencia.
      return null;
    }
  }

  private guardar(idioma: Idioma): void {
    if (!isPlatformBrowser(this.platformId)) return;
    try {
      localStorage.setItem(CLAVE, idioma);
    } catch {
      // Si no se puede guardar, el idioma vale para esta sesion y ya.
    }
  }
}
