import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { authInterceptor } from './auth-interceptor';

/**
 * Quien lleva la cookie y quien no.
 *
 * <p>Este interceptor ponia `withCredentials: true` en TODAS las peticiones. Solo lo
 * necesita el flujo de sesion (`/api/v1/auth/*`), donde vive la cookie de refresco; el resto
 * de la API se autentica con el `Authorization: Bearer`.</p>
 *
 * <p>No era cosmetico: Angular <b>descarta del estado transferido</b> cualquier peticion con
 * credenciales salientes ({@code hasOutgoingCredentials}). Con la bandera puesta en todo, el
 * render de servidor pedia los datos, los pintaba, y el navegador VOLVIA A PEDIRLOS TODOS al
 * hidratar. Medido antes: el bloque `ng-state` de /partidos pesaba 2,6 kB y no traia ni una
 * respuesta. Despues: 536 kB y 5 respuestas, o sea 5 peticiones que el navegador se ahorra.</p>
 */
describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  /** Lanza la peticion y devuelve lo que le llega al backend. */
  function peticionA(url: string) {
    http.get(url).subscribe({ next: () => {}, error: () => {} });
    const req = httpMock.expectOne(url);
    req.flush({});
    return req.request;
  }

  it('los datos publicos NO llevan credenciales, para que se puedan cachear', () => {
    for (const url of ['/api/v1/partidos', '/api/v1/ligas', '/api/v1/equipos/liga/140',
                       '/api/v1/jugadores/1']) {
      expect(peticionA(url).withCredentials, `${url} no deberia llevar cookie`).toBe(false);
    }
  });

  it('el flujo de sesion SI las lleva: ahi vive la cookie de refresco', () => {
    for (const url of ['/api/v1/auth/login', '/api/v1/auth/refresh',
                       '/api/v1/auth/register', '/api/v1/auth/google']) {
      expect(peticionA(url).withCredentials, `${url} necesita la cookie`).toBe(true);
    }
  });

  it('sin sesion no se manda cabecera Authorization', () => {
    expect(peticionA('/api/v1/partidos').headers.has('Authorization')).toBe(false);
  });

  afterEach(() => httpMock.verify());
});
