import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable } from 'rxjs';
import { Pagina } from '../models/pagina.model';
import { Favorito, TipoFavorito, Usuario, UsuarioUpdate } from '../models/usuario.model';

@Injectable({ providedIn: 'root' })
export class UsuarioService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/api/v1/usuarios`;

  getPorId(id: number): Observable<Usuario> {
    return this.http.get<Usuario>(`${this.apiUrl}/${id}`);
  }

  actualizar(id: number, dto: UsuarioUpdate): Observable<Usuario> {
    return this.http.put<Usuario>(`${this.apiUrl}/${id}`, dto);
  }

  /** No borra de verdad: el backend anonimiza y desactiva la cuenta. */
  eliminar(id: number): Observable<Usuario> {
    return this.http.delete<Usuario>(`${this.apiUrl}/${id}`);
  }

  getListadoAdmin(page = 0, size = 20): Observable<Pagina<Usuario>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Pagina<Usuario>>(this.apiUrl, { params });
  }

  crearAdmin(usuario: Usuario): Observable<Usuario> {
    return this.http.post<Usuario>(this.apiUrl, usuario);
  }

  /** Endpoint real es /usuarios/exportar, no /usuarios/excel. */
  exportarExcel(): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/exportar`, { responseType: 'blob' });
  }

  getFavoritos(idUsuario: number, tipo: TipoFavorito): Observable<Favorito[]> {
    return this.http.get<Favorito[]>(`${this.apiUrl}/${idUsuario}/favoritos/${tipo}`);
  }

  addFavorito(idUsuario: number, tipo: TipoFavorito, idReferenciaApi: number): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${idUsuario}/favoritos/${tipo}/${idReferenciaApi}`, {});
  }

  quitarFavorito(idUsuario: number, tipo: TipoFavorito, idReferenciaApi: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${idUsuario}/favoritos/${tipo}/${idReferenciaApi}`);
  }
}
