import { DestroyRef, PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

/**
 * Repite una acción cada X mientras la pestaña se esté mirando.
 *
 * **Por qué existe.** El backend sincroniza los partidos en vivo cada 30 segundos, pero el
 * frontend pedía la lista una sola vez al entrar: quien dejaba la página abierta seguía viendo
 * el 0-0 del minuto 3 media hora después. Una página de resultados en directo que no se mueve
 * sola no es una página de resultados en directo.
 *
 * **Se para cuando no se ve.** Con la pestaña en segundo plano no hay nadie mirando, así que
 * seguir pidiendo es gastar batería y llenar el log del gateway de peticiones que nadie lee.
 * Al volver se refresca inmediatamente, sin esperar al siguiente turno: es justo el momento en
 * el que el usuario quiere ver lo que se ha perdido.
 *
 * **No corre en el servidor.** Con SSR, un `setInterval` en el render del servidor deja la
 * petición colgada y retrasa la respuesta. En servidor no se programa nada; el navegador
 * arranca el ciclo al hidratar.
 *
 * Se cancela solo al destruirse el componente que lo llama, así que no hay que acordarse de
 * nada al salir de la pantalla.
 *
 * **Se llama desde el constructor**, no desde `ngOnInit`: usa `inject()`, que solo funciona en
 * contexto de inyección. Da igual que la acción use campos que rellena `ngOnInit`, porque el
 * primer turno no llega hasta `intervaloMs` después.
 *
 * @param intervaloMs cada cuánto se repite
 * @param accion      lo que se hace en cada turno (normalmente, recargar una lista)
 */
export function refrescarEnVivo(intervaloMs: number, accion: () => void): void {
  const plataforma = inject(PLATFORM_ID);
  const destroyRef = inject(DestroyRef);

  if (!isPlatformBrowser(plataforma)) return;

  let temporizador: ReturnType<typeof setInterval> | null = null;

  const arrancar = () => {
    if (temporizador === null) {
      temporizador = setInterval(accion, intervaloMs);
    }
  };

  const parar = () => {
    if (temporizador !== null) {
      clearInterval(temporizador);
      temporizador = null;
    }
  };

  const alCambiarVisibilidad = () => {
    if (document.visibilityState === 'visible') {
      accion();
      arrancar();
    } else {
      parar();
    }
  };

  document.addEventListener('visibilitychange', alCambiarVisibilidad);
  arrancar();

  destroyRef.onDestroy(() => {
    parar();
    document.removeEventListener('visibilitychange', alCambiarVisibilidad);
  });
}
