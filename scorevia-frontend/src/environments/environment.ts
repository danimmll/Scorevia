// El environment de PRODUCCIÓN de verdad: `ng build` (config "production" por defecto) NO
// tiene fileReplacements hacia este archivo -- solo la config "development" lo sustituye por
// environment.development.ts (ver angular.json).
//
// apiUrl VACÍO a propósito: las peticiones salen relativas (`/api/v1/...`) y las resuelve
// quien esté delante:
//
//   · En producción, Caddy: enruta /api/v1/* y /v3/api-docs/* al Gateway y todo lo
//     demás al SSR (ver scorevia-backend/Caddyfile).
//   · En local con docker compose, el propio servidor SSR: reenvía /api/** a API_URL_INTERNAL
//     (ver src/server.ts).
//   · En SSR, el interceptor ssr-api-url-interceptor.ts las convierte en absolutas contra
//     API_URL_INTERNAL, porque dentro del contenedor no hay origen desde el que resolver una
//     ruta relativa.
//
// Antes esto era 'http://localhost:8085' y se compilaba DENTRO del bundle, así que un
// visitante de scorevia.net habría pedido los datos a su propio localhost. El fichero ya
// avisaba de ello en un comentario; ahora está resuelto.
//
// Para hablar con un backend en otra máquina (p. ej. el front en local contra el VPS), usa la
// config de desarrollo y pon ahí la URL absoluta.
export const environment = {
  production: true,
  apiUrl: ''
};
