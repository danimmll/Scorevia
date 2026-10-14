import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LineaTrofeosComponent, TemporadaDeTrofeos } from './linea-trofeos';
import { Trofeo } from '../../core/models/estadisticas.model';
import { importarTranslocoDePrueba } from '../../core/i18n/testing';

describe('LineaTrofeosComponent', () => {
  let fixture: ComponentFixture<LineaTrofeosComponent>;
  let componente: LineaTrofeosComponent;

  const temporadas = (): TemporadaDeTrofeos[] =>
    (componente as unknown as { temporadas: () => TemporadaDeTrofeos[] }).temporadas();

  const trofeo = (id: number, competicion: string, temporada: string | undefined, resultado: string): Trofeo =>
    ({ idTrofeo: id, competicion, temporada, resultado } as Trofeo);

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [LineaTrofeosComponent, importarTranslocoDePrueba()] }).compileComponents();
    fixture = TestBed.createComponent(LineaTrofeosComponent);
    componente = fixture.componentInstance;
  });

  it('agrupa por temporada, de la más reciente a la más antigua', () => {
    componente.trofeos = [
      trofeo(1, 'La Liga', '2011/2012', 'Winner'),
      trofeo(2, 'Champions League', '2023/2024', 'Winner'),
      trofeo(3, 'Copa del Rey', '2011/2012', 'Winner'),
    ];

    const resultado = temporadas();
    expect(resultado.map(t => t.etiqueta)).toEqual(['2023/2024', '2011/2012']);
    // Dos títulos en la misma temporada van juntos en la misma fila.
    expect(resultado[1].ganados).toHaveLength(2);
  });

  it('los dos formatos de temporada se ordenan entre sí', () => {
    // "2023/2024" y "2023" son la misma temporada escrita según la competición.
    componente.trofeos = [
      trofeo(1, 'Copa América', '2021', 'Winner'),
      trofeo(2, 'Premier League', '2022/2023', 'Winner'),
    ];

    expect(temporadas().map(t => t.etiqueta)).toEqual(['2022/2023', '2021']);
  });

  it('separa lo ganado de los subcampeonatos', () => {
    componente.trofeos = [
      trofeo(1, 'La Liga', '2023/2024', 'Winner'),
      trofeo(2, 'Champions League', '2023/2024', '2nd Place'),
    ];

    const t = temporadas()[0];
    expect(t.ganados.map(g => g.competicion)).toEqual(['La Liga']);
    expect(t.subcampeonatos.map(s => s.competicion)).toEqual(['Champions League']);
  });

  it('puede esconder los subcampeonatos', () => {
    componente.trofeos = [trofeo(1, 'Champions League', '2023/2024', '2nd Place')];
    componente.incluirSubcampeonatos = false;

    // La temporada entera desaparece si no queda nada que enseñar en ella.
    expect(temporadas()).toEqual([]);
  });

  it('los trofeos sin temporada van al final, nunca encabezando la línea', () => {
    // Son 108.788 de 560.212 filas: no se pueden tirar, pero tampoco abrir el palmarés.
    componente.trofeos = [
      trofeo(1, 'Copa sin fecha', undefined, 'Winner'),
      trofeo(2, 'La Liga', '2015/2016', 'Winner'),
    ];

    const resultado = temporadas();
    expect(resultado[0].etiqueta).toBe('2015/2016');
    expect(resultado[1].etiqueta).toBeNull();
  });

  it('sin trofeos no explota', () => {
    componente.trofeos = [];
    expect(temporadas()).toEqual([]);
  });
});
