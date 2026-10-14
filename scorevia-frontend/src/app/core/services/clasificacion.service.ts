import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable } from 'rxjs';
import { Clasificacion } from '../models/estadisticas.model';

@Injectable({ providedIn: 'root' })
export class ClasificacionService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/api/v1/clasificaciones`;

  getTabla(idLiga: number, temporada: number): Observable<Clasificacion[]> {
    return this.http.get<Clasificacion[]>(`${this.apiUrl}/liga/${idLiga}/temporada/${temporada}`);
  }
}
