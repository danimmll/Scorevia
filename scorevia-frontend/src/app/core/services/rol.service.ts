import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable } from 'rxjs';
import { Pagina } from '../models/pagina.model';
import { Rol, UsuarioRol } from '../models/usuario.model';

@Injectable({ providedIn: 'root' })
export class RolService {
  private http = inject(HttpClient);
  private apiUrlRoles = `${environment.apiUrl}/api/v1/roles`;
  private apiUrlUsuarios = `${environment.apiUrl}/api/v1/usuarios`;

  getListado(): Observable<Rol[]> {
    return this.http.get<Rol[]>(this.apiUrlRoles);
  }

  getPorId(id: number): Observable<Rol> {
    return this.http.get<Rol>(`${this.apiUrlRoles}/${id}`);
  }

  crear(rol: Rol): Observable<Rol> {
    return this.http.post<Rol>(this.apiUrlRoles, rol);
  }

  actualizar(id: number, rol: Rol): Observable<Rol> {
    return this.http.put<Rol>(`${this.apiUrlRoles}/${id}`, rol);
  }

  eliminar(id: number): Observable<Rol> {
    return this.http.delete<Rol>(`${this.apiUrlRoles}/${id}`);
  }

  getUsuariosDeRol(idRol: number): Observable<UsuarioRol[]> {
    return this.http.get<UsuarioRol[]>(`${this.apiUrlRoles}/${idRol}/usuarios`);
  }

  asignarRol(idUsuario: number, idRol: number): Observable<UsuarioRol> {
    return this.http.post<UsuarioRol>(`${this.apiUrlUsuarios}/${idUsuario}/roles`, { idRol });
  }

  quitarRol(idUsuario: number, idRol: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrlUsuarios}/${idUsuario}/roles/${idRol}`);
  }

  getRolesDeUsuario(idUsuario: number): Observable<UsuarioRol[]> {
    return this.http.get<UsuarioRol[]>(`${this.apiUrlUsuarios}/${idUsuario}/roles`);
  }

  getListadoAdmin(page = 0, size = 20): Observable<Pagina<UsuarioRol>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Pagina<UsuarioRol>>(`${environment.apiUrl}/api/v1/usuarios-roles`, { params });
  }
}
