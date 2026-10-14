import type { Pagina } from './pagina.model';

export interface Partido {
  idPartido: number;
  liga?: string;
  // Para agrupar y ordenar por competición (ver compararCompeticiones): con el nombre
  // solo no se puede saber si algo es La Liga o la tercera regional de Rumanía.
  idLiga?: number;
  paisLiga?: string;
  logoLiga?: string;
  /**
   * Bandera del país de la competición. Hace falta porque hay muchísimas competiciones que
   * comparten nombre entre países ("Primera División", "Copa del Rey", "Premier League"),
   * y con el escudo solo no se sabe cuál se está mirando. La tienen 1.232 de las 1.244 ligas.
   */
  banderaLiga?: string;
  idEquipoLocal?: number;
  equipoLocal?: string;
  escudoLocal?: string;
  idEquipoVisitante?: number;
  equipoVisitante?: string;
  escudoVisitante?: string;
  arbitro?: string;
  fecha?: string;
  jornada?: number;
  resultadoLocal?: number | null;
  resultadoVisitante?: number | null;
  /**
   * La fase en crudo, como la manda la API: "Regular Season - 12", "Quarter-finals",
   * "Group Stage - A". En liga es redundante con `jornada`, pero en copa es lo único que hay.
   * Ver rondaLegible() en core/utils/ronda.util.ts.
   */
  ronda?: string | null;
  estado?: string; // PROGRAMADO | EN_JUEGO | FINALIZADO | APLAZADO
  /**
   * El código de estado original de API-Football: 1H, HT, 2H, ET, BT, P, FT, AET, PEN...
   * `estado` agrupa esos códigos en cuatro cajones, que es lo que necesita la lógica, pero al
   * agrupar pierde justo lo que un marcador en directo tiene que enseñar: un partido en el
   * descanso y otro en el minuto 70 son los dos EN_JUEGO. Ver etiquetaMinuto().
   */
  estadoCorto?: string | null;
  /** Minuto de juego. Solo viene relleno mientras el partido está en juego. */
  minuto?: number | null;
  /** Marcador al descanso. Null si el partido no ha llegado ahí o es de una ingesta antigua. */
  resultadoLocalDescanso?: number | null;
  resultadoVisitanteDescanso?: number | null;
  /** Tanda de penaltis. Solo en eliminatorias que llegaron a ella. */
  penaltisLocal?: number | null;
  penaltisVisitante?: number | null;
  /**
   * Qué publica API-Football de esta competición. Sirve para decir "esta competición no
   * publica estadísticas" en vez de dejar un hueco que se lee como un fallo nuestro.
   * Null = no se sabe, y entonces no se afirma nada.
   */
  coberturaStatsPartido?: boolean | null;
  coberturaStatsJugadores?: boolean | null;
  coberturaEventos?: boolean | null;
  coberturaAlineaciones?: boolean | null;
  temporada?: number;
}

export interface Estadio {
  idEstadio: number;
  nombre?: string;
  ciudad?: string;
  capacidad?: number;
  superficie?: string;
  fotoUrl?: string;
}

export interface Equipo {
  idEquipo: number;
  nombre?: string;
  codigo?: string;
  pais?: string;
  fundacion?: number;
  logo?: string;
  estadio?: Estadio;
}

export interface Traspaso {
  // Clave estable de la fila. Sin ella las listas se recorrían con la fecha (o nombre+fecha)
  // como clave de seguimiento y dos movimientos del mismo día -- que los hay -- producían
  // claves duplicadas, que en Angular 21 son un error en tiempo de ejecución.
  idTraspaso?: number;
  idJugador?: number;
  nombreJugador?: string;
  fecha?: string;
  idEquipoOrigen?: number;
  equipoOrigen?: string;
  escudoOrigen?: string;
  idEquipoDestino?: number;
  equipoDestino?: string;
  escudoDestino?: string;
  tipo?: string;
  coste?: string;
}

export interface Jugador {
  idJugador: number;
  nombre?: string;
  nombreCompleto?: string;
  edad?: number;
  fechaNacimiento?: string;
  nacionalidad?: string;
  altura?: string;
  peso?: string;
  foto?: string;
  lugarNacimiento?: string;
  posicion?: string;
  dorsal?: number;
  equipo?: Equipo;

  /**
   * Si la ficha se ha contrastado con el catálogo de la API.
   *
   * Con false pueden faltar altura, peso o pie bueno: no es que el jugador no los tenga.
   */
  perfilVerificado?: boolean | null;

  /**
   * Si ya se ha preguntado por sus lesiones y sanciones.
   *
   * Con false, una lista de bajas vacía significa "no lo hemos mirado" y no "nunca se ha
   * lesionado", que no es lo mismo y la ficha no debería afirmarlo.
   */
  bajasConsultadas?: boolean | null;
}

export interface Entrenador {
  idEntrenador: number;
  nombre?: string;
  edad?: number;
  nacionalidad?: string;
  foto?: string;
  equipo?: Equipo;

