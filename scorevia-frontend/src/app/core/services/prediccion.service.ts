import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable } from 'rxjs';
import { LivePrediction, ProbabilidadesPrediccion } from '../models/prediccion.model';

@Injectable({ providedIn: 'root' })
export class PrediccionService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/api/v1/predicciones`;

  /** Requiere sesión; el backend devuelve 403 si el usuario FREE no compró acceso a este partido. */
  getPrediccionPrePartido(idPartido: number): Observable<ProbabilidadesPrediccion> {
    return this.http.get<ProbabilidadesPrediccion>(`${this.apiUrl}/${idPartido}`);
  }

  /** El marcador se lee siempre en el servidor -- solo se manda el minuto. */
  getPrediccionEnVivo(idPartido: number, minuto: number): Observable<LivePrediction> {
    const params = new HttpParams().set('minuto', minuto);
    return this.http.get<LivePrediction>(`${this.apiUrl}/${idPartido}/en-vivo`, { params });
  }
}
