import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ReporteService } from '../../../core/services/reporte.service';
import { EstadoReporte, Reporte } from '../../../core/models/reporte.model';
import { PaginadorComponent } from '../../../shared/paginador/paginador';
import { TranslocoPipe } from '@jsverse/transloco';
import { ErroresService } from '../../../core/services/errores.service';

@Component({
  selector: 'app-admin-reportes',
  standalone: true,
  imports: [CommonModule, FormsModule, PaginadorComponent, TranslocoPipe],
  templateUrl: './reportes.html',
  styleUrls: ['./reportes.scss']
})
export class AdminReportesComponent implements OnInit {
  private errores = inject(ErroresService);
  private reporteService = inject(ReporteService);

  reportes = signal<Reporte[]>([]);
  pagina = signal(0);
  totalPaginas = signal(0);
  error = signal<string | null>(null);

  resolucionTexto: Record<number, string> = {};
  resolucionEstado: Record<number, EstadoReporte> = {};

  ngOnInit(): void {
    this.cargarPagina(0);
  }

  cargarPagina(page: number): void {
    this.reporteService.getPendientesAdmin(page).subscribe({
      next: pag => {
        this.reportes.set(pag.content);
        this.pagina.set(pag.number);
        this.totalPaginas.set(pag.totalPages);
        pag.content.forEach(r => {
          if (r.idReporte != null) this.resolucionEstado[r.idReporte] = 'RESUELTO';
        });
      },
      error: err => this.error.set(this.errores.mensaje(err))
    });
  }

  resolver(reporte: Reporte): void {
    if (!reporte.idReporte) return;
    const estado = this.resolucionEstado[reporte.idReporte] ?? 'RESUELTO';
    const resolucion = this.resolucionTexto[reporte.idReporte] ?? '';

    this.reporteService.resolver(reporte.idReporte, estado, resolucion).subscribe({
      next: () => this.cargarPagina(this.pagina()),
      error: err => this.error.set(this.errores.mensaje(err))
    });
  }
}
