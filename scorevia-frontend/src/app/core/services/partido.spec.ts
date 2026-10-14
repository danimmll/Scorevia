import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { PartidoService } from './partido.service';
import { importarTranslocoDePrueba } from '../i18n/testing';

describe('PartidoService', () => {
  let service: PartidoService;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [importarTranslocoDePrueba()],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PartidoService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
