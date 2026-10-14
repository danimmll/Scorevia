import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { TranslocoPipe } from '@jsverse/transloco';
import { ErroresService } from '../../../core/services/errores.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule, TranslocoPipe],
  templateUrl: './register.html',
  styleUrls: ['../auth-card.scss']
})
export class RegisterComponent {
  private errores = inject(ErroresService);
  private fb = inject(FormBuilder);
  private router = inject(Router);
  private authService = inject(AuthService); 

  public registerForm: FormGroup = this.fb.group({
    nickname: ['', [Validators.required, Validators.pattern(/^(?!(admin|moderador|soporte|system|root)).{3,20}$/i)]],
    nombre: ['', [Validators.required]],
    apellido: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    // Mismo regex que UsuarioDTO.contrasenhaUsuario en el backend (Microservicio-usuarios-roles):
    // sin esto, una contraseña de 8+ caracteres sin ningún carácter especial pasaba la
    // validación del formulario (en verde) y recién fallaba al recibir el 400 del backend.
    password: ['', [Validators.required, Validators.pattern(/^(?=.*[!@#$%^&*(),.?":{}|<>])[A-Za-z\d!@#$%^&*(),.?":{}|<>]{8,}$/)]]
  });

  public errorMessage: string | null = null;

  public register(): void {
    if (this.registerForm.valid) {
      this.errorMessage = null;

      const usuario = {
        nickname: this.registerForm.value.nickname,
        nombreUsuario: this.registerForm.value.nombre,
        apellidoUsuario: this.registerForm.value.apellido,
        correoUsuario: this.registerForm.value.email,
        contrasenhaUsuario: this.registerForm.value.password
      };

      this.authService.register(usuario).subscribe({
        next: () => {
          this.router.navigate(['/verify'], { queryParams: { email: this.registerForm.value.email } });
        },
        error: (err) => {
          this.errorMessage = this.errores.mensaje(err, 'No se pudo completar el registro.');
        }
      });
    } else {
      this.registerForm.markAllAsTouched();
    }
  }
}