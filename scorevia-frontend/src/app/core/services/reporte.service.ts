import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable } from 'rxjs';
import { Pagina } from '../models/pagina.model';
import { EstadoReporte, Reporte } from '../models/reporte.model';

@Injectable({ providedIn: 'root' })
export class ReporteService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/api/v1/reportes`;

  crear(reporte: Pick<Reporte, 'idDenunciado' | 'motivo'>): Observable<Reporte> {
    return this.http.post<Reporte>(`${this.apiUrl}/crear`, reporte);
  }

  getPendientesAdmin(page = 0, size = 20): Observable<Pagina<Reporte>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Pagina<Reporte>>(`${this.apiUrl}/pendientes`, { params });
  }

  /** estado/resolucion van por query param, no en el body. */
  resolver(idReporte: number, estado: EstadoReporte, resolucion: string): Observable<Reporte> {
    const params = new HttpParams().set('estado', estado).set('resolucion', resolucion);
    return this.http.put<Reporte>(`${this.apiUrl}/${idReporte}/resolver`, null, { params });
  }

  /** Solo el propio denunciante, y solo mientras el reporte siga PENDIENTE. */
  retirar(idReporte: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${idReporte}`);
  }
}
