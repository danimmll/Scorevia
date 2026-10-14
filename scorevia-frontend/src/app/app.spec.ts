import { TestBed } from '@angular/core/testing';
import { App } from './app';
import { importarTranslocoDePrueba } from './core/i18n/testing';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App, importarTranslocoDePrueba()],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });
});
