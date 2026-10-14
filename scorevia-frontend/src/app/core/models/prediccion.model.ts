/** Respuesta de GET /predicciones/{idPartido} (pre-partido). */
export interface ProbabilidadesPrediccion {
  victoriaLocal: number;
  empate: number;
  victoriaVisitante: number;
}

/** Respuesta de GET /predicciones/{idPartido}/en-vivo. */
export interface LivePrediction {
  idPartido: number;
  minutoActual: number;
  golesLocalActuales: number;
  golesVisitanteActuales: number;
  probabilidades: ProbabilidadesPrediccion;
  lambdaLocalRestante: number;
  lambdaVisitanteRestante: number;
}