  /** Si ya se ha preguntado por su palmarés. Con false, "sin títulos" es "sin mirar". */
  palmaresConsultado?: boolean | null;
}

export interface Trofeo {
  idTrofeo: number;
  idJugador?: number | null;
  idEntrenador?: number | null;
  competicion?: string;
  pais?: string;
  temporada?: string;
  resultado?: string;
  /** Bandera del país del trofeo. "Super Cup" a secas no dice de dónde es. */
  banderaPais?: string | null;
}

export interface HistorialBaja {
  idBaja: number;
  idJugador: number;
  tipo?: string;
  causa?: string;
  fechaInicio?: string;
  fechaFin?: string;
  activo?: boolean;
}

export interface HistorialEntrenador {
  idEntrenador?: number;
  nombreEntrenador?: string;
  fotoEntrenador?: string;
  idEquipo?: number;
  nombreEquipo?: string;
  escudoEquipo?: string;
  temporada?: number;
}

export interface HistorialDorsal {
  idEquipo: number;
  nombreEquipo?: string;
  escudoEquipo?: string;
  temporada?: number;
  dorsal?: number;
  posicion?: string;
}

export interface Liga {
  idLiga: number;
  nombre?: string;
  pais?: string;
  logo?: string;
  bandera?: string;
  temporadaActual?: number;

  /**
   * Si la competición publica clasificación.
   *
   * Solo 788 de las 1.244 competiciones de la base tienen tabla: las demás son copas,
   * amistosos y juveniles. Null es "todavía no se sabe" y no se avisa de nada.
   */
  coberturaClasificacion?: boolean | null;
}

/**
 * Una temporada disponible de una liga (GET /api/v1/ligas/{id}/temporadas).
 *
 * `conDatos` es false cuando la clasificación existe pero nadie ha jugado todavía: la API
 * publica la tabla de la temporada siguiente en cuanto hay calendario, con todo a cero.
 */
export interface TemporadaLiga {
  temporada: number;
  conDatos: boolean;
}

export interface Clasificacion {
  posicion: number;
  puntos: number;
  partidosJugados?: number;
  partidosGanados?: number;
  partidosEmpatados?: number;
  partidosPerdidos?: number;
  golesFavor?: number;
  golesContra?: number;
  diferenciaGoles?: number;
  estadoCompeticion?: string;
  /** Grupo dentro de la competición ("Group A"...). Null en una liga de tabla única. */
  grupo?: string;
  equipo?: Equipo;
  logoEquipo?: string;
  // "V"/"E"/"D" de los últimos partidos finalizados, más antiguo primero (el más reciente
  // queda el último del array) -- mismo orden que devuelve el backend.
  forma?: string[];
}

export interface EstadisticaEquipo {
  idPartido: number;
  idEquipo: number;
  nombreEquipo?: string;
  posesion?: number;
  tiros?: number;
  tirosPuerta?: number;
  corners?: number;
  faltas?: number;
  tarjetasAmarillas?: number;
  tarjetasRojas?: number;
  golesEsperados?: number;
  sistemaTactico?: string;
  estadisticasExtra?: Record<string, string>;
}

export interface EstadisticaJugadorPartido {
  id: number;
  idEquipo: number;
  idJugador?: number;
  foto?: string;
  nombreJugador?: string;
  posicion?: string;
  titular?: boolean;
  minutosJugados?: number;
  goles?: number;
  asistencias?: number;
  tarjetasAmarillas?: number;
  tarjetasRojas?: number;
  puntuacion?: string;
  posicionCampo?: string;
}

export interface EventoPartido {
  idEvento: number;
  tiempoMinuto?: number;
  tiempoExtra?: number;
  tipoEvento?: string;
  detalle?: string;
  nombreEquipo?: string;
  logoEquipo?: string;
  nombreJugadorPrincipal?: string;
  nombreJugadorSecundario?: string;
}

/** Todo lo que se pide para pintar una página de detalle de partido de una vez. */
export interface Arbitro {
  idArbitro: number;
  nombre?: string;
  pais?: string;
}

/**
 * Ficha de árbitro. No hay foto ni biografía porque API-Football no da al árbitro como
 * entidad propia (llega como texto dentro del fixture); lo que sí se puede contar es cuánto
 * ha arbitrado y con qué mano va de tarjetas.
 */
export interface ArbitroDetalle extends Arbitro {
  partidosDirigidos: number;
  tarjetasAmarillas: number;
  tarjetasRojas: number;
  /** Null cuando no consta ni un partido suyo con eventos: no hay media que dar. */
  mediaAmarillas?: number | null;
  partidosConEventos: number;
}

