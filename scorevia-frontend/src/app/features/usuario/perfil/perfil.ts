import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { UsuarioService } from '../../../core/services/usuario.service';
import { AuthService } from '../../../core/services/auth.service';
import { Usuario, UsuarioUpdate } from '../../../core/models/usuario.model';
import { CargandoComponent } from '../../../shared/cargando/cargando';
import { TranslocoPipe } from '@jsverse/transloco';
import { ErroresService } from '../../../core/services/errores.service';

@Component({
  selector: 'app-perfil',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, CargandoComponent, TranslocoPipe],
  templateUrl: './perfil.html',
  styleUrls: ['./perfil.scss']
})
export class PerfilComponent implements OnInit {
  private errores = inject(ErroresService);
  private fb = inject(FormBuilder);
  private usuarioService = inject(UsuarioService);
  public authService = inject(AuthService);
  private router = inject(Router);

  usuario = signal<Usuario | null>(null);
  cargando = signal(true);
  error = signal<string | null>(null);
  mensajeGuardado = signal<string | null>(null);

  perfilForm: FormGroup = this.fb.group({
    nickname: ['', [Validators.required, Validators.pattern(/^(?!(admin|moderador|soporte|system|root)).{3,20}$/i)]],
    nombreUsuario: ['', [Validators.required]],
    apellidoUsuario: ['', [Validators.required]],
    correoUsuario: ['', [Validators.required, Validators.email]],
    urlFoto: [''],
    nuevaContrasenha: ['']
  });

  ngOnInit(): void {
    const idUsuario = this.authService.currentUser()?.idUsuario;
    if (!idUsuario) return;

    this.usuarioService.getPorId(idUsuario).subscribe({
      next: usuario => {
        this.usuario.set(usuario);
        this.perfilForm.patchValue(usuario);
        this.cargando.set(false);
      },
      error: err => {
        this.error.set(this.errores.mensaje(err, 'No se pudo cargar tu perfil.'));
        this.cargando.set(false);
      }
    });
  }

  guardar(): void {
    const idUsuario = this.authService.currentUser()?.idUsuario;
    if (!idUsuario || this.perfilForm.invalid) {
      this.perfilForm.markAllAsTouched();
      return;
    }

    this.error.set(null);
    this.mensajeGuardado.set(null);

    const valores = this.perfilForm.value;
    const dto: UsuarioUpdate = {
      idUsuario,
      nickname: valores.nickname,
      nombreUsuario: valores.nombreUsuario,
      apellidoUsuario: valores.apellidoUsuario,
      correoUsuario: valores.correoUsuario,
      urlFoto: valores.urlFoto || null,
      // Contraseña opcional: si el campo queda vacío no se toca la actual.
      contrasenhaUsuario: valores.nuevaContrasenha ? valores.nuevaContrasenha : null
    };

    this.usuarioService.actualizar(idUsuario, dto).subscribe({
      next: usuario => {
        this.usuario.set(usuario);
        this.mensajeGuardado.set('Perfil actualizado correctamente.');
      },
      error: err => this.error.set(this.errores.mensaje(err, 'No se pudo guardar el perfil.'))
    });
  }

  exportarExcel(): void {
    this.usuarioService.exportarExcel().subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const enlace = document.createElement('a');
        enlace.href = url;
        enlace.download = 'listado_usuarios_completo.xlsx';
        enlace.click();
        URL.revokeObjectURL(url);
      },
      error: err => this.error.set(this.errores.mensaje(err, 'No se pudo exportar el listado.'))
    });
  }

  darDeBaja(): void {
    const idUsuario = this.authService.currentUser()?.idUsuario;
    if (!idUsuario) return;
    if (!confirm('¿Seguro que quieres dar de baja tu cuenta? Esta acción anonimiza tus datos.')) return;

    this.usuarioService.eliminar(idUsuario).subscribe({
      next: () => {
        this.authService.logout().subscribe(() => this.router.navigate(['/']));
      },
      error: err => this.error.set(this.errores.mensaje(err, 'No se pudo dar de baja la cuenta.'))
    });
  }
}
