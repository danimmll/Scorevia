import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable } from 'rxjs';
import { Pagina } from '../models/pagina.model';
import { HistorialBaja, HistorialDorsal, Jugador, Trofeo, Traspaso } from '../models/estadisticas.model';

@Injectable({ providedIn: 'root' })
export class JugadorService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/api/v1/jugadores`;

  // Pageable estándar de Spring (page/size/sort), distinto del paginado a mano de PartidoController.
  getListado(page = 0, size = 20): Observable<Pagina<Jugador>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Pagina<Jugador>>(this.apiUrl, { params });
  }

  getPorId(idJugador: number): Observable<Jugador> {
    return this.http.get<Jugador>(`${this.apiUrl}/${idJugador}`);
  }

  getPorEquipo(idEquipo: number, page = 0, size = 20): Observable<Pagina<Jugador>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Pagina<Jugador>>(`${this.apiUrl}/equipo/${idEquipo}`, { params });
  }

  // Vía Clasificacion en el backend (liga+equipo+temporada), mismo motivo que
  // EquipoService.getPorLiga -- un equipo puede jugar liga + Champions + copa a la vez.
  getPorLiga(idLiga: number, temporada: number, page = 0, size = 20): Observable<Pagina<Jugador>> {
    const params = new HttpParams().set('temporada', temporada).set('page', page).set('size', size);
    return this.http.get<Pagina<Jugador>>(`${this.apiUrl}/liga/${idLiga}`, { params });
  }

  // Búsqueda global por nombre -- no limitada a la liga/equipo actualmente filtrados en la
  // página, a diferencia de un filtro client-side sobre la página ya cargada.
  buscar(nombre: string, page = 0, size = 20): Observable<Pagina<Jugador>> {
    const params = new HttpParams().set('nombre', nombre).set('page', page).set('size', size);
    return this.http.get<Pagina<Jugador>>(`${this.apiUrl}/buscar`, { params });
  }

  getTrofeos(idJugador: number): Observable<Trofeo[]> {
    return this.http.get<Trofeo[]>(`${this.apiUrl}/${idJugador}/trofeos`);
  }

  getBajas(idJugador: number): Observable<HistorialBaja[]> {
    return this.http.get<HistorialBaja[]>(`${this.apiUrl}/${idJugador}/bajas`);
  }

  getTraspasos(idJugador: number): Observable<Traspaso[]> {
    return this.http.get<Traspaso[]>(`${this.apiUrl}/${idJugador}/traspasos`);
  }

  getDorsales(idJugador: number): Observable<HistorialDorsal[]> {
    return this.http.get<HistorialDorsal[]>(`${this.apiUrl}/${idJugador}/dorsales`);
  }
}
