import type { Traspaso } from '../models/estadisticas.model';

/**
 * Etiquetas visibles para el tipo de traspaso.
 *
 * La API-Football mete en un mismo campo la categoría de la operación ("Loan", "Free agent",
 * "N/A") y a veces el importe ("€ 55M"), con la caja y la redacción cambiando entre
 * respuestas. Eso hacía que la MISMA operación quedara guardada con etiquetas distintas y no
 * coincidiera en la comprobación de duplicados, así que el backend pasó a guardar un código
 * estable y el texto visible se decide aquí.
 *
 * Desde el 08/09/2026 lo que se devuelve es la CLAVE de traduccion, no el texto: la
 * plantilla la pasa por `| transloco`. Sigue habiendo un unico sitio donde se decide
 * como se llama cada tipo, pero ahora ese sitio tiene las dos versiones.
 */
const ETIQUETAS_TIPO: Record<string, string> = {
  TRANSFER: 'transfer_type_transfer',
  LOAN: 'transfer_type_loan',
  LOAN_RETURN: 'transfer_type_loan_return',
  FREE: 'transfer_type_free',
  SWAP: 'transfer_type_swap',
  PROMOTION: 'transfer_type_promotion',
  RETIRED: 'transfer_type_retired',
  UNKNOWN: 'transfer_type_unknown',
};

export function etiquetaTipoTraspaso(tipo: string | undefined | null): string {
  if (!tipo) return ETIQUETAS_TIPO['UNKNOWN'];
  // Los traspasos guardados antes de normalizar el catálogo siguen teniendo el texto crudo de
  // la API; enseñarlo tal cual es mejor que enseñar "Sin detalle" para todos ellos.
  return ETIQUETAS_TIPO[tipo] ?? tipo;
}

/**
 * Descripción del movimiento en una línea: "Chelsea → Real Madrid", "Llega a Getafe" cuando
 * solo se conoce un extremo, o el club a secas cuando la operación entra y sale del mismo.
 *
 * Que origen y destino sean el mismo club no siempre significa lo mismo. En un `PROMOTION` sí
 * es un ascenso desde el filial y así se dice. En los otros 10.829 casos medidos —`UNKNOWN`,
 * `FREE`, `LOAN`, `TRANSFER`— es una rareza de la API, y llamarlos a todos "ascenso del
 * filial" era inventarse una explicación: se enseña el club y que el tipo diga lo que sepa.
 */
export interface DescripcionTraspaso {
  clave: string;
  params?: Record<string, unknown>;
}

export function descripcionTraspaso(traspaso: Traspaso): DescripcionTraspaso {
  const origen = traspaso.equipoOrigen?.trim();
  const destino = traspaso.equipoDestino?.trim();

  if (origen && destino && origen === destino) {
    return traspaso.tipo === 'PROMOTION'
      ? { clave: 'transfer_promoted_from_academy', params: { club: origen } }
      : { clave: 'transfer_same_club', params: { club: origen } };
  }
  if (origen && destino) return { clave: 'transfer_from_to', params: { origen, destino } };
  if (destino) return { clave: 'transfer_joins', params: { club: destino } };
  if (origen) return { clave: 'transfer_leaves', params: { club: origen } };
  return { clave: 'transfer_no_teams' };
}
