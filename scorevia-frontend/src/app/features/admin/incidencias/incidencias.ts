import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { IncidenciaService } from '../../../core/services/incidencia.service';
import { Incidencia } from '../../../core/models/incidencia.model';
import { PaginadorComponent } from '../../../shared/paginador/paginador';
import { TranslocoPipe } from '@jsverse/transloco';
import { ErroresService } from '../../../core/services/errores.service';

@Component({
  selector: 'app-admin-incidencias',
  standalone: true,
  imports: [CommonModule, FormsModule, PaginadorComponent, TranslocoPipe],
  templateUrl: './incidencias.html',
  styleUrls: ['./incidencias.scss']
})
export class AdminIncidenciasComponent implements OnInit {
  private errores = inject(ErroresService);
  private incidenciaService = inject(IncidenciaService);

  incidencias = signal<Incidencia[]>([]);
  pagina = signal(0);
  totalPaginas = signal(0);
  error = signal<string | null>(null);
  filtroClase = '';
  filtroUsuario = '';
  filtroMetodo = '';

  ngOnInit(): void {
    this.cargarPagina(0);
  }

  cargarPagina(page: number): void {
    // Los tres filtros son mutuamente excluyentes (mismo criterio "un solo filtro activo a
    // la vez" que ya tenía filtroClase) -- por prioridad, si hay más de uno con texto se usa
    // el primero de esta lista.
    let fuente;
    if (this.filtroClase) {
      fuente = this.incidenciaService.getPorClase(this.filtroClase, page);
    } else if (this.filtroUsuario) {
      fuente = this.incidenciaService.getPorUsuario(this.filtroUsuario, page);
    } else if (this.filtroMetodo) {
      fuente = this.incidenciaService.getPorMetodo(this.filtroMetodo, page);
    } else {
      fuente = this.incidenciaService.getListado(page);
    }

    fuente.subscribe({
      next: pag => {
        this.incidencias.set(pag.content);
        this.pagina.set(pag.number);
        this.totalPaginas.set(pag.totalPages);
      },
      error: err => this.error.set(this.errores.mensaje(err))
    });
  }

  filtrar(): void {
    this.cargarPagina(0);
  }

  limpiarFiltros(): void {
    this.filtroClase = '';
    this.filtroUsuario = '';
    this.filtroMetodo = '';
    this.cargarPagina(0);
  }
}
