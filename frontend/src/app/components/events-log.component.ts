import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ScrollingModule } from '@angular/cdk/scrolling';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { ApiService } from '../services/api.service';
import { WebSocketService } from '../services/ws.service';
import { EventGroupView, SeverityStr, VehicleEvent, toIso } from '../models';

@Component({
  standalone: true,
  selector: 'app-events-log',
  imports: [ScrollingModule, FormsModule, DatePipe],
  template: `
    <div class="header">
      <strong>События</strong>
      <span class="muted">({{ filtered().length }})</span>
      <button class="export" (click)="exportCsv()">CSV</button>
    </div>
    <div class="filters">
      <input [(ngModel)]="query" (ngModelChange)="query.valueOf()" placeholder="машина / водитель / ID" />
      <select [(ngModel)]="severity">
        <option value="">все</option>
        <option value="LOW">LOW</option>
        <option value="MEDIUM">MEDIUM</option>
        <option value="CRITICAL">CRITICAL</option>
      </select>
    </div>
    <cdk-virtual-scroll-viewport itemSize="56" class="viewport">
      <div *cdkVirtualFor="let e of filtered()" class="row sev-{{ e.severity }}">
        <span class="time">{{ e.lastSeen | date: 'HH:mm:ss' }}</span>
        <span class="plate">{{ e.licensePlate || e.deviceId }}</span>
        <span class="type">{{ e.type }}</span>
        <span class="sev">{{ e.severity }}</span>
        @if (e.eventCount > 1) {
          <span class="count">×{{ e.eventCount }}</span>
        }
      </div>
    </cdk-virtual-scroll-viewport>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; height: 100%; background: #fafafa; font-size: 13px; }
    .header { display: flex; align-items: center; gap: 8px; padding: 8px 12px; border-bottom: 1px solid #e0e0e0; background: #fff; }
    .header .muted { color: #666; }
    .header .export { margin-left: auto; border: 1px solid #1976d2; color: #1976d2; background: #fff; padding: 4px 10px; border-radius: 4px; cursor: pointer; }
    .filters { display: flex; gap: 6px; padding: 6px 12px; border-bottom: 1px solid #e0e0e0; background: #fff; }
    .filters input, .filters select { padding: 4px 8px; border: 1px solid #ccc; border-radius: 3px; font-size: 12px; }
    .filters input { flex: 1; }
    .viewport { flex: 1; }
    .row { display: flex; gap: 8px; align-items: center; padding: 8px 12px; border-bottom: 1px solid #eee; white-space: nowrap; }
    .row.sev-CRITICAL { background: #ffebee; }
    .row.sev-MEDIUM   { background: #fff3e0; }
    .time { color: #666; font-variant-numeric: tabular-nums; font-size: 12px; }
    .plate { font-weight: 600; min-width: 90px; }
    .type { flex: 1; color: #333; }
    .sev { font-size: 11px; padding: 2px 6px; border-radius: 10px; background: #eceff1; }
    .sev-CRITICAL .sev { background: #e53935; color: #fff; }
    .sev-MEDIUM   .sev { background: #fb8c00; color: #fff; }
    .count { font-weight: 600; color: #1976d2; }
  `],
})
export class EventsLogComponent implements OnInit {
  private api = inject(ApiService);
  private ws = inject(WebSocketService);

  events = signal<EventGroupView[]>([]);
  query = '';
  severity: '' | SeverityStr = '';

  // Re-computes when events() changes OR template re-renders after ngModel (zone).
  filtered = computed<EventGroupView[]>(() => {
    const q = this.query.toLowerCase();
    const sev = this.severity;
    return this.events().filter((e) => {
      if (sev && e.severity !== sev) return false;
      if (q && !(e.deviceId.toLowerCase().includes(q) ||
                 (e.licensePlate ?? '').toLowerCase().includes(q) ||
                 (e.driverName ?? '').toLowerCase().includes(q))) return false;
      return true;
    });
  });

  ngOnInit(): void {
    this.backfill();
    this.ws.onConnect(() => this.backfill());
    this.ws.onEvent().subscribe((e) => this.merge(e));
  }

  backfill(): void {
    const lastSeen = this.ws.getLastSeen();
    const since = lastSeen ?? new Date(Date.now() - 60 * 60 * 1000).toISOString();
    this.api.events({ since, limit: 500 }).subscribe((list) => this.events.set(list));
  }

  exportCsv(): void {
    window.open(this.api.csvExportUrl({ severity: this.severity || undefined }), '_blank');
  }

  private merge(e: VehicleEvent): void {
    const groupId = e.metadata?.groupId;
    if (!groupId) return;
    const current = this.events();
    const idx = current.findIndex((x) => x.groupId === groupId);
    const ts = toIso(e.timestamp);

    if (idx >= 0) {
      const existing = current[idx];
      const updated: EventGroupView = {
        ...existing,
        eventCount: existing.eventCount + 1,
        lastSeen: ts,
        severity: priority(e.severity) > priority(existing.severity) ? e.severity : existing.severity,
      };
      const next = [...current];
      next[idx] = updated;
      this.events.set(next);
    } else {
      const row: EventGroupView = {
        groupId,
        deviceId: e.deviceId,
        licensePlate: null,
        driverName: null,
        type: e.type,
        severity: e.severity,
        firstSeen: ts,
        lastSeen: ts,
        eventCount: 1,
      };
      this.events.update((curr) => [row, ...curr].slice(0, 1000));
    }
  }
}

function priority(s: SeverityStr): number {
  return s === 'CRITICAL' ? 3 : s === 'MEDIUM' ? 2 : 1;
}
