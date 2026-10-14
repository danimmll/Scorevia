import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RolService } from '../../../core/services/rol.service';
import { Rol, UsuarioRol } from '../../../core/models/usuario.model';
import { TranslocoPipe } from '@jsverse/transloco';
import { ErroresService } from '../../../core/services/errores.service';

@Component({
  selector: 'app-admin-roles',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, TranslocoPipe],
  templateUrl: './roles.html',
  styleUrls: ['./roles.scss']
})
export class AdminRolesComponent implements OnInit {
  private errores = inject(ErroresService);
  private rolService = inject(RolService);
  private fb = inject(FormBuilder);

  roles = signal<Rol[]>([]);
  error = signal<string | null>(null);
  mensaje = signal<string | null>(null);

  // Resultado de "consultar" (getRolesDeUsuario/getUsuariosDeRol) -- reutilizan los mismos
  // campos idUsuario/idRol de asignacionForm, no hace falta un formulario aparte.
  consultaRolesUsuario = signal<UsuarioRol[]>([]);
  consultaUsuariosRol = signal<UsuarioRol[]>([]);

  nuevoRolForm: FormGroup = this.fb.group({
    nombreRol: ['', Validators.required]
  });

  asignacionForm: FormGroup = this.fb.group({
    idUsuario: [null, Validators.required],
    idRol: [null, Validators.required]
  });

  ngOnInit(): void {
    this.cargarRoles();
  }

  cargarRoles(): void {
    this.rolService.getListado().subscribe({
      next: roles => this.roles.set(roles),
      error: err => this.error.set(this.errores.mensaje(err))
    });
  }

  crear(): void {
    if (this.nuevoRolForm.invalid) return;
    this.rolService.crear(this.nuevoRolForm.value).subscribe({
      next: () => {
        this.nuevoRolForm.reset();
        this.cargarRoles();
      },
      error: err => this.error.set(this.errores.mensaje(err))
    });
  }

  eliminar(rol: Rol): void {
    if (!rol.idRol || !confirm(`¿Eliminar el rol ${rol.nombreRol}?`)) return;
    this.rolService.eliminar(rol.idRol).subscribe({
      next: () => this.cargarRoles(),
      error: err => this.error.set(this.errores.mensaje(err))
    });
  }

  asignar(): void {
    if (this.asignacionForm.invalid) return;
    const { idUsuario, idRol } = this.asignacionForm.value;
    this.rolService.asignarRol(idUsuario, idRol).subscribe({
      next: () => this.mensaje.set(`Rol ${idRol} asignado al usuario ${idUsuario}.`),
      error: err => this.error.set(this.errores.mensaje(err))
    });
  }

  quitar(): void {
    if (this.asignacionForm.invalid) return;
    const { idUsuario, idRol } = this.asignacionForm.value;
    this.rolService.quitarRol(idUsuario, idRol).subscribe({
      next: () => this.mensaje.set(`Rol ${idRol} retirado del usuario ${idUsuario}.`),
      error: err => this.error.set(this.errores.mensaje(err))
    });
  }

  verRolesDeUsuario(): void {
    const idUsuario = this.asignacionForm.value.idUsuario;
    if (!idUsuario) return;
    this.rolService.getRolesDeUsuario(idUsuario).subscribe({
      next: roles => this.consultaRolesUsuario.set(roles),
      error: err => this.error.set(this.errores.mensaje(err))
    });
  }

  verUsuariosDeRol(): void {
    const idRol = this.asignacionForm.value.idRol;
    if (!idRol) return;
    this.rolService.getUsuariosDeRol(idRol).subscribe({
      next: usuarios => this.consultaUsuariosRol.set(usuarios),
      error: err => this.error.set(this.errores.mensaje(err))
    });
  }
}
