import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable } from 'rxjs';

/** Disparadores manuales de ingesta -- solo ADMIN. Todas devuelven texto plano de estado. */
@Injectable({ providedIn: 'root' })
export class SyncService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/api/v1/partidos`;

  sync(fecha: string): Observable<string> {
    const params = new HttpParams().set('fecha', fecha);
    return this.http.post(`${this.apiUrl}/sync`, null, { params, responseType: 'text' });
  }

  statsSync(): Observable<string> {
    return this.http.post(`${this.apiUrl}/stats-sync`, null, { responseType: 'text' });
  }

  syncTrofeosBajas(): Observable<string> {
    return this.http.post(`${this.apiUrl}/sync-trofeos-bajas`, null, { responseType: 'text' });
  }

  syncTraspasos(): Observable<string> {
    return this.http.post(`${this.apiUrl}/sync-traspasos`, null, { responseType: 'text' });
  }

  equiposEstadios(idLiga: number, temporada: number): Observable<string> {
    const params = new HttpParams().set('idLiga', idLiga).set('temporada', temporada);
    return this.http.post(`${this.apiUrl}/equipos-estadios`, null, { params, responseType: 'text' });
  }

  entrenadores(): Observable<string> {
    return this.http.post(`${this.apiUrl}/entrenadores`, null, { responseType: 'text' });
  }

  clasificaciones(idLiga: number, temporada: number): Observable<string> {
    const params = new HttpParams().set('idLiga', idLiga).set('temporada', temporada);
    return this.http.post(`${this.apiUrl}/clasificaciones`, null, { params, responseType: 'text' });
  }

  ligas(temporada: number): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/ligas/${temporada}`, null);
  }

  jugadoresCompletos(temporada: number): Observable<string> {
    return this.http.post(`${this.apiUrl}/jugadores/completos/${temporada}`, null, { responseType: 'text' });
  }

  perfilesJugadores(): Observable<string> {
    return this.http.post(`${this.apiUrl}/perfiles-jugadores`, null, { responseType: 'text' });
  }

  syncEnVivo(): Observable<string> {
    return this.http.post(`${this.apiUrl}/sync-en-vivo`, null, { responseType: 'text' });
  }

  eventosHistoricos(): Observable<string> {
    return this.http.post(`${this.apiUrl}/eventos-historicos`, null, { responseType: 'text' });
  }
}
