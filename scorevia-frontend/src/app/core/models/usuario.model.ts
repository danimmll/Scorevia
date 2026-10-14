/** UsuarioDTO — usado para registro (POST) y como modelo de lectura. */
export interface Usuario {
  idUsuario: number | null;
  nickname: string;
  nombreUsuario: string;
  apellidoUsuario: string;
  correoUsuario: string;
  contrasenhaUsuario: string;
  activo?: boolean | null;
  urlFoto?: string | null;
  esPremium?: boolean | null;
}

/** UsuarioUpdateDTO — usado SOLO en PUT /usuarios/{id}. Password opcional, sin activo/esPremium. */
export interface UsuarioUpdate {
  idUsuario: number | null;
  nickname: string;
  nombreUsuario: string;
  apellidoUsuario: string;
  correoUsuario: string;
  contrasenhaUsuario?: string | null;
  urlFoto?: string | null;
}

/**
 * Forma del campo "usuario" que devuelven login/verificar-email/google. `/auth/me` añade
 * "nickname" a esta misma forma -- por eso es opcional aquí en vez de dos DTOs separados.
 */
export interface SesionUsuario {
  idUsuario: number;
  correoUsuario: string;
  nombreUsuario: string;
  apellidoUsuario: string;
  activo: boolean;
  roles: string[]; // ej. ["ROLE_ADMIN", "ROLE_FREE"] -- prefijo ROLE_ siempre presente
  nickname?: string;
}

export interface Rol {
  idRol: number | null;
  nombreRol: string;
  activo?: boolean | null;
}

export interface UsuarioRol {
  idUsuario: number | null;
  idRol: number | null;
  nombreUsuario?: string | null;
  activoUsuario?: boolean | null;
  nombreRol?: string | null;
  activoRol?: boolean | null;
}

export type TipoFavorito = 'equipos' | 'ligas' | 'partidos';

export interface Favorito {
  idUsuario: number;
  idReferenciaApi: number;
  fechaAgregado?: string;
}

export const ROLE_ADMIN = 'ROLE_ADMIN';
export const ROLE_PREMIUM = 'ROLE_PREMIUM';
export const ROLE_FREE = 'ROLE_FREE';
