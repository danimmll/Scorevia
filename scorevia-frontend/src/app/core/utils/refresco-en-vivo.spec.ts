import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { refrescarEnVivo } from './refresco-en-vivo';
import { importarTranslocoDePrueba } from '../i18n/testing';

@Component({ standalone: true, template: '' })
class AnfitrionComponent {
  turnos = 0;

  constructor() {
    refrescarEnVivo(1000, () => this.turnos++);
  }
}

/** Pone la pestaña en primer o segundo plano y avisa, como hace el navegador. */
function visibilidad(estado: DocumentVisibilityState): void {
  Object.defineProperty(document, 'visibilityState', { value: estado, configurable: true });
  document.dispatchEvent(new Event('visibilitychange'));
}

describe('refrescarEnVivo', () => {
  let fixture: ComponentFixture<AnfitrionComponent>;

  beforeEach(async () => {
    vi.useFakeTimers();
    visibilidad('visible');
    await TestBed.configureTestingModule({ imports: [AnfitrionComponent, importarTranslocoDePrueba()] }).compileComponents();
    fixture = TestBed.createComponent(AnfitrionComponent);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('repite la acción cada intervalo', () => {
    expect(fixture.componentInstance.turnos).toBe(0);

    vi.advanceTimersByTime(3000);

    expect(fixture.componentInstance.turnos).toBe(3);
  });

  it('se para con la pestaña en segundo plano y se reanuda al volver', () => {
    vi.advanceTimersByTime(1000);
    expect(fixture.componentInstance.turnos).toBe(1);

    visibilidad('hidden');
    vi.advanceTimersByTime(5000);
    expect(fixture.componentInstance.turnos).toBe(1);

    // Al volver refresca en el acto, sin esperar al siguiente turno: es justo cuando el
    // usuario quiere ver lo que se ha perdido.
    visibilidad('visible');
    expect(fixture.componentInstance.turnos).toBe(2);

    vi.advanceTimersByTime(1000);
    expect(fixture.componentInstance.turnos).toBe(3);
  });

  it('deja de repetir al destruirse el componente', () => {
    vi.advanceTimersByTime(1000);
    expect(fixture.componentInstance.turnos).toBe(1);

    fixture.destroy();
    vi.advanceTimersByTime(5000);

    expect(fixture.componentInstance.turnos).toBe(1);
  });
});
