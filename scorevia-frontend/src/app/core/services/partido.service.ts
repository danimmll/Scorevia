import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable, forkJoin, of, switchMap, map, tap, catchError } from 'rxjs';
import { Pagina } from '../models/pagina.model';
import {
  AlineacionesPartido,
  BajaPartido,
  CuotasPartido,
  EstadisticaEquipo,
  EstadisticaJugadorPartido,
  EventoPartido,
  Partido,
  PartidoDetalleCompleto
} from '../models/estadisticas.model';

@Injectable({
  providedIn: 'root'
})
export class PartidoService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/api/v1/partidos`;

  partidosEnVivo = signal<Partido[]>([]);

  /**
   * TODOS los partidos de hoy, en una sola lista.
   *
   * Se piden todas las páginas y no solo la primera porque la pantalla los agrupa por
   * competición: con 451 partidos en un día y páginas de 100 ordenadas por hora, la primera
   * página son los primeros horarios (Australia, Asia) y las grandes ligas europeas —que
   * juegan por la tarde— no aparecían por ningún lado. Agrupar media lista da un orden
   * engañoso, no un orden parcial.
   *
   * El filtro por fecha lo hace el servidor. Antes se pedía el listado general y se
   * filtraba en el cliente, lo que funcionó mientras el partido más reciente fuera de ayer;
   * con el calendario ingerido hasta mayo de 2027 la primera página son partidos de dentro
   * de ocho meses y la lista salía SIEMPRE vacía.
   */
  getPartidosHoy(): Observable<Partido[]> {
    const hoy = new Date();
    const fecha = `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}-${String(hoy.getDate()).padStart(2, '0')}`;
    const pagina = (n: number) => this.http.get<Pagina<Partido>>(this.apiUrl, {
      params: new HttpParams().set('page', n).set('size', 100).set('fecha', fecha)
    });

    return pagina(0).pipe(
      switchMap(primera => {
        if (primera.totalPages <= 1) return of([primera]);
        const resto = Array.from({ length: primera.totalPages - 1 }, (_, i) => pagina(i + 1));
        return forkJoin(resto).pipe(map(paginas => [primera, ...paginas]));
      }),
      map(paginas => paginas.flatMap(p => p.content)),
    );
  }

  getPartidosEnVivo(): Observable<Partido[]> {
    return this.http.get<Partido[]>(`${this.apiUrl}/en-vivo`).pipe(
      tap(partidos => this.partidosEnVivo.set(partidos))
    );
  }

  getPartidoPorId(idPartido: number): Observable<Partido> {
    return this.http.get<Partido>(`${this.apiUrl}/${idPartido}`);
  }

  getPartidosPorLiga(idLiga: number, temporada?: number, page = 0, size = 20): Observable<Pagina<Partido>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (temporada) params = params.set('temporada', temporada);
    return this.http.get<Pagina<Partido>>(`${this.apiUrl}/liga/${idLiga}`, { params });
  }

  /**
   * Partidos de un equipo. `estado` separa jugados de programados.
   *
   * El filtro lo hace el backend y no el cliente porque cada lista tiene su propia
   * paginación: partiendo una página de 5 en memoria saldrían "3 jugados y 2 programados"
   * y el total de páginas no cuadraría con ninguna de las dos.
   */
  getPartidosPorEquipo(
    idEquipo: number,
    page = 0,
    size = 20,
    estado?: 'JUGADOS' | 'PROGRAMADOS',
  ): Observable<Pagina<Partido>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (estado) params = params.set('estado', estado);
    return this.http.get<Pagina<Partido>>(`${this.apiUrl}/equipo/${idEquipo}`, { params });
  }

  getEnfrentamientos(idEquipoA: number, idEquipoB: number, page = 0, size = 20): Observable<Pagina<Partido>> {
    const params = new HttpParams()
      .set('equipoA', idEquipoA)
      .set('equipoB', idEquipoB)
      .set('page', page)
      .set('size', size);
    return this.http.get<Pagina<Partido>>(`${this.apiUrl}/enfrentamientos`, { params });
  }

  getEstadisticasEquipos(idPartido: number): Observable<EstadisticaEquipo[]> {
    return this.http.get<EstadisticaEquipo[]>(`${this.apiUrl}/${idPartido}/estadisticas`);
  }

  getEstadisticasJugadores(idPartido: number): Observable<EstadisticaJugadorPartido[]> {
    return this.http.get<EstadisticaJugadorPartido[]>(`${this.apiUrl}/${idPartido}/jugadores`);
  }

  getEventos(idPartido: number): Observable<EventoPartido[]> {
    return this.http.get<EventoPartido[]>(`${this.apiUrl}/${idPartido}/eventos`);
  }

  /**
   * Los dos onces, los banquillos, los entrenadores y el árbitro del partido. Sale de datos
   * ya ingeridos, así que no cuesta ninguna petición a API-Football.
   */
  getAlineaciones(idPartido: number): Observable<AlineacionesPartido> {
    return this.http.get<AlineacionesPartido>(`${this.apiUrl}/${idPartido}/alineaciones`);
  }

  /**
   * Cuotas del partido. El backend las pide a API-Football en el momento y las cachea 3h, así
   * que abrir la misma ficha dos veces no gasta dos peticiones.
   */
  getCuotas(idPartido: number): Observable<CuotasPartido> {
    return this.http.get<CuotasPartido>(`${this.apiUrl}/${idPartido}/cuotas`);
  }

  /** Bajas y dudas de los dos equipos para este partido. */
  getBajas(idPartido: number): Observable<BajaPartido[]> {
    return this.http.get<BajaPartido[]>(`${this.apiUrl}/${idPartido}/bajas`);
  }

  /** Todo lo necesario para pintar una página de detalle de un partido, de una vez. */
  getPartidoDetalleCompleto(idPartido: number, idEquipoLocal: number, idEquipoVisitante: number): Observable<PartidoDetalleCompleto> {
    return forkJoin({
      info: this.getPartidoPorId(idPartido),
      statsEquipos: this.getEstadisticasEquipos(idPartido),
      statsJugadores: this.getEstadisticasJugadores(idPartido),
      eventos: this.getEventos(idPartido),
      h2h: this.getEnfrentamientos(idEquipoLocal, idEquipoVisitante),
      // Con catchError y no a pelo: en un forkJoin, si una sola llamada falla se cae la
      // pantalla entera. Las alineaciones son la parte más nueva del backend, así que un
      // entorno con el contenedor todavía sin actualizar respondería 404 aquí y se llevaría
      // por delante el marcador, las stats y los eventos, que sí están.
      alineaciones: this.getAlineaciones(idPartido).pipe(
        catchError(() => of({ idPartido, hayDatos: false } as AlineacionesPartido))
      )
    });
  }
}
