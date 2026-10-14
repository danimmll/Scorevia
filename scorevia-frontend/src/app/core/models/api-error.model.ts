/** Forma del ProblemDetail que devuelve GlobalExceptionHandler en los 5 microservicios. */
export interface ApiError {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  tiempo?: string;
}

// El texto que ve el usuario ya no sale de aqui: lo arma ErroresService, que ademas de
// leer el `detail` sabe en que idioma esta la interfaz y traduce por el codigo `type`.
// Esta funcion devolvia el `detail` del backend tal cual, que esta escrito en espanol, asi
// que con la interfaz en ingles todo iba en ingles hasta que algo fallaba.
