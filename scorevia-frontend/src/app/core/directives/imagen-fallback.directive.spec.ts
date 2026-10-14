import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { ImagenFallbackDirective } from './imagen-fallback.directive';
import { importarTranslocoDePrueba } from '../i18n/testing';

const URL_BUENA = 'https://media.api-sports.io/football/coachs/1192.png';

@Component({
  standalone: true,
  imports: [ImagenFallbackDirective],
  template: `<img svImagen="entrenador" [src]="url" alt="prueba">`,
})
class AnfitrionComponent {
  url: string | null | undefined = URL_BUENA;
}

/** Monta el componente ya con el valor puesto, para no cambiarlo entre ciclos de detección. */
function montar(url: string | null | undefined): HTMLImageElement {
  const fixture = TestBed.createComponent(AnfitrionComponent);
  fixture.componentInstance.url = url;
  fixture.detectChanges();
  return fixture.nativeElement.querySelector('img') as HTMLImageElement;
}

const esRelleno = (img: HTMLImageElement) => /^data:image\/svg\+xml/.test(img.getAttribute('src') ?? '');

describe('ImagenFallbackDirective', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [AnfitrionComponent, importarTranslocoDePrueba()] }).compileComponents();
  });

  it('deja pasar una URL normal sin tocarla', () => {
    const img = montar(URL_BUENA);
    expect(img.getAttribute('src')).toBe(URL_BUENA);
    expect(img.classList.contains('img-sustituida')).toBe(false);
  });

  it('sin URL pinta el dibujo de relleno en vez de dejar el icono de papel roto', () => {
    const img = montar(null);
    expect(esRelleno(img)).toBe(true);
    expect(img.classList.contains('img-sustituida')).toBe(true);
  });

  // Llegan así cuando el backend no manda el campo. Sin esto el navegador acaba
  // pidiendo la propia página como si fuera una imagen.
  it.each([undefined, 'undefined', 'null', '', '   '])('trata %p como que no hay imagen', valor => {
    expect(esRelleno(montar(valor))).toBe(true);
  });

  it('si la descarga falla, sustituye la imagen', () => {
    const img = montar(URL_BUENA);
    expect(esRelleno(img)).toBe(false);

    img.dispatchEvent(new Event('error'));

    expect(esRelleno(img)).toBe(true);
    expect(img.classList.contains('img-sustituida')).toBe(true);
  });

  it('no entra en bucle si el propio sustituto fallara', () => {
    const img = montar(URL_BUENA);
    img.dispatchEvent(new Event('error'));
    const trasPrimerFallo = img.getAttribute('src');

    img.dispatchEvent(new Event('error'));

    expect(img.getAttribute('src')).toBe(trasPrimerFallo);
  });

  it('el relleno cambia según el tipo pedido', () => {
    // Un escudo y una silueta no pueden salir iguales: si coincidieran, sería que
    // el tipo no se está leyendo y todo caería en el mismo dibujo por defecto.
    @Component({
      standalone: true,
      imports: [ImagenFallbackDirective],
      template: `<img svImagen="equipo" [src]="null" alt=""><img svImagen="jugador" [src]="null" alt="">`,
    })
    class DosTipos {}

    const fixture = TestBed.createComponent(DosTipos);
    fixture.detectChanges();
    const [escudo, silueta] = fixture.nativeElement.querySelectorAll('img') as NodeListOf<HTMLImageElement>;

    expect(escudo.getAttribute('src')).not.toBe(silueta.getAttribute('src'));
  });
});
