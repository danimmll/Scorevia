import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { JugadorService } from '../../../../core/services/jugador.service';
import { Jugador, Trofeo, HistorialBaja, Traspaso, HistorialDorsal } from '../../../../core/models/estadisticas.model';
import { descripcionTraspaso, etiquetaTipoTraspaso } from '../../../../core/utils/traspaso.util';
import { textoFinBaja } from '../../../../core/utils/baja.util';
import { ImagenFallbackDirective } from '../../../../core/directives/imagen-fallback.directive';
import { PaginadorComponent } from '../../../../shared/paginador/paginador';
import { CargandoComponent } from '../../../../shared/cargando/cargando';
import { LineaTrofeosComponent } from '../../../../shared/linea-trofeos/linea-trofeos';
import { TranslocoPipe } from '@jsverse/transloco';
import { ErroresService } from '../../../../core/services/errores.service';

@Component({
  selector: 'app-jugador-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, ImagenFallbackDirective, PaginadorComponent, CargandoComponent, LineaTrofeosComponent, TranslocoPipe],
  templateUrl: './jugador-detail.html',
  styleUrls: ['./jugador-detail.scss']
})
export class JugadorDetailComponent implements OnInit {
  private errores = inject(ErroresService);
  // El backend guarda un código estable en `tipo` (no el texto crudo de la API, que venía con
  // redacciones distintas para la misma operación); la etiqueta visible se decide en el
  // cliente, que es donde tendrá que traducirse el día que la interfaz sea multiidioma.
  readonly etiquetaTipo = etiquetaTipoTraspaso;
  readonly descripcion = descripcionTraspaso;
  readonly finBaja = textoFinBaja;

  private route = inject(ActivatedRoute);
  private jugadorService = inject(JugadorService);

  jugador = signal<Jugador | null>(null);
  trofeos = signal<Trofeo[]>([]);
  bajas = signal<HistorialBaja[]>([]);
  traspasos = signal<Traspaso[]>([]);
  dorsales = signal<HistorialDorsal[]>([]);

  cargando = signal(true);
  error = signal<string | null>(null);

  // Listas acotadas por naturaleza (una carrera no tiene miles de lesiones/traspasos), así
  // que se paginan en el cliente en vez de tocar el backend -- ya llegan ordenadas por fecha
  // descendente (más reciente primero).
  private readonly TAMANIO_PAGINA = 5;
  paginaBajas = signal(0);
  paginaTraspasos = signal(0);

  bajasVisibles = computed(() => {
    const inicio = this.paginaBajas() * this.TAMANIO_PAGINA;
    return this.bajas().slice(inicio, inicio + this.TAMANIO_PAGINA);
  });
  totalPaginasBajas = computed(() => Math.max(1, Math.ceil(this.bajas().length / this.TAMANIO_PAGINA)));

  traspasosVisibles = computed(() => {
    const inicio = this.paginaTraspasos() * this.TAMANIO_PAGINA;
    return this.traspasos().slice(inicio, inicio + this.TAMANIO_PAGINA);
  });
  totalPaginasTraspasos = computed(() => Math.max(1, Math.ceil(this.traspasos().length / this.TAMANIO_PAGINA)));

  // Colapsa temporadas con mismo equipo+dorsal en un solo rango ("2009-2011") en vez de una
  // fila por temporada -- si no, la lista de un jugador con carrera larga se hace kilométrica
  // para no decir nada nuevo (mismo dorsal, año tras año). No exige años estrictamente
  // consecutivos (huecos por temporadas sin sincronizar no deberían partir el rango en dos).
  dorsalesAgrupados = computed(() => {
    const ordenados = [...this.dorsales()].sort((a, b) => (a.temporada ?? 0) - (b.temporada ?? 0));
    const grupos: { idEquipo: number; nombreEquipo?: string; escudoEquipo?: string; dorsal?: number; posicion?: string; desde: number; hasta: number }[] = [];

    for (const d of ordenados) {
      const ultimo = grupos[grupos.length - 1];
      const temporada = d.temporada ?? 0;
      const continuaGrupo = ultimo && ultimo.idEquipo === d.idEquipo && ultimo.dorsal === d.dorsal;

      if (continuaGrupo) {
        ultimo.hasta = temporada;
      } else {
        grupos.push({ idEquipo: d.idEquipo, nombreEquipo: d.nombreEquipo, escudoEquipo: d.escudoEquipo, dorsal: d.dorsal, posicion: d.posicion, desde: temporada, hasta: temporada });
      }
    }
    return grupos.reverse();
  });

