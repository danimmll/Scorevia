export type EstadoReporte = 'PENDIENTE' | 'RESUELTO' | 'RECHAZADO';

export interface Reporte {
  idReporte: number | null;
  idDenunciante?: number | null; // fijado por el servidor desde el JWT, ignorado si lo manda el cliente
  idDenunciado: number;
  motivo: string;
  estado?: string | null;
  fechaReporte?: string | null;
  resolucion?: string | null;
  idAdminResolutor?: number | null;
  fechaResolucion?: string | null;
}
