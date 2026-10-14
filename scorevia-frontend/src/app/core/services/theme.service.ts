import { Injectable, signal, effect, PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

export type Theme = 'dark' | 'light';

const STORAGE_KEY = 'scorevia-theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private platformId = inject(PLATFORM_ID);
  private isBrowser = isPlatformBrowser(this.platformId);

  // Oscuro es el tema por defecto de ScoreVia
  theme = signal<Theme>(this.leerThemeInicial());

  constructor() {
    // Cada vez que cambie el signal, sincroniza la clase en <html> y localStorage.
    // Con esto te basta: inyecta ThemeService una vez en AppComponent (o donde
    // ya tengas el AuthService) y el toggle funciona en toda la app.
    effect(() => {
      if (!this.isBrowser) return;
      const theme = this.theme();
      document.documentElement.classList.toggle('light', theme === 'light');
      localStorage.setItem(STORAGE_KEY, theme);
    });
  }

  toggle(): void {
    this.theme.set(this.theme() === 'dark' ? 'light' : 'dark');
  }

  set(theme: Theme): void {
    this.theme.set(theme);
  }

  private leerThemeInicial(): Theme {
    if (!this.isBrowser) return 'dark';
    const guardado = localStorage.getItem(STORAGE_KEY);
    if (guardado === 'light' || guardado === 'dark') return guardado;
    return 'dark';
  }
}