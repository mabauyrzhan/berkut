import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { ApiService } from '../services/api.service';
import { DashboardStats } from '../models';
import { WebSocketService, WsStatus } from '../services/ws.service';

@Component({
  standalone: true,
  selector: 'app-dashboard',
  template: `
    <div class="bar">
      <div class="title">Berkut · Fleet Monitor</div>
      @if (stats(); as s) {
        <div class="stat">
          <span class="val">{{ s.onlineVehicles }}/{{ s.totalVehicles }}</span>
          <span class="lbl">онлайн</span>
        </div>
        <div class="stat">
          <span class="val">{{ s.eventsLastHour }}</span>
          <span class="lbl">событий/час</span>
        </div>
        <div class="stat">
          <span class="val">{{ s.eventsLastDay }}</span>
          <span class="lbl">событий/день</span>
        </div>
        <div class="stat crit">
          <span class="val">{{ s.criticalLastDay }}</span>
          <span class="lbl">CRITICAL/день</span>
        </div>
      }
      <div class="status" [attr.data-s]="status()">{{ statusLabel() }}</div>
    </div>
  `,
  styles: [`
    .bar { display: flex; align-items: center; gap: 24px; padding: 8px 20px; background: #263238; color: #fff; min-height: 48px; }
    .title { font-size: 16px; font-weight: 700; letter-spacing: 0.5px; }
    .stat { display: flex; flex-direction: column; align-items: center; min-width: 80px; }
    .val { font-size: 20px; font-weight: 700; font-variant-numeric: tabular-nums; }
    .lbl { font-size: 10px; opacity: 0.7; text-transform: uppercase; }
    .crit .val { color: #ff5252; }
    .status { margin-left: auto; padding: 4px 12px; border-radius: 12px; font-size: 11px; background: #455a64; }
    .status[data-s="connected"] { background: #2e7d32; }
    .status[data-s="reconnecting"], .status[data-s="connecting"] { background: #f57c00; }
    .status[data-s="disconnected"] { background: #c62828; }
  `],
})
export class DashboardComponent implements OnInit, OnDestroy {
  private api = inject(ApiService);
  private ws = inject(WebSocketService);

  stats = signal<DashboardStats | null>(null);
  status = signal<WsStatus>('connecting');
  private timer: ReturnType<typeof setInterval> | null = null;

  ngOnInit(): void {
    this.load();
    this.timer = setInterval(() => this.load(), 5000);
    this.ws.onStatus().subscribe((s) => this.status.set(s));
  }

  ngOnDestroy(): void {
    if (this.timer !== null) clearInterval(this.timer);
  }

  statusLabel(): string {
    switch (this.status()) {
      case 'connected': return 'WS · OK';
      case 'connecting': return 'WS · подключение';
      case 'reconnecting': return 'WS · переподключение';
      case 'disconnected': return 'WS · разрыв';
    }
  }

  private load(): void {
    this.api.dashboardStats().subscribe((s) => this.stats.set(s));
  }
}
