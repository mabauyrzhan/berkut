import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { DashboardStats, EventGroupView, PositionView, VehicleDetail } from '../models';

const API = 'http://localhost:8090/api';

export interface EventFilters {
  vehicle?: string;
  type?: string;
  severity?: string;
  since?: string;
  until?: string;
  limit?: number;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);

  events(filters: EventFilters = {}): Observable<EventGroupView[]> {
    return this.http.get<EventGroupView[]>(`${API}/events${toQuery(filters)}`);
  }

  lastPositions(): Observable<PositionView[]> {
    return this.http.get<PositionView[]>(`${API}/positions/last`);
  }

  vehicle(id: string): Observable<VehicleDetail> {
    return this.http.get<VehicleDetail>(`${API}/vehicles/${id}`);
  }

  dashboardStats(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>(`${API}/dashboard/stats`);
  }

  csvExportUrl(filters: Omit<EventFilters, 'limit'> = {}): string {
    return `${API}/events/export${toQuery(filters)}`;
  }
}

function toQuery(params: object): string {
  const q = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') q.set(k, String(v));
  });
  const s = q.toString();
  return s ? `?${s}` : '';
}
