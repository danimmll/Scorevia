import { RenderMode, ServerRoute } from '@angular/ssr';

// La app es de datos en vivo/gated por sesión -- no tiene sentido prerenderizar (build-time
// estático) casi ninguna ruta. Server = SSR por petición, sirve para todo por defecto.
export const serverRoutes: ServerRoute[] = [
  {
    path: '**',
    renderMode: RenderMode.Server,
  },
];
