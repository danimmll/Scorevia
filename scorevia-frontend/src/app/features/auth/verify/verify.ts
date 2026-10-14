import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, ActivatedRoute, RouterModule } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-verify',
  standalone: true,
  imports: [CommonModule, 
    ReactiveFormsModule, 
    RouterModule, TranslocoPipe],
  templateUrl: './verify.html',
  styleUrls: ['../auth-card.scss'] 
})
export class VerifyComponent implements OnInit {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  correo: string = '';
  
  verifyForm: FormGroup = this.fb.group({
    codigo: ['', [Validators.required, Validators.minLength(6), Validators.maxLength(6), Validators.pattern('^[0-9]*$')]]
  });

  errorMessage: string | null = null;
  successMessage: string | null = null;
  isLoading = false;

  ngOnInit() {
    this.route.queryParams.subscribe(params => {
      this.correo = params['email'] || '';
      if (!this.correo) {
        this.router.navigate(['/register']);
      }
    });
  }

  onSubmit() {
    if (this.verifyForm.invalid || !this.correo) return;

    this.isLoading = true;
    this.errorMessage = null;

    this.authService.verifyEmail(this.correo, this.verifyForm.value.codigo).subscribe({
      next: (res) => {
        this.isLoading = false;
        this.successMessage = '¡Cuenta verificada con éxito! Entrando al panel...';
        setTimeout(() => {
          this.router.navigate(['/dashboard']);
        }, 1500);
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err.error?.detail || 'Código inválido o caducado.';
      }
    });
  }
}