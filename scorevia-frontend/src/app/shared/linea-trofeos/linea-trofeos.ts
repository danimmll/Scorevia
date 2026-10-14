import { ChangeDetectionStrategy, Component, Input, computed, signal } from '@angular/core';
import { Trofeo } from '../../core/models/estadisticas.model';
import { TranslocoPipe } from '@jsverse/transloco';

/** Una temporada de la línea del tiempo, con lo que se ganó y lo que se perdió en ella. */
export interface TemporadaDeTrofeos {
  /** Tal cual viene de la API: "2023/2024" o "2023". Null si no consta. */
  etiqueta: string | null;
  /** Para ordenar: el año en que empieza la temporada. */
  anio: number;
  ganados: Trofeo[];
  subcampeonatos: Trofeo[];
}

/** Lo que la API llama a un título ganado. El otro valor que usa es "2nd Place". */
const GANADO = 'Winner';

/**
 * El palmarés como línea del tiempo, temporada a temporada.
 *
 * La ficha de jugador ya agrupaba los trofeos por competición ("Champions x5"), que responde
 * a "¿cuántas tiene?" pero no a "¿cuándo?". Esto responde a lo segundo: una carrera se lee
 * mucho mejor viendo los años buenos seguidos que en un montón de contadores.
 *
 * Se usa igual en jugador, entrenador y equipo, así que las tres fichas cuentan lo mismo de
 * la misma forma.
 */
@Component({
  imports: [TranslocoPipe],
  selector: 'sv-linea-trofeos',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './linea-trofeos.html',
  styleUrl: './linea-trofeos.scss',
})
export class LineaTrofeosComponent {
  @Input({ required: true }) set trofeos(valor: Trofeo[]) {
    this.origen.set(valor ?? []);
  }

  /** Incluir los subcampeonatos. Son casi la mitad de las filas (265.862 de 560.212). */
  @Input() set incluirSubcampeonatos(valor: boolean) {
    this.conSubcampeonatos.set(valor);
  }

  protected readonly origen = signal<Trofeo[]>([]);
  protected readonly conSubcampeonatos = signal(true);

  /** Cuántas temporadas se pintan antes de ofrecer "ver todo". */
  private static readonly VISIBLES_AL_PRINCIPIO = 6;
  protected readonly desplegado = signal(false);

  /**
   * Los trofeos agrupados por temporada, de la más reciente a la más antigua.
   *
   * Las que no traen temporada van al final juntas: son 108.788 filas de 560.212, así que no
   * se pueden tirar, pero tampoco pueden encabezar una línea del tiempo.
   */
  protected readonly temporadas = computed<TemporadaDeTrofeos[]>(() => {
    const porTemporada = new Map<string, TemporadaDeTrofeos>();

    for (const trofeo of this.origen()) {
      const esGanado = trofeo.resultado === GANADO;
      if (!esGanado && !this.conSubcampeonatos()) continue;

      const etiqueta = trofeo.temporada?.trim() || null;
      const clave = etiqueta ?? '__sin_temporada';
      const grupo = porTemporada.get(clave)
        ?? { etiqueta, anio: anioDe(etiqueta), ganados: [], subcampeonatos: [] };

      (esGanado ? grupo.ganados : grupo.subcampeonatos).push(trofeo);
      porTemporada.set(clave, grupo);
    }

    return [...porTemporada.values()].sort((a, b) => b.anio - a.anio);
  });

  protected readonly visibles = computed(() =>
    this.desplegado()
      ? this.temporadas()
      : this.temporadas().slice(0, LineaTrofeosComponent.VISIBLES_AL_PRINCIPIO));

  protected readonly ocultas = computed(() =>
    Math.max(0, this.temporadas().length - this.visibles().length));

  protected readonly totalGanados = computed(() =>
    this.temporadas().reduce((suma, t) => suma + t.ganados.length, 0));

  protected alternar(): void {
    this.desplegado.update(v => !v);
  }
}

/**
 * El año en que empieza la temporada, para ordenar.
 *
 * "2023/2024" y "2023" dan los dos 2023: son la misma temporada escrita de dos formas según
 * la competición. Lo que no se puede leer se va al final, no al principio.
 */
function anioDe(temporada: string | null): number {
  if (!temporada) return Number.NEGATIVE_INFINITY;
  const anio = Number(temporada.slice(0, 4));
  return Number.isFinite(anio) ? anio : Number.NEGATIVE_INFINITY;
}
