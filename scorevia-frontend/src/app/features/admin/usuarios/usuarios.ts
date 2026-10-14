import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { UsuarioService } from '../../../core/services/usuario.service';
import { Usuario } from '../../../core/models/usuario.model';
import { PaginadorComponent } from '../../../shared/paginador/paginador';
import { TranslocoPipe } from '@jsverse/transloco';
import { ErroresService } from '../../../core/services/errores.service';

@Component({
  selector: 'app-admin-usuarios',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, PaginadorComponent, TranslocoPipe],
  templateUrl: './usuarios.html',
  styleUrls: ['./usuarios.scss']
})
export class AdminUsuariosComponent implements OnInit {
  private errores = inject(ErroresService);
  private usuarioService = inject(UsuarioService);
  private fb = inject(FormBuilder);

  usuarios = signal<Usuario[]>([]);
  pagina = signal(0);
  totalPaginas = signal(0);
  error = signal<string | null>(null);
  edicionId = signal<number | null>(null);

  editarForm: FormGroup = this.fb.group({
    idUsuario: [null],
    nickname: ['', Validators.required],
    nombreUsuario: ['', Validators.required],
    apellidoUsuario: ['', Validators.required],
    correoUsuario: ['', [Validators.required, Validators.email]],
    urlFoto: ['']
  });

  ngOnInit(): void {
    this.cargarPagina(0);
  }

  cargarPagina(page: number): void {
    this.usuarioService.getListadoAdmin(page).subscribe({
      next: pag => {
        this.usuarios.set(pag.content);
        this.pagina.set(pag.number);
        this.totalPaginas.set(pag.totalPages);
      },
      error: err => this.error.set(this.errores.mensaje(err))
    });
  }

  editar(usuario: Usuario): void {
    this.edicionId.set(usuario.idUsuario);
    this.editarForm.patchValue(usuario);
  }

  cancelarEdicion(): void {
    this.edicionId.set(null);
  }

  guardar(): void {
    const id = this.edicionId();
    if (!id || this.editarForm.invalid) return;

    this.usuarioService.actualizar(id, this.editarForm.value).subscribe({
      next: () => {
        this.edicionId.set(null);
        this.cargarPagina(this.pagina());
      },
      error: err => this.error.set(this.errores.mensaje(err))
    });
  }

  darDeBaja(usuario: Usuario): void {
    if (!confirm(`¿Dar de baja a ${usuario.nickname}? Esto anonimiza su cuenta.`)) return;

    this.usuarioService.eliminar(usuario.idUsuario!).subscribe({
      next: () => this.cargarPagina(this.pagina()),
      error: err => this.error.set(this.errores.mensaje(err))
    });
  }
}
