import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth-guard';
import { publicGuard } from './core/guards/public-guard';
import { adminGuard } from './core/guards/admin-guard';

export const routes: Routes = [
  // Rutas de Autenticación (Sin el layout principal)
  {
    path: 'login',
    canActivate: [publicGuard],
    loadComponent: () => import('./features/auth/login/login').then(m => m.LoginComponent)
  },
  {
    path: 'register',
    canActivate: [publicGuard],
    loadComponent: () => import('./features/auth/register/register').then(m => m.RegisterComponent)
  },
  {
    path: 'verify',
    canActivate: [publicGuard],
    loadComponent: () => import('./features/auth/verify/verify').then(m => m.VerifyComponent)
  },
  {
    path: 'forgot-password',
    canActivate: [publicGuard],
    loadComponent: () => import('./features/auth/forgot-password/forgot-password').then(m => m.ForgotPasswordComponent)
  },
  {
    path: 'reset-password',
    canActivate: [publicGuard],
    loadComponent: () => import('./features/auth/reset-password/reset-password').then(m => m.ResetPasswordComponent)
  },

  // Layout Principal
  {
    path: '',
    loadComponent: () => import('./layout/main-layout/main-layout').then(m => m.MainLayoutComponent),
    children: [
      {
        // La raiz va directa al marcador. La home existia (favoritos, en vivo, un par de
        // juegos, ultimos partidos) pero es lo que menos se mira: quien entra a un sitio de
        // resultados quiere los resultados, no un indice de secciones.
        path: '',
        pathMatch: 'full',
        redirectTo: 'partidos'
      },
      {
        path: 'partidos',
        loadComponent: () => import('./features/partidos/partidos-listado/partidos-listado').then(m => m.PartidosListadoComponent)
      },
      {
        path: 'partidos/comparador',
        loadComponent: () => import('./features/partidos/comparador/comparador').then(m => m.ComparadorComponent)
      },
      {
        path: 'partidos/:idPartido',
        loadComponent: () => import('./features/partidos/partido-detail/partido-detail').then(m => m.PartidoDetailComponent)
      },
      {
        path: 'estadisticas/equipos',
        loadComponent: () => import('./features/estadisticas/equipos/equipos').then(m => m.EquiposComponent)
      },
      {
        path: 'estadisticas/equipos/:idEquipo',
        loadComponent: () => import('./features/estadisticas/equipos/equipo-detail/equipo-detail').then(m => m.EquipoDetailComponent)
      },
      {
        path: 'estadisticas/jugadores',
        loadComponent: () => import('./features/estadisticas/jugadores/jugadores').then(m => m.JugadoresComponent)
      },
      {
        path: 'estadisticas/jugadores/:idJugador',
        loadComponent: () => import('./features/estadisticas/jugadores/jugador-detail/jugador-detail').then(m => m.JugadorDetailComponent)
      },
      {
        // La temporada de un equipo: plantilla, entrenadores, competiciones y titulos de ese
        // año. Va con la temporada en la ruta y no en un query param para que sea compartible
        // y para que el boton de atras haga lo que se espera.
        path: 'estadisticas/equipos/:id/temporada/:temporada',
        loadComponent: () => import('./features/estadisticas/equipos/temporada-equipo/temporada-equipo').then(m => m.TemporadaEquipoComponent)
      },
      {
        path: 'estadisticas/clasificacion',
        loadComponent: () => import('./features/estadisticas/ligas-clasificacion/ligas-clasificacion').then(m => m.LigasClasificacionComponent)
      },
      {
        path: 'estadisticas/entrenadores',
        loadComponent: () => import('./features/estadisticas/entrenadores/entrenadores').then(m => m.EntrenadoresComponent)
      },
      {
        // El árbitro no tiene listado propio (API-Football no lo da como entidad, solo
        // como texto dentro del fixture), así que solo hay ficha: se llega a ella desde
        // el detalle de un partido.
        path: 'estadisticas/arbitros/:idArbitro',
        loadComponent: () => import('./features/estadisticas/arbitros/arbitro-detail/arbitro-detail').then(m => m.ArbitroDetailComponent)
      },
      {
        path: 'estadisticas/entrenadores/:idEntrenador',
        loadComponent: () => import('./features/estadisticas/entrenadores/entrenador-detail/entrenador-detail').then(m => m.EntrenadorDetailComponent)
      },

      // RUTAS PRIVADAS (usuario logueado)
      {
        path: 'perfil',
        canActivate: [authGuard],
        loadComponent: () => import('./features/usuario/perfil/perfil').then(m => m.PerfilComponent)
      },
      {
        path: 'favoritos',
        canActivate: [authGuard],
        loadComponent: () => import('./features/usuario/favoritos/favoritos').then(m => m.FavoritosComponent)
      },

      // Panel de administración (ADMIN)
      {
        path: 'admin/dashboard',
        canActivate: [authGuard, adminGuard],
        loadComponent: () => import('./features/admin/dashboard/dashboard').then(m => m.DashboardComponent)
      },
      {
        path: 'admin/usuarios',
        canActivate: [authGuard, adminGuard],
        loadComponent: () => import('./features/admin/usuarios/usuarios').then(m => m.AdminUsuariosComponent)
      },
      {
        path: 'admin/roles',
        canActivate: [authGuard, adminGuard],
        loadComponent: () => import('./features/admin/roles/roles').then(m => m.AdminRolesComponent)
      },
      {
        path: 'admin/reportes',
        canActivate: [authGuard, adminGuard],
        loadComponent: () => import('./features/admin/reportes/reportes').then(m => m.AdminReportesComponent)
      },
      {
        path: 'admin/incidencias',
        canActivate: [authGuard, adminGuard],
        loadComponent: () => import('./features/admin/incidencias/incidencias').then(m => m.AdminIncidenciasComponent)
      },
      {
        path: 'admin/sync',
        canActivate: [authGuard, adminGuard],
        loadComponent: () => import('./features/admin/sync/sync').then(m => m.AdminSyncComponent)
      },
      {
        // Aqui se aterriza a proposito: `router.navigate(['/error'], { state: {...} })`
        // desde donde haga falta, con el codigo y el mensaje ya traducido.
        path: 'error',
        loadComponent: () => import('./features/error/pagina-error').then(m => m.PaginaErrorComponent)
      },
      {
        // El comodin va DENTRO del layout, no fuera: una direccion equivocada sigue siendo
        // una pagina del sitio, y sin la barra de navegacion el unico camino de vuelta seria
        // el boton del navegador.
        //
        // Antes esto era `redirectTo: ''`, que te dejaba en el marcador sin decir nada: desde
        // fuera no habia forma de distinguir "esta pagina no existe" de "he pulsado mal".
        path: '**',
        loadComponent: () => import('./features/error/pagina-error').then(m => m.PaginaErrorComponent)
      }
    ]
  }
];
