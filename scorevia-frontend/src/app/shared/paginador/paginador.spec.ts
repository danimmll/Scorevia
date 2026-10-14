import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PaginadorComponent } from './paginador';
import { importarTranslocoDePrueba } from '../../core/i18n/testing';

describe('PaginadorComponent', () => {
  let fixture: ComponentFixture<PaginadorComponent>;
  let componente: PaginadorComponent;

  /** Los números visibles, con null donde va una elipsis. */
  const numeros = (): (number | null)[] => (componente as unknown as {
    numeros: () => (number | null)[];
  }).numeros();

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [PaginadorComponent, importarTranslocoDePrueba()] }).compileComponents();
    fixture = TestBed.createComponent(PaginadorComponent);
    componente = fixture.componentInstance;
  });

  it('con pocas páginas las enseña todas, sin elipsis', () => {
    componente.pagina = 0;
    componente.totalPaginas = 5;
    expect(numeros()).toEqual([0, 1, 2, 3, 4]);
  });

  it('no pinta nada si solo hay una página', () => {
    componente.pagina = 0;
    componente.totalPaginas = 1;
    expect(numeros()).toEqual([]);
  });

  it('con muchas páginas deja la primera, la última y una ventana alrededor de la actual', () => {
    componente.pagina = 10;
    componente.totalPaginas = 40;
    // Elipsis a los dos lados: 1 … 9 10 11 12 13 … 40 (en base 0).
    expect(numeros()).toEqual([0, null, 8, 9, 10, 11, 12, null, 39]);
  });

  it('al principio no mete elipsis por la izquierda', () => {
    componente.pagina = 1;
    componente.totalPaginas = 40;
    expect(numeros()).toEqual([0, 1, 2, 3, null, 39]);
  });

  it('un hueco de una sola página se pinta como número, no como elipsis', () => {
    // La elipsis ocuparía lo mismo que el número que esconde, y encima no se puede pinchar.
    componente.pagina = 3;
    componente.totalPaginas = 7;
    expect(numeros()).toEqual([0, 1, 2, 3, 4, 5, 6]);
  });

  it('emite la página pedida', () => {
    componente.pagina = 2;
    componente.totalPaginas = 10;
    const emitidas: number[] = [];
    componente.cambio.subscribe(n => emitidas.push(n));

    (componente as unknown as { ir: (n: number) => void }).ir(5);
    expect(emitidas).toEqual([5]);
  });

  it('ignora los saltos imposibles en vez de emitir una página que no existe', () => {
    componente.pagina = 0;
    componente.totalPaginas = 3;
    const emitidas: number[] = [];
    componente.cambio.subscribe(n => emitidas.push(n));

    (componente as unknown as { ir: (n: number) => void }).ir(-1);   // antes de la primera
    (componente as unknown as { ir: (n: number) => void }).ir(3);    // después de la última
    (componente as unknown as { ir: (n: number) => void }).ir(0);    // la que ya está puesta
    expect(emitidas).toEqual([]);
  });
});
