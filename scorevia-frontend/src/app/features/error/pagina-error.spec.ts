import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { Location } from '@angular/common';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { PaginaErrorComponent } from './pagina-error';

/**
 * Lo que tiene que hacer la página de error.
 *
 * <p>Antes de que existiera, la ruta comodín era `redirectTo: ''`: cualquier dirección mal
 * escrita te dejaba en el marcador sin decir nada, y no había forma de distinguir "esta
 * página no existe" de "he pulsado mal". Estos tests fijan las dos cosas que hacen que la
 * página sirva para algo: que enseñe la dirección concreta que se pidió, y que la vuelta
 * automática NO se lleve por delante un error de verdad mientras lo estás leyendo.</p>
 */
describe('PaginaErrorComponent', () => {
  let navegaciones: unknown[][];
  let rutaActual: string;

  function crear(estado: Record<string, unknown> | null = null, queryParams: Record<string, string> = {}) {
    navegaciones = [];
    TestBed.configureTestingModule({
      imports: [
        PaginaErrorComponent,
        TranslocoTestingModule.forRoot({
          langs: { es: { error_page_not_found_body: 'No existe.' } },
          translocoConfig: { availableLangs: ['es'], defaultLang: 'es' },
        }),
      ],
      providers: [
        {
          provide: Router,
          useValue: {
            getCurrentNavigation: () => (estado ? { extras: { state: estado } } : null),
            navigate: (...args: unknown[]) => { navegaciones.push(args); return Promise.resolve(true); },
          },
        },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } },
        },
        { provide: Location, useValue: { path: () => rutaActual, back: () => undefined } },
      ],
    });
    return TestBed.createComponent(PaginaErrorComponent);
  }

  beforeEach(() => {
    rutaActual = '/estadisticas/ligass';
  });

  it('en un 404 enseña la dirección concreta que se pidió', () => {
    const fixture = crear();
    fixture.detectChanges();

    const comp = fixture.componentInstance;
    expect(comp.codigo()).toBe('404');
    // Esto es lo único que permite darse cuenta de que sobra una "s".
    expect(comp.rutaIntentada()).toBe('/estadisticas/ligass');
  });

  it('un error propagado enseña su código y su mensaje, ya traducido', () => {
    const fixture = crear({ codigo: '500', mensaje: 'El servidor no responde.' });
    fixture.detectChanges();

    const comp = fixture.componentInstance;
    expect(comp.codigo()).toBe('500');
    expect(comp.mensaje()).toBe('El servidor no responde.');
    expect(comp.esNoEncontrada()).toBe(false);
  });

  it('un error de verdad NO se auto-redirige: te da tiempo a leerlo', () => {
    const fixture = crear({ codigo: '500', mensaje: 'Ha petado.' });
    fixture.detectChanges();

    // Sin esto, la página se iría sola mientras alguien intenta copiar el mensaje o
    // enseñárselo a otro, que es peor que no tener página de error.
    expect(fixture.componentInstance.cuentaAtrasActiva()).toBe(false);
  });

  it('el 404 sí cuenta atrás, y cualquier interacción la cancela', () => {
    const fixture = crear();
    fixture.detectChanges();

    const comp = fixture.componentInstance;
    expect(comp.cuentaAtrasActiva()).toBe(true);

    comp.cancelarRedireccion();
    expect(comp.cuentaAtrasActiva()).toBe(false);
    expect(navegaciones.length).toBe(0);
  });

  it('también acepta el error por query params, para poder enlazarlo', () => {
    const fixture = crear(null, { codigo: '403', mensaje: 'No tienes permiso.' });
    fixture.detectChanges();

    expect(fixture.componentInstance.codigo()).toBe('403');
    expect(fixture.componentInstance.mensaje()).toBe('No tienes permiso.');
  });
});
