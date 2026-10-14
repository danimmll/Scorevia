import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable } from 'rxjs';
import { Pagina } from '../models/pagina.model';
import { Incidencia } from '../models/incidencia.model';

/** Todo ADMIN, todo lectura. Histórico de errores técnicos automáticos entre microservicios. */
@Injectable({ providedIn: 'root' })
export class IncidenciaService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/api/v1/incidencias`;

  getListado(page = 0, size = 5): Observable<Pagina<Incidencia>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Pagina<Incidencia>>(this.apiUrl, { params });
  }

  getPorId(id: number): Observable<Incidencia> {
    return this.http.get<Incidencia>(`${this.apiUrl}/${id}`);
  }

  getPorUsuario(usuarioContexto: string, page = 0, size = 5): Observable<Pagina<Incidencia>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Pagina<Incidencia>>(`${this.apiUrl}/usuario/${usuarioContexto}`, { params });
  }

  getPorClase(clase: string, page = 0, size = 5): Observable<Pagina<Incidencia>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Pagina<Incidencia>>(`${this.apiUrl}/clase/${clase}`, { params });
  }

  getPorMetodo(metodo: string, page = 0, size = 5): Observable<Pagina<Incidencia>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Pagina<Incidencia>>(`${this.apiUrl}/metodo/${metodo}`, { params });
  }
}
