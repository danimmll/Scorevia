import { TestBed } from '@angular/core/testing';
import { CanActivateFn } from '@angular/router';

import { publicGuard } from './public-guard';
import { importarTranslocoDePrueba } from '../i18n/testing';

describe('publicGuard', () => {
  const executeGuard: CanActivateFn = (...guardParameters) =>
    TestBed.runInInjectionContext(() => publicGuard(...guardParameters));

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [importarTranslocoDePrueba()],});
  });

  it('should be created', () => {
    expect(executeGuard).toBeTruthy();
  });
});
