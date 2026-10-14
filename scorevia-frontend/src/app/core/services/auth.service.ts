import { Injectable, signal, computed, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable, of, throwError } from 'rxjs';
import { catchError, finalize, map, shareReplay, switchMap, tap } from 'rxjs/operators';
import { ROLE_ADMIN, SesionUsuario, Usuario } from '../models/usuario.model';

interface RespuestaSesion {
  message?: string;
  accessToken: string;
  usuario: SesionUsuario;
}

interface RespuestaMe {
  usuario: SesionUsuario;
}

interface RespuestaRefresh {
  accessToken: string;
}

interface RespuestaRegistro {
  usuario: Usuario;
  message: string;
}

interface RespuestaMensaje {
  message: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/api/v1/auth`;

  /**
   * Access token JWT, en memoria únicamente (nunca localStorage): vive 15 minutos y el
   * refresh token que lo renueva ya está protegido en una cookie httpOnly -- no hace falta
   * ni conviene persistir este en el navegador.
   */
  accessToken = signal<string | null>(null);
  currentUser = signal<SesionUsuario | null>(null);
  esAdmin = computed(() => this.currentUser()?.roles?.includes(ROLE_ADMIN) ?? false);

  /**
   * Un solo refresh en vuelo compartido entre todas las peticiones concurrentes que lo
   * necesiten. El backend rota el refresh token con detección de reuso: si dos llamadas a
   * /auth/refresh usan a la vez el mismo token viejo, la segunda revoca TODAS las sesiones
   * del usuario -- deduplicar aquí no es un detalle de estilo, evita desloguearnos a
   * nosotros mismos en cuanto haya dos peticiones 401 a la vez.
   */
  private refreshEnCurso$: Observable<string> | null = null;

  login(correoUsuario: string, contrasenhaUsuario: string): Observable<RespuestaSesion> {
    return this.http.post<RespuestaSesion>(`${this.apiUrl}/login`, { correoUsuario, contrasenhaUsuario }).pipe(
      tap(res => this.guardarSesion(res))
    );
  }

  register(usuario: Pick<Usuario, 'nickname' | 'nombreUsuario' | 'apellidoUsuario' | 'correoUsuario' | 'contrasenhaUsuario'>): Observable<RespuestaRegistro> {
    return this.http.post<RespuestaRegistro>(`${this.apiUrl}/register`, usuario);
  }

  verifyEmail(correoUsuario: string, codigo: string): Observable<RespuestaSesion> {
    return this.http.post<RespuestaSesion>(`${this.apiUrl}/verificar-email`, { correoUsuario, codigo }).pipe(
      tap(res => this.guardarSesion(res))
    );
  }

  loginConGoogle(token: string): Observable<RespuestaSesion> {
    return this.http.post<RespuestaSesion>(`${this.apiUrl}/google`, { token }).pipe(
      tap(res => this.guardarSesion(res))
    );
  }

  solicitarResetPassword(correoUsuario: string): Observable<RespuestaMensaje> {
    return this.http.post<RespuestaMensaje>(`${this.apiUrl}/solicitar-reset-password`, { correoUsuario });
  }

  resetPassword(correoUsuario: string, codigo: string, nuevaContrasenha: string): Observable<RespuestaMensaje> {
    return this.http.post<RespuestaMensaje>(`${this.apiUrl}/reset-password`, { correoUsuario, codigo, nuevaContrasenha });
  }

  /** Usado por el interceptor en un 401, y al arrancar la app si no hay access token en memoria. */
  refresh(): Observable<string> {
    if (!this.refreshEnCurso$) {
      this.refreshEnCurso$ = this.http.post<RespuestaRefresh>(`${this.apiUrl}/refresh`, {}).pipe(
        tap(res => this.accessToken.set(res.accessToken)),
        map(res => res.accessToken),
        shareReplay(1),
        finalize(() => { this.refreshEnCurso$ = null; })
      );
    }
    return this.refreshEnCurso$;
  }

  /**
   * Usado por los guards. Si ya tenemos access token en memoria, solo confirma con /auth/me.
   * Si no (recarga de página: el signal se pierde, la cookie httpOnly no), intenta primero un
   * refresh silencioso antes de preguntar -- si el refresh falla, no hay sesión que rehidratar.
   */
  checkAuthStatus(): Observable<SesionUsuario> {
    const token = this.accessToken();
    const arrancar$: Observable<unknown> = token ? of(token) : this.refresh();

    return arrancar$.pipe(
      switchMap(() => this.http.get<RespuestaMe>(`${this.apiUrl}/me`)),
      map(res => res.usuario),
      tap(usuario => this.currentUser.set(usuario)),
      catchError(err => {
        this.limpiarSesion();
        return throwError(() => err);
      })
    );
  }

  logout(): Observable<RespuestaMensaje> {
    return this.http.post<RespuestaMensaje>(`${this.apiUrl}/logout`, {}).pipe(
      tap(() => this.limpiarSesion()),
      // El objetivo real del botón "cerrar sesión" es salir localmente -- si el POST falla
      // (red, backend caído, o el access token ya venció), no tiene sentido dejar al usuario
      // "atascado" logueado solo porque el servidor no pudo confirmar la revocación del
      // refresh token. Se limpia la sesión igual y se deja pasar como si hubiera funcionado.
      catchError(() => {
        this.limpiarSesion();
        return of<RespuestaMensaje>({ message: 'Sesión cerrada localmente' });
      })
    );
  }

  limpiarSesion(): void {
    this.accessToken.set(null);
    this.currentUser.set(null);
  }

  private guardarSesion(res: RespuestaSesion): void {
    this.accessToken.set(res.accessToken);
    this.currentUser.set(res.usuario);
  }
}
