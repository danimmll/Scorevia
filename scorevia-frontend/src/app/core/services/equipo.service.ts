import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable } from 'rxjs';
import { Equipo, TemporadaEquipo, Traspaso } from '../models/estadisticas.model';

@Injectable({ providedIn: 'root' })
export class EquipoService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/api/v1/equipos`;

  getPorId(idEquipo: number): Observable<Equipo> {
    return this.http.get<Equipo>(`${this.apiUrl}/${idEquipo}`);
  }

  // Vía Clasificacion en el backend (liga+equipo+temporada), no vía "Equipo.liga" -- un mismo
  // club puede jugar liga + Champions + copa a la vez, y ese campo es una FK única que la
  // última ingesta se pisa. Por eso hace falta la temporada aquí (antes no la pedía).
  getPorLiga(idLiga: number, temporada: number): Observable<Equipo[]> {
    const params = new HttpParams().set('temporada', temporada);
    return this.http.get<Equipo[]>(`${this.apiUrl}/liga/${idLiga}`, { params });
  }

  getTraspasos(idEquipo: number): Observable<Traspaso[]> {
    return this.http.get<Traspaso[]>(`${this.apiUrl}/${idEquipo}/traspasos`);
  }

  // Búsqueda global por nombre -- no limitada al país/liga actualmente filtrados.
  buscar(nombre: string): Observable<Equipo[]> {
    const params = new HttpParams().set('nombre', nombre);
    return this.http.get<Equipo[]>(`${this.apiUrl}/buscar`, { params });
  }

  /** Las temporadas en las que el equipo jugó, de la más reciente a la más antigua. */
  getTemporadas(idEquipo: number): Observable<number[]> {
    return this.http.get<number[]>(`${this.apiUrl}/${idEquipo}/temporadas`);
  }

  /**
   * La temporada completa: plantilla con lo que hizo cada jugador, entrenadores,
   * competiciones con su puesto final, títulos y totales.
   *
   * Todo de datos ya ingeridos: cero peticiones a API-Football.
   */
  getTemporada(idEquipo: number, temporada: number): Observable<TemporadaEquipo> {
    return this.http.get<TemporadaEquipo>(`${this.apiUrl}/${idEquipo}/temporadas/${temporada}`);
  }
}
