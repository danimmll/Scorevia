import {
  AngularNodeAppEngine,
  createNodeRequestHandler,
  isMainModule,
  writeResponseToNodeResponse,
} from '@angular/ssr/node';
import express from 'express';
import { join } from 'node:path';

const browserDistFolder = join(import.meta.dirname, '../browser');

const app = express();

/**
 * Hosts que este servidor acepta en la cabecera Host.
 *
 * Angular SSR rechaza por defecto cualquier Host que no reconozca (protección contra SSRF) y,
 * cuando lo rechaza, NO renderiza: cae a renderizado en cliente y avisa de que en una versión
 * mayor futura pasará a ser un 400 seco. En este despliegue eso ocurría en TODAS las
 * peticiones ("Header host with value localhost:4000 is not allowed"), así que el SSR estaba
 * apagado de hecho sin que se notara -- la página llegaba vacía y se pintaba entera en el
 * navegador, justo lo contrario de lo que se buscaba al montarlo.
 *
 * La comprobación es sobre el HOSTNAME, sin puerto: da igual que en local se sirva en el 4000
 * (contenedor Node) o en el 4200 ("ng serve"). Para un despliegue real, la variable de entorno
 * NG_ALLOWED_HOSTS -- que Angular lee por su cuenta -- tiene prioridad sobre esta lista y es
 * donde va el dominio público.
 */
const HOSTS_LOCALES = ['localhost', '127.0.0.1'];

const angularApp = new AngularNodeAppEngine({ allowedHosts: HOSTS_LOCALES });

/**
 * Reenvia las llamadas a la API hacia el Gateway.
 *
 * <h2>Por que existe</h2>
 * Desde que `environment.apiUrl` es relativo, el NAVEGADOR pide `/api/v1/...` al mismo sitio
 * del que se descargo la pagina. Quien resuelve esa ruta depende de donde este corriendo:
 *
 * - **En produccion, Caddy.** Enruta `/api/v1/*` y `/v3/api-docs/*` al Gateway y
 *   todo lo demas aqui, asi que este reenvio NO llega a ejecutarse nunca.
 * - **En local con `docker compose`** (este servidor en el 4000, sin Caddy delante) si: sin
 *   el, el navegador pediria `localhost:4000/api/v1/...` y se encontraria el index.html de
 *   Angular en vez de datos.
 *
 * Antes esto funcionaba porque el apiUrl absoluto mandaba al navegador directo al 8085. El
 * efecto secundario bueno de hacerlo asi: en local ya no hay peticion entre origenes
 * distintos, o sea ni CORS ni cookies de tercera parte.
 *
 * Si `API_URL_INTERNAL` no esta definida se deja pasar (`next()`) y la peticion acaba en el
 * render de Angular, que es lo que ocurre con `ng serve`, donde el apiUrl si es absoluto.
 */
const API_INTERNA = process.env['API_URL_INTERNAL'];
const CABECERAS_QUE_NO_SE_REENVIAN = new Set(['host', 'connection', 'content-length']);

app.use('/api', async (req, res, next) => {
  if (!API_INTERNA) {
    next();
    return;
  }

  try {
    const cabeceras = new Headers();
    for (const [nombre, valor] of Object.entries(req.headers)) {
      if (CABECERAS_QUE_NO_SE_REENVIAN.has(nombre) || valor === undefined) continue;
      cabeceras.set(nombre, Array.isArray(valor) ? valor.join(', ') : valor);
    }

    // GET y HEAD no llevan cuerpo; en los demas el cuerpo aun no se ha leido (no hay body
    // parser montado), asi que se pasa el propio stream. `duplex: 'half'` es obligatorio en
    // fetch cuando el cuerpo es un stream.
    const llevaCuerpo = req.method !== 'GET' && req.method !== 'HEAD';

    const respuesta = await fetch(API_INTERNA + req.originalUrl, {
      method: req.method,
      headers: cabeceras,
      body: llevaCuerpo ? (req as unknown as ReadableStream) : undefined,
      duplex: llevaCuerpo ? 'half' : undefined,
      redirect: 'manual',
    } as RequestInit);

    res.status(respuesta.status);
    respuesta.headers.forEach((valor, nombre) => {
      // El cuerpo se reenvia ya decodificado y de una pieza, asi que estas dos cabeceras del
      // origen mentirian sobre lo que va a recibir el navegador.
      if (nombre === 'content-encoding' || nombre === 'transfer-encoding' || nombre === 'set-cookie') return;
      res.setHeader(nombre, valor);
    });

    // Las cookies van aparte: `forEach` junta varias Set-Cookie en una sola cadena separada
    // por comas, que el navegador lee como UNA cookie con basura dentro. getSetCookie() las
    // devuelve como lo que son, una lista.
    const cookies = respuesta.headers.getSetCookie?.() ?? [];
    if (cookies.length > 0) res.setHeader('set-cookie', cookies);

    res.end(Buffer.from(await respuesta.arrayBuffer()));
  } catch (error) {
    next(error);
  }
});


/**
 * Serve static files from /browser
 */
app.use(
  express.static(browserDistFolder, {
    maxAge: '1y',
    index: false,
    redirect: false,
  }),
);

/**
 * Handle all other requests by rendering the Angular application.
 */
app.use((req, res, next) => {
  angularApp
    .handle(req)
    .then((response) => (response ? writeResponseToNodeResponse(response, res) : next()))
    .catch(next);
});

/**
 * Start the server if this module is the main entry point, or it is ran via PM2.
 * The server listens on the port defined by the `PORT` environment variable, or defaults to 4000.
 */
if (isMainModule(import.meta.url) || process.env['pm_id']) {
  const port = process.env['PORT'] || 4000;
  app.listen(port, (error) => {
    if (error) {
      throw error;
    }

    console.log(`Node Express server listening on http://localhost:${port}`);
  });
}

/**
 * Request handler used by the Angular CLI (for dev-server and during build) or Firebase Cloud Functions.
 */
export const reqHandler = createNodeRequestHandler(app);