export interface JugadorAlineacion {
  idJugador?: number;
  nombre?: string;
  foto?: string;
  dorsal?: number;
  /** Línea en ESTE partido: G, D, M o F. */
  linea?: string;
  /**
   * Sitio exacto en el campo, "fila:columna", de la alineación oficial. Null cuando la
   * competición no publica alineaciones: entonces se reparte por línea y ya está.
   */
  grid?: string;
  posicionNatural?: string;
  minutosJugados?: number;
  goles?: number;
  asistencias?: number;
  tarjetasAmarillas?: number;
  tarjetasRojas?: number;
  valoracion?: number;
}

export interface EquipoAlineacion {
  idEquipo?: number;
  nombre?: string;
  escudo?: string;
  /** "4-3-3". Null si de este partido no constan titulares con posición. */
  formacion?: string | null;
  /** Hoy siempre true: sale de contar los titulares por línea, no de la API. */
  formacionDeducida?: boolean;
  entrenador?: Entrenador;
  /** true si es el entrenador ACTUAL del club, no el de la temporada del partido. */
  entrenadorEsElActual?: boolean;
  titulares: JugadorAlineacion[];
  suplentes: JugadorAlineacion[];
}

export interface AlineacionesPartido {
  idPartido: number;
  arbitro?: Arbitro;
  local?: EquipoAlineacion;
  visitante?: EquipoAlineacion;
  /** false si el partido aún no tiene ningún jugador registrado. */
  hayDatos: boolean;
}

export interface PartidoDetalleCompleto {
  info: Partido;
  statsEquipos: EstadisticaEquipo[];
  statsJugadores: EstadisticaJugadorPartido[];
  eventos: EventoPartido[];
  h2h: Pagina<Partido>;
  alineaciones: AlineacionesPartido;
}

/** Una opción de un mercado de apuestas con su cuota. */
export interface OpcionCuota {
  etiqueta: string;
  cuota: string;
}

export interface MercadoCuotas {
  clave: string;
  nombre: string;
  opciones: OpcionCuota[];
}

/**
 * Cuotas de una casa para un partido.
 *
 * Vienen como lista de mercados y no con un campo por cuota para que añadir un mercado más
 * no obligue a tocar el modelo ni la pantalla.
 */
export interface CuotasPartido {
  idPartido: number;
  casa?: string;
  actualizado?: string;
  /** false cuando no hay cuotas: competición sin cobertura, o partido fuera de la ventana. */
  hayCuotas: boolean;
  mercados: MercadoCuotas[];
}

/** Un jugador que se pierde el partido (o que es duda). */
export interface BajaPartido {
  idJugador?: number;
  nombre?: string;
  foto?: string;
  idEquipo?: number;
  nombreEquipo?: string;
  /** true si no juega seguro; false si es duda. */
  baja: boolean;
  motivo?: string;
}

/** Una fila de un ranking de liga (goleadores, asistentes, tarjetas). */
export interface RankingJugador {
  puesto: number;
  idJugador?: number;
  nombre?: string;
  foto?: string;
  idEquipo?: number;
  nombreEquipo?: string;
  escudoEquipo?: string;
  valor?: number;
  partidos?: number;
  minutos?: number;
}

export type TipoRanking = 'GOLES' | 'ASISTENCIAS' | 'AMARILLAS' | 'ROJAS';

/** La temporada entera de un equipo: quién la jugó, quién la dirigió y cómo acabó. */
export interface TemporadaEquipo {
  idEquipo?: number;
  equipo?: string;
  escudo?: string;
  temporada?: number;
  entrenadores?: EntrenadorTemporada[];
  competiciones?: CompeticionTemporada[];
  /** Las competiciones que ganó: puesto 1 en la clasificación final. */
  titulos?: CompeticionTemporada[];
  plantilla?: JugadorTemporada[];
  totales?: TotalesTemporada;
}

export interface EntrenadorTemporada {
  idEntrenador?: number;
  nombre?: string;
  foto?: string;
  /**
   * La temporada del registro del que sale, que no siempre es la que se está mirando.
   *
   * El historial guarda la temporada en que EMPIEZA cada etapa, no todas las que cubre: al
   * pedir 2024 del Real Madrid la respuesta viene de la etapa que arrancó en 2021. Por eso se
   * enseña "desde 2021" en vez de dar a entender que consta ese año exacto.
   */
  temporadaRegistro?: number;
}

export interface CompeticionTemporada {
  idLiga?: number;
  nombre?: string;
  logo?: string;
  bandera?: string;
  posicion?: number;
  puntos?: number;
  partidosJugados?: number;
  /** El grupo, en las competiciones que los tienen. Null en una liga normal. */
  grupo?: string | null;
}

export interface JugadorTemporada {
  idJugador?: number;
  nombre?: string;
  foto?: string;
  posicion?: string;
  partidos?: number;
  titularidades?: number;
  minutos?: number;
  goles?: number;
  asistencias?: number;
  amarillas?: number;
  rojas?: number;
  valoracionMedia?: number | null;
}

export interface TotalesTemporada {
  jugadoresUtilizados?: number;
  goles?: number;
  asistencias?: number;
  amarillas?: number;
  rojas?: number;
}
