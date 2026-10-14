import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { PartidoService } from '../../../core/services/partido.service';
import { Partido } from '../../../core/models/estadisticas.model';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule, TranslocoPipe],
  templateUrl: './dashboard.html',
  styleUrls: ['./dashboard.scss']
})
export class DashboardComponent implements OnInit {
  private partidoService = inject(PartidoService);

  public partidos = signal<Partido[]>([]);
  // Métricas derivadas de datos reales -- no hay endpoint de cuota de API-Football/backlog
  // expuesto por el backend, así que no se inventa ese dato.
  public metrics = signal({
    totalHoy: 0,
    enVivo: 0,
    finalizados: 0
  });

  ngOnInit() {
    this.cargarDatosDashboard();
  }

  cargarDatosDashboard() {
    // getPartidosHoy devuelve ya el día entero (todas las páginas concatenadas), así que
    // las cuentas de aquí son del día completo y no de los primeros 20 partidos.
    this.partidoService.getPartidosHoy().subscribe({
      next: partidos => {
        this.partidos.set(partidos);
        this.metrics.set({
          totalHoy: partidos.length,
          enVivo: partidos.filter(p => p.estado === 'EN_JUEGO').length,
          finalizados: partidos.filter(p => p.estado === 'FINALIZADO').length
        });
      },
      error: (err) => console.error('Error cargando partidos reales:', err)
    });
  }
}