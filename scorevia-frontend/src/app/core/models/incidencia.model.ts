/** Solo lectura, panel ADMIN. Histórico de errores técnicos automáticos. */
export interface Incidencia {
  idIncidencia: number | null;
  endpoint: string;
  tipo: string;
  clase: string;
  metodo: string;
  traza: string;
  fecha?: string | null;
  usuarioContexto?: string | null;
}
