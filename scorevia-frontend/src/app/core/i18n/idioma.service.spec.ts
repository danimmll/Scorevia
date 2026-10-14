import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';
import { IdiomaService } from './idioma.service';
import { importarTranslocoDePrueba } from './testing';

/**
 * El fallo que estas pruebas vigilan tumbo la pagina entera el 2026-09-08.
 *
 * `iniciar()` devolvia `void`, asi que `provideAppInitializer` no esperaba a nadie:
 *
 *   1. la app arrancaba con el catalogo sin cargar;
 *   2. la hidratacion pintaba las CLAVES en crudo ("game_higher_lower_name");
 *   3. eso no cuadraba con el HTML del servidor, que si traia el texto bueno, y el error de
 *      hidratacion dejaba la pagina muerta: se veia, pero no respondia a un solo clic.
 *
 * Lo peor es como se manifestaba: nada en rojo, la pagina "bien" y la barra de navegacion
 * sin funcionar. Por eso se comprueba el contrato explicitamente.
 */
describe('IdiomaService', () => {
  let servicio: IdiomaService;
  let transloco: TranslocoService;

  beforeEach(() => {
    // El idioma elegido se guarda en localStorage, y jsdom lo comparte entre specs: sin
    // limpiarlo, un test que hace cambiar('es') en OTRO fichero deja este arrancando en
    // espanol. Paso una vez de verdad: 'arranca en ingles' fallaba con 'es'.
    localStorage.clear();
    TestBed.configureTestingModule({ imports: [importarTranslocoDePrueba()] });
    servicio = TestBed.inject(IdiomaService);
    transloco = TestBed.inject(TranslocoService);
  });

  it('iniciar() devuelve algo que se puede esperar', () => {
    const resultado = servicio.iniciar();
    // Si esto deja de ser una promesa, provideAppInitializer no espera y vuelve el fallo.
    expect(typeof (resultado as Promise<unknown>)?.then).toBe('function');
  });

  it('al resolverse iniciar(), las traducciones YA estan disponibles', async () => {
    await servicio.iniciar();
    // translate() sincrono solo devuelve el texto si el catalogo esta cargado; si no,
    // devolveria la clave, que es justo lo que se veia en pantalla.
    const texto = transloco.translate('matches');
    expect(texto).not.toBe('matches');
    expect(texto).toBe('Matches');
  });

  it('arranca en ingles: el sitio es english-first', async () => {
    await servicio.iniciar();
    expect(servicio.actual()).toBe('en');
  });

  it('cambiar() conmuta el idioma activo', async () => {
    await servicio.iniciar();
    servicio.cambiar('es');
    expect(servicio.actual()).toBe('es');
    expect(transloco.getActiveLang()).toBe('es');
  });

  it('cambiar() al idioma que ya esta puesto no hace nada', async () => {
    await servicio.iniciar();
    servicio.cambiar('en');
    expect(servicio.actual()).toBe('en');
  });
});
