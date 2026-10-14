/** Forma exacta de un Page<T> de Spring Data, tal cual lo serializan los 5 microservicios. */
export interface Pagina<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  empty: boolean;
  numberOfElements: number;
}
