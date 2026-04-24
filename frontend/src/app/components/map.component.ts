import { AfterViewInit, Component, ElementRef, EventEmitter, Output, ViewChild, inject } from '@angular/core';
import * as L from 'leaflet';
import { ApiService } from '../services/api.service';
import { WebSocketService } from '../services/ws.service';
import { GpsPoint, PositionView } from '../models';

/**
 * Canvas-rendered Leaflet with throttled painting: GPS updates land in a Map buffer,
 * the actual marker moves happen on a 500 ms timer. For 1k vehicles at 3 s cadence
 * the buffer stays small and the UI stays smooth. For 10k+ vehicles, swap CircleMarker
 * for supercluster — described in README.
 */
@Component({
  standalone: true,
  selector: 'app-map',
  template: `<div #mapEl class="map"></div>`,
  styles: [`.map { height: 100%; width: 100%; }`],
})
export class MapComponent implements AfterViewInit {
  @ViewChild('mapEl') mapEl!: ElementRef<HTMLDivElement>;
  @Output() vehicleClick = new EventEmitter<string>();

  private api = inject(ApiService);
  private ws = inject(WebSocketService);

  private map!: L.Map;
  private readonly markers = new Map<string, L.CircleMarker>();
  private readonly meta = new Map<string, { plate: string; driver: string }>();
  private readonly buffer = new Map<string, GpsPoint>();
  private paintTimer: ReturnType<typeof setTimeout> | null = null;

  ngAfterViewInit(): void {
    this.map = L.map(this.mapEl.nativeElement, {
      center: [43.2389, 76.8897],
      zoom: 12,
      preferCanvas: true,
    });
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '© OpenStreetMap',
    }).addTo(this.map);

    this.loadInitial();
    this.ws.onGps().subscribe((p) => this.bufferUpdate(p));
    this.ws.onEvent().subscribe((e) => this.flashEvent(e.deviceId, e.severity));
    this.ws.onConnect(() => this.loadInitial());
  }

  private loadInitial(): void {
    this.api.lastPositions().subscribe((positions) => {
      positions.forEach((p) => {
        this.meta.set(p.deviceId, { plate: p.licensePlate, driver: p.driverName });
        this.upsertMarker(p.deviceId, p.latitude, p.longitude);
      });
    });
  }

  private bufferUpdate(p: GpsPoint): void {
    this.buffer.set(p.deviceId, p);
    if (this.paintTimer === null) {
      this.paintTimer = setTimeout(() => this.paint(), 500);
    }
  }

  private paint(): void {
    this.paintTimer = null;
    this.buffer.forEach((p, id) => this.upsertMarker(id, p.latitude, p.longitude));
    this.buffer.clear();
  }

  private upsertMarker(id: string, lat: number, lon: number): void {
    const existing = this.markers.get(id);
    if (existing) {
      existing.setLatLng([lat, lon]);
      return;
    }
    const m = L.circleMarker([lat, lon], {
      radius: 6,
      color: '#1976d2',
      fillColor: '#2196f3',
      fillOpacity: 0.85,
      weight: 2,
    });
    const meta = this.meta.get(id);
    const label = meta ? `${meta.plate}<br>${meta.driver}` : id;
    m.bindTooltip(label, { direction: 'top' });
    m.on('click', () => this.vehicleClick.emit(id));
    m.addTo(this.map);
    this.markers.set(id, m);
  }

  /** Brief pulse on a marker when its vehicle emits an event — visual heads-up for dispatcher. */
  private flashEvent(deviceId: string, severity: string): void {
    const m = this.markers.get(deviceId);
    if (!m) return;
    const color = severity === 'CRITICAL' ? '#e53935' : severity === 'MEDIUM' ? '#fb8c00' : '#ffeb3b';
    const originalColor = m.options.color;
    const originalRadius = m.options.radius ?? 6;
    m.setStyle({ color, fillColor: color, radius: originalRadius + 4 });
    setTimeout(() => m.setStyle({ color: originalColor, fillColor: '#2196f3', radius: originalRadius }), 1200);
  }
}
