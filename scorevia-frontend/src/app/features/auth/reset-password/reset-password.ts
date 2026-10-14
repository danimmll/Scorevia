import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AbstractControl, FormBuilder, FormGroup, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { Router, ActivatedRoute, RouterModule } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { TranslocoPipe } from '@jsverse/transloco';
import { ErroresService } from '../../../core/services/errores.service';

// Mismo regex que UsuarioDTO.contrasenhaUsuario en el backend (ver register.ts, que ya lo
// aplica al registro) -- la nueva contraseña tiene que cumplir la misma regla.
const PASSWORD_PATTERN = /^(?=.*[!@#$%^&*(),.?":{}|<>])[A-Za-z\d!@#$%^&*(),.?":{}|<>]{8,}$/;

function passwordsCoinciden(): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const nueva = group.get('nuevaContrasenha')?.value;
    const confirmar = group.get('confirmarContrasenha')?.value;
    return nueva && confirmar && nueva !== confirmar ? { noCoinciden: true } : null;
  };
}

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule, TranslocoPipe],
  templateUrl: './reset-password.html',
  styleUrls: ['../auth-card.scss']
})
export class ResetPasswordComponent implements OnInit {
  private errores = inject(ErroresService);
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  correo = '';

  resetForm: FormGroup = this.fb.group({
    codigo: ['', [Validators.required, Validators.minLength(6), Validators.maxLength(6), Validators.pattern('^[0-9]*$')]],
    nuevaContrasenha: ['', [Validators.required, Validators.pattern(PASSWORD_PATTERN)]],
    confirmarContrasenha: ['', [Validators.required]]
  }, { validators: passwordsCoinciden() });

  errorMessage: string | null = null;
  successMessage: string | null = null;
  isLoading = false;

  ngOnInit() {
    this.route.queryParams.subscribe(params => {
      this.correo = params['email'] || '';
      if (!this.correo) {
        this.router.navigate(['/forgot-password']);
      }
    });
  }

  onSubmit() {
    if (this.resetForm.invalid || !this.correo) {
      this.resetForm.markAllAsTouched();
      return;
    }

    this.isLoading = true;
    this.errorMessage = null;

    const { codigo, nuevaContrasenha } = this.resetForm.value;

    this.authService.resetPassword(this.correo, codigo, nuevaContrasenha).subscribe({
      next: () => {
        this.isLoading = false;
        this.successMessage = 'Contraseña actualizada. Ya podés iniciar sesión.';
        setTimeout(() => {
          this.router.navigate(['/login']);
        }, 1500);
      },
      error: (err) => {
        this.isLoading = false;
        // Mismo esquema de intentos que login/verificación de email: 5 fallos bloquean el
        // reset 15 minutos (ver docs/back/usuarios-roles.md, "Recuperar contraseña").
        this.errorMessage = this.errores.mensaje(err, 'Código inválido o caducado.');
      }
    });
  }
}
