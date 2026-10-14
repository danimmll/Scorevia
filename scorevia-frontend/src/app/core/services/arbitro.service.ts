import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable } from 'rxjs';
import { ArbitroDetalle, Partido } from '../models/estadisticas.model';
import { Pagina } from '../models/pagina.model';

/**
 * Ficha de árbitro.
 *
 * Existe porque desde un partido se podía llegar al equipo, al jugador y al entrenador, pero
 * el árbitro era un nombre suelto sin ningún sitio al que ir.
 */
@Injectable({ providedIn: 'root' })
export class ArbitroService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/api/v1/arbitros`;

  getArbitro(idArbitro: number): Observable<ArbitroDetalle> {
    return this.http.get<ArbitroDetalle>(`${this.apiUrl}/${idArbitro}`);
  }

  getPartidos(idArbitro: number, page = 0, size = 20): Observable<Pagina<Partido>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Pagina<Partido>>(`${this.apiUrl}/${idArbitro}/partidos`, { params });
  }
}
