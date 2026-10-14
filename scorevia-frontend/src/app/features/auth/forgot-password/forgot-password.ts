import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { TranslocoPipe } from '@jsverse/transloco';
import { ErroresService } from '../../../core/services/errores.service';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule, TranslocoPipe],
  templateUrl: './forgot-password.html',
  styleUrls: ['../auth-card.scss']
})
export class ForgotPasswordComponent {
  private errores = inject(ErroresService);
  private fb = inject(FormBuilder);
  private router = inject(Router);
  private authService = inject(AuthService);

  public forgotForm: FormGroup = this.fb.group({
    email: ['', [Validators.required, Validators.email]]
  });

  public errorMessage: string | null = null;
  public successMessage: string | null = null;
  public isLoading = false;

  public onSubmit(): void {
    if (this.forgotForm.invalid) {
      this.forgotForm.markAllAsTouched();
      return;
    }

    this.isLoading = true;
    this.errorMessage = null;

    const correo = this.forgotForm.value.email;

    // La respuesta del backend es idéntica exista o no esa cuenta (anti-enumeración, ver
    // docs/back/usuarios-roles.md "Recuperar contraseña") -- por eso acá siempre se muestra
    // el mismo mensaje de éxito, nunca "ese correo no existe".
    this.authService.solicitarResetPassword(correo).subscribe({
      next: () => {
        this.isLoading = false;
        this.successMessage = 'Si ese correo tiene una cuenta activa, te llegó un código de 6 dígitos.';
        setTimeout(() => {
          this.router.navigate(['/reset-password'], { queryParams: { email: correo } });
        }, 1500);
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = this.errores.mensaje(err, 'No se pudo procesar la solicitud.');
      }
    });
  }
}
