import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { TranslocoPipe } from '@jsverse/transloco';
import { ErroresService } from '../../../core/services/errores.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule, TranslocoPipe],
  templateUrl: './login.html',
  styleUrls: ['../auth-card.scss']
})
export class LoginComponent {
  private errores = inject(ErroresService);
  private fb = inject(FormBuilder);
  private router = inject(Router);
  private authService = inject(AuthService);

  public loginForm: FormGroup = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]]
  });

public errorMessage: string | null = null;

public login(): void {
    if (this.loginForm.valid) {
      this.errorMessage = null;

      this.authService.login(this.loginForm.value.email, this.loginForm.value.password).subscribe({
        next: () => {
          this.router.navigate(['/']);
        },
        error: (err) => {
          this.errorMessage = this.errores.mensaje(err, 'Correo o contraseña incorrectos.');
        }
      });
    } else {
      this.loginForm.markAllAsTouched();
    }
  }
}