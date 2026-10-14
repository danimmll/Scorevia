import { TestBed } from '@angular/core/testing';

import { ThemeService } from './theme.service';
import { importarTranslocoDePrueba } from '../i18n/testing';

describe('ThemeService', () => {
  let service: ThemeService;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [importarTranslocoDePrueba()],});
    service = TestBed.inject(ThemeService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
