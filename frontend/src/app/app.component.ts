import { Component, OnInit, inject, signal } from '@angular/core';
import { MapComponent } from './components/map.component';
import { EventsLogComponent } from './components/events-log.component';
import { VehicleCardComponent } from './components/vehicle-card.component';
import { DashboardComponent } from './components/dashboard.component';
import { WebSocketService } from './services/ws.service';

@Component({
  standalone: true,
  selector: 'app-root',
  imports: [MapComponent, EventsLogComponent, VehicleCardComponent, DashboardComponent],
  template: `
    <app-dashboard></app-dashboard>
    <main>
      <div class="map-wrap">
        <app-map (vehicleClick)="selected.set($event)"></app-map>
        @if (selected(); as id) {
          <aside class="card-wrap">
            <app-vehicle-card [deviceId]="id" (closeClick)="selected.set(null)"></app-vehicle-card>
          </aside>
        }
      </div>
      <div class="log-wrap">
        <app-events-log></app-events-log>
      </div>
    </main>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; height: 100vh; }
    main { flex: 1; display: grid; grid-template-columns: 1fr 420px; overflow: hidden; }
    .map-wrap { position: relative; }
    .card-wrap { position: absolute; top: 16px; right: 16px; width: 320px; z-index: 1000; }
    .log-wrap { border-left: 1px solid #e0e0e0; overflow: hidden; display: flex; flex-direction: column; }
  `],
})
export class AppComponent implements OnInit {
  private ws = inject(WebSocketService);
  selected = signal<string | null>(null);

  ngOnInit(): void {
    this.ws.start();
  }
}