  // El historial de dorsales guarda la posición como código de una letra (G/D/M/F, del
  // "pos" de alineaciones de la API) -- no las palabras completas que usa el resto de la
  // ficha del jugador. "F" a secas no dice nada sin contexto.
  private static readonly POSICIONES_DORSAL: Record<string, string> = {
    G: 'Portero', D: 'Defensa', M: 'Centrocampista', F: 'Delantero', SUB: 'Suplente'
  };
  etiquetaPosicionDorsal(codigo?: string): string {
    if (!codigo) return '';
    return JugadorDetailComponent.POSICIONES_DORSAL[codigo] ?? codigo;
  }

  // Agrupa "Champions League 2010, Champions League 2014..." en una sola tarjeta
  // "Champions League x5" -- la clave incluye el país: "Supercopa" de España y de Arabia son
  // trofeos DISTINTOS aunque compartan nombre, agruparlos solo por nombre los mezclaría en
  // uno solo y mentiría sobre cuántas veces se ganó cada uno.
  //
  // FIX: solo cuentan las filas donde de verdad se ganó ("Winner") -- antes se contaba
  // cualquier fila con ese competicion+país sin mirar "resultado", así que un subcampeonato
  // ("2nd Place") sumaba igual que un título real. Por eso Cristiano aparecía con "6
  // Champions" cuando ganó 5 y perdió una final (2008/09).
  private esVictoria(resultado?: string): boolean {
    return (resultado ?? '').toLowerCase().includes('winner');
  }
  trofeosAgrupados = computed(() => {
    const grupos = new Map<string, { competicion: string; pais?: string; temporadas: string[] }>();
    for (const t of this.trofeos()) {
      if (!this.esVictoria(t.resultado)) continue;
      const clave = `${t.competicion ?? 'Otro'}__${t.pais ?? ''}`;
      const existente = grupos.get(clave);
      if (existente) {
        if (t.temporada) existente.temporadas.push(t.temporada);
      } else {
        grupos.set(clave, { competicion: t.competicion ?? 'Otro', pais: t.pais, temporadas: t.temporada ? [t.temporada] : [] });
      }
    }
    return [...grupos.values()];
  });

  ngOnInit(): void {
    const idJugador = Number(this.route.snapshot.paramMap.get('idJugador'));

    if (!Number.isFinite(idJugador)) {
      this.error.set('Jugador no válido.');
      this.cargando.set(false);
      return;
    }

    this.jugadorService.getPorId(idJugador).subscribe({
      next: jugador => {
        this.jugador.set(jugador);
        this.cargando.set(false);
      },
      error: err => {
        this.error.set(this.errores.mensaje(err, 'No se pudo cargar el jugador.'));
        this.cargando.set(false);
      }
    });

    // Best-effort, igual que en entrenador-detail: si alguna de estas falla, esa sección
    // concreta simplemente queda vacía -- no tiene sentido tumbar toda la ficha del jugador
    // por un histórico que no cargó.
    this.jugadorService.getTrofeos(idJugador).subscribe({ next: t => this.trofeos.set(t), error: () => this.trofeos.set([]) });
    this.jugadorService.getBajas(idJugador).subscribe({ next: b => this.bajas.set(b), error: () => this.bajas.set([]) });
    this.jugadorService.getTraspasos(idJugador).subscribe({ next: t => this.traspasos.set(t), error: () => this.traspasos.set([]) });
    this.jugadorService.getDorsales(idJugador).subscribe({ next: d => this.dorsales.set(d), error: () => this.dorsales.set([]) });
  }
}
