import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, signal } from '@angular/core';
import { FilaPartidoComponent } from './fila-partido';
import { Partido } from '../../core/models/estadisticas.model';
import { importarTranslocoDePrueba } from '../../core/i18n/testing';

/**
 * La fila va como selector de atributo sobre un `<tr>`, así que necesita una tabla que la
 * aloje: creada suelta, el navegador la saca de sitio y no se puede consultar el DOM.
 *
 * El partido va en una señal y no en un campo normal a propósito: la aplicación es zoneless,
 * así que escribir un campo suelto no marca la vista como sucia y el segundo
 * `detectChanges()` no volvería a evaluar el binding. Con la señal, el segundo render es un
 * refresco de verdad, que es justo lo que se quiere probar.
 */
@Component({
  standalone: true,
  imports: [FilaPartidoComponent],
  template: `<table><tbody><tr svFilaPartido [partido]="partido()"></tr></tbody></table>`,
})
class AnfitrionComponent {
  readonly partido = signal<Partido>({} as Partido);
}

describe('FilaPartidoComponent', () => {
  let fixture: ComponentFixture<AnfitrionComponent>;

  const partido = (cambios: Partial<Partido> = {}): Partido => ({
    idPartido: 1,
    equipoLocal: 'Girona',
    equipoVisitante: 'Atletico Madrid',
    estado: 'EN_JUEGO',
    estadoCorto: '2H',
    minuto: 67,
    resultadoLocal: 0,
    resultadoVisitante: 0,
    ...cambios,
  } as Partido);

  const marcador = () => fixture.nativeElement.querySelector('.fp-marcador') as HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [AnfitrionComponent, importarTranslocoDePrueba()] }).compileComponents();
    fixture = TestBed.createComponent(AnfitrionComponent);
  });

  it('un partido en juego lleva punto rojo y minuto', () => {
    fixture.componentInstance.partido.set(partido());
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.fp-punto')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.fp-minuto').textContent.trim()).toBe("67'");
  });

  it('un partido terminado no lleva punto rojo', () => {
    fixture.componentInstance.partido.set(partido({ estado: 'FINALIZADO', estadoCorto: 'FT', minuto: null }));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.fp-punto')).toBeNull();
    expect(fixture.nativeElement.querySelector('.fp-estado').textContent.trim()).toBe('Final');
  });

  it('la primera carga no destella', () => {
    // Si destellara, al abrir la página parpadearían todas las filas a la vez.
    fixture.componentInstance.partido.set(partido());
    fixture.detectChanges();

    expect(marcador().classList.contains('fp-marcador--gol')).toBe(false);
  });

  it('un gol destella el marcador', () => {
    fixture.componentInstance.partido.set(partido());
    fixture.detectChanges();

    fixture.componentInstance.partido.set(partido({ resultadoLocal: 1 }));
    fixture.detectChanges();

    expect(marcador().classList.contains('fp-marcador--gol')).toBe(true);
  });

  it('un refresco que no cambia el marcador no destella', () => {
    // El listado se recarga cada 30 segundos y casi siempre trae lo mismo: si destellara en
    // cada vuelta, el destello dejaría de significar "gol".
    fixture.componentInstance.partido.set(partido());
    fixture.detectChanges();

    fixture.componentInstance.partido.set(partido({ minuto: 68 }));
    fixture.detectChanges();

    expect(marcador().classList.contains('fp-marcador--gol')).toBe(false);
  });

  it('un marcador que aparece al terminar el partido no destella', () => {
    // Un partido que pasa de programado a finalizado también "cambia" de marcador, pero eso
    // no es un gol en directo: el destello es solo para lo que está pasando ahora.
    fixture.componentInstance.partido.set(partido({ estado: 'PROGRAMADO', resultadoLocal: null, resultadoVisitante: null }));
    fixture.detectChanges();

    fixture.componentInstance.partido.set(partido({ estado: 'FINALIZADO', resultadoLocal: 2, resultadoVisitante: 1 }));
    fixture.detectChanges();

    expect(marcador().classList.contains('fp-marcador--gol')).toBe(false);
  });
});
