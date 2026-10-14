import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SyncService } from '../../../core/services/sync.service';
import { TranslocoPipe } from '@jsverse/transloco';
import { ErroresService } from '../../../core/services/errores.service';

@Component({
  selector: 'app-admin-sync',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslocoPipe],
  templateUrl: './sync.html',
  styleUrls: ['./sync.scss']
})
export class AdminSyncComponent {
  private errores = inject(ErroresService);
  private syncService = inject(SyncService);

  resultado = signal<string | null>(null);
  error = signal<string | null>(null);

  fecha = '';
  idLigaEstadios = 0;
  temporadaEstadios = 0;
  idLigaClasificaciones = 0;
  temporadaClasificaciones = 0;
  temporadaLigas = 0;
  temporadaJugadores = 0;

  private ejecutar(fuente: import('rxjs').Observable<string | void>, etiqueta: string): void {
    this.error.set(null);
    this.resultado.set(null);
    fuente.subscribe({
      next: (texto) => this.resultado.set(typeof texto === 'string' ? texto : `${etiqueta}: disparado.`),
      error: err => this.error.set(this.errores.mensaje(err))
    });
  }

  sync(): void { this.ejecutar(this.syncService.sync(this.fecha), 'Sync por fecha'); }
  statsSync(): void { this.ejecutar(this.syncService.statsSync(), 'Stats sync'); }
  syncTrofeosBajas(): void { this.ejecutar(this.syncService.syncTrofeosBajas(), 'Trofeos/bajas'); }
  syncTraspasos(): void { this.ejecutar(this.syncService.syncTraspasos(), 'Traspasos'); }
  equiposEstadios(): void { this.ejecutar(this.syncService.equiposEstadios(this.idLigaEstadios, this.temporadaEstadios), 'Equipos/estadios'); }
  entrenadores(): void { this.ejecutar(this.syncService.entrenadores(), 'Entrenadores'); }
  clasificaciones(): void { this.ejecutar(this.syncService.clasificaciones(this.idLigaClasificaciones, this.temporadaClasificaciones), 'Clasificaciones'); }
  ligas(): void { this.ejecutar(this.syncService.ligas(this.temporadaLigas), 'Ligas'); }
  jugadoresCompletos(): void { this.ejecutar(this.syncService.jugadoresCompletos(this.temporadaJugadores), 'Jugadores completos'); }
  perfilesJugadores(): void { this.ejecutar(this.syncService.perfilesJugadores(), 'Perfiles de jugadores'); }
  syncEnVivo(): void { this.ejecutar(this.syncService.syncEnVivo(), 'Sync en vivo'); }
  eventosHistoricos(): void { this.ejecutar(this.syncService.eventosHistoricos(), 'Eventos históricos'); }
}
