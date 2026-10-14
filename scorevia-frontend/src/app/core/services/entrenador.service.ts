import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable } from 'rxjs';
import { Entrenador, Trofeo, HistorialEntrenador } from '../models/estadisticas.model';

@Injectable({ providedIn: 'root' })
export class EntrenadorService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/api/v1/entrenadores`;

  getPorId(idEntrenador: number): Observable<Entrenador> {
    return this.http.get<Entrenador>(`${this.apiUrl}/${idEntrenador}`);
  }

  getPorEquipo(idEquipo: number): Observable<Entrenador> {
    return this.http.get<Entrenador>(`${this.apiUrl}/equipo/${idEquipo}`);
  }

  // Vía Clasificacion en el backend, mismo motivo que EquipoService.getPorLiga.
  getPorLiga(idLiga: number, temporada: number): Observable<Entrenador[]> {
    const params = new HttpParams().set('temporada', temporada);
    return this.http.get<Entrenador[]>(`${this.apiUrl}/liga/${idLiga}`, { params });
  }

  getTrofeos(idEntrenador: number): Observable<Trofeo[]> {
    return this.http.get<Trofeo[]>(`${this.apiUrl}/${idEntrenador}/trofeos`);
  }

  // Clubes que ha entrenado, más reciente primero.
  getHistorialClubes(idEntrenador: number): Observable<HistorialEntrenador[]> {
    return this.http.get<HistorialEntrenador[]>(`${this.apiUrl}/${idEntrenador}/historial`);
  }

  // Técnicos que han pasado por el banquillo de este equipo, más reciente primero.
  getHistorialPorEquipo(idEquipo: number): Observable<HistorialEntrenador[]> {
    return this.http.get<HistorialEntrenador[]>(`${this.apiUrl}/equipo/${idEquipo}/historial`);
  }

  // Autocompletado por nombre.
  buscar(nombre: string): Observable<Entrenador[]> {
    const params = new HttpParams().set('nombre', nombre);
    return this.http.get<Entrenador[]>(`${this.apiUrl}/buscar`, { params });
  }
}
