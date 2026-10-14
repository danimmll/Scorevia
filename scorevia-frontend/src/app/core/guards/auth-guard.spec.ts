import { TestBed } from '@angular/core/testing';
import { CanActivateFn } from '@angular/router';

import { authGuard } from './auth-guard';
import { importarTranslocoDePrueba } from '../i18n/testing';

describe('authGuard', () => {
  const executeGuard: CanActivateFn = (...guardParameters) =>
    TestBed.runInInjectionContext(() => authGuard(...guardParameters));

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [importarTranslocoDePrueba()],});
  });

  it('should be created', () => {
    expect(executeGuard).toBeTruthy();
  });
});
