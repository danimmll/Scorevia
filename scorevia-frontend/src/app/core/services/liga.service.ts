import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable } from 'rxjs';
import { Liga, RankingJugador, TemporadaLiga, TipoRanking } from '../models/estadisticas.model';
import { map, shareReplay } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class LigaService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/api/v1/ligas`;

  getListado(): Observable<Liga[]> {
    return this.http.get<Liga[]>(this.apiUrl);
  }

  getPorId(idLiga: number): Observable<Liga> {
    return this.http.get<Liga>(`${this.apiUrl}/${idLiga}`);
  }

  /**
   * Temporadas de las que hay datos para una liga, de la más reciente a la más antigua.
   *
   * No se puede usar `liga.temporadaActual` como temporada por defecto: unas veces apunta a
   * una temporada recién sorteada, con la tabla entera a cero (111 competiciones estaban así),
   * y en las grandes ligas llegó a quedarse en 2010 por los backfills históricos -- por eso
   * "La Liga" abría en la clasificación 2010-11, con el Hércules en Primera.
   *
   * Se cachea por liga porque lo piden varias pantallas seguidas (equipos, clasificación,
   * partidos) y el catálogo solo cambia cuando entra una sincronización.
   */
  getTemporadas(idLiga: number): Observable<TemporadaLiga[]> {
    const cacheada = this.cacheTemporadas.get(idLiga);
    if (cacheada) return cacheada;

    const peticion = this.http
      .get<TemporadaLiga[]>(`${this.apiUrl}/${idLiga}/temporadas`)
      .pipe(shareReplay({ bufferSize: 1, refCount: false }));
    this.cacheTemporadas.set(idLiga, peticion);
    return peticion;
  }

  /** Última temporada con partidos jugados; si ninguna los tiene, la más reciente que haya. */
  getTemporadaPorDefecto(idLiga: number, respaldo: number): Observable<number> {
    return this.getTemporadas(idLiga).pipe(
      map(temporadas => temporadas.find(t => t.conDatos)?.temporada ?? temporadas[0]?.temporada ?? respaldo)
    );
  }

  private readonly cacheTemporadas = new Map<number, Observable<TemporadaLiga[]>>();

  /**
   * Ranking de jugadores de una competición.
   *
   * Sale directamente de API-Football (una petición por liga+temporada+tipo, cacheada 6h en el
   * backend) en vez de una tabla propia: es un agregado que ellos ya calculan y que se mueve
   * como mucho una vez por jornada.
   */
  getRanking(idLiga: number, temporada: number, tipo: TipoRanking = 'GOLES'): Observable<RankingJugador[]> {
    const params = new HttpParams().set('temporada', temporada).set('tipo', tipo);
    return this.http.get<RankingJugador[]>(`${this.apiUrl}/${idLiga}/rankings`, { params });
  }
}
