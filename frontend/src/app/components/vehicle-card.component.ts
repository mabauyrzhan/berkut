import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ApiService } from '../services/api.service';
import { VehicleDetail } from '../models';

@Component({
  standalone: true,
  selector: 'app-vehicle-card',
  imports: [DatePipe, DecimalPipe],
  template: `
    @if (detail(); as d) {
      <div class="card">
        <header>
          <strong>{{ d.licensePlate }}</strong>
          <button aria-label="close" (click)="closeClick.emit()">×</button>
        </header>
        <div class="body">
          <p>{{ d.driverName }}</p>
          @if (d.lastPosition; as pos) {
            <p class="pos">
              <span>{{ pos.speedKmh | number: '1.0-1' }} км/ч</span>
              <span class="dot" [class.online]="pos.online" [class.offline]="!pos.online"></span>
              <span>{{ pos.online ? 'онлайн' : 'оффлайн' }}</span>
            </p>
            <p class="muted">обновлено {{ pos.timestamp | date: 'HH:mm:ss' }}</p>
          }
          <h4>Последние группы событий</h4>
          @if (d.recentEvents.length === 0) {
            <p class="muted">нет</p>
          }
          @for (e of d.recentEvents; track e.groupId) {
            <div class="event sev-{{ e.severity }}">
              <div class="line">
                <span>{{ e.type }}</span>
                <span class="sev">{{ e.severity }}</span>
                @if (e.eventCount > 1) {<span class="count">×{{ e.eventCount }}</span>}
              </div>
              <small>{{ e.lastSeen | date: 'HH:mm:ss' }}</small>
            </div>
          }
        </div>
      </div>
    }
  `,
  styles: [`
    .card { background: #fff; border-radius: 8px; box-shadow: 0 6px 24px rgba(0,0,0,0.15); overflow: hidden; }
    header { display: flex; justify-content: space-between; align-items: center; padding: 10px 14px; background: #1976d2; color: #fff; }
    header button { background: transparent; color: #fff; border: 0; font-size: 22px; cursor: pointer; line-height: 1; }
    .body { padding: 12px 14px; font-size: 13px; max-height: 60vh; overflow-y: auto; }
    .body p { margin: 4px 0; }
    .pos { display: flex; align-items: center; gap: 8px; }
    .dot { width: 8px; height: 8px; border-radius: 50%; }
    .dot.online  { background: #43a047; }
    .dot.offline { background: #bdbdbd; }
    .muted { color: #888; font-size: 11px; }
    h4 { margin: 12px 0 6px; font-size: 12px; text-transform: uppercase; color: #666; }
    .event { padding: 6px 8px; margin-bottom: 4px; border-left: 3px solid #bdbdbd; background: #fafafa; }
    .event.sev-CRITICAL { border-left-color: #e53935; background: #ffebee; }
    .event.sev-MEDIUM   { border-left-color: #fb8c00; background: #fff3e0; }
    .event .line { display: flex; align-items: center; gap: 6px; }
    .event .sev { font-size: 10px; padding: 1px 6px; border-radius: 10px; background: #eceff1; }
    .event.sev-CRITICAL .sev { background: #e53935; color: #fff; }
    .event.sev-MEDIUM   .sev { background: #fb8c00; color: #fff; }
    .event .count { font-weight: 600; color: #1976d2; margin-left: auto; }
    .event small { color: #888; font-size: 10px; }
  `],
})
export class VehicleCardComponent implements OnChanges {
  @Input() deviceId: string | null = null;
  @Output() closeClick = new EventEmitter<void>();

  private api = inject(ApiService);
  detail = signal<VehicleDetail | null>(null);

  ngOnChanges(_: SimpleChanges): void {
    if (this.deviceId) {
      this.api.vehicle(this.deviceId).subscribe((d) => this.detail.set(d));
    } else {
      this.detail.set(null);
    }
  }
}
