import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { VerifyComponent } from './verify';
import { importarTranslocoDePrueba } from '../../../core/i18n/testing';

describe('VerifyComponent', () => {
  let component: VerifyComponent;
  let fixture: ComponentFixture<VerifyComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VerifyComponent, importarTranslocoDePrueba()],
      // Ruta dummy para 'register': sin ningún query param 'email', ngOnInit() navega ahí
      // (ver verify.ts) -- con provideRouter([]) esa navegación no encuentra ninguna ruta
      // y revienta como unhandled rejection (NG04002) aunque el test en sí pase.
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'register', component: VerifyComponent }]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(VerifyComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
