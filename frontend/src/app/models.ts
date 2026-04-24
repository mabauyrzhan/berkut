export interface GpsPoint {
  deviceId: string;
  timestamp: string | number;
  latitude: number;
  longitude: number;
  speedKmh: number;
  headingDegrees: number;
}

export type EventTypeStr = 'DROWSINESS' | 'SPEEDING' | 'HARSH_BRAKING' | 'COLLISION_WARNING';
export type SeverityStr = 'LOW' | 'MEDIUM' | 'CRITICAL';

export interface VehicleEvent {
  eventId: string;
  deviceId: string;
  timestamp: string | number;
  type: EventTypeStr;
  severity: SeverityStr;
  latitude: number;
  longitude: number;
  speedKmh: number;
  metadata?: {
    groupId?: string;
    isNew?: boolean;
    source?: string;
    [k: string]: unknown;
  };
}

export interface EventGroupView {
  groupId: string;
  deviceId: string;
  licensePlate: string | null;
  driverName: string | null;
  type: EventTypeStr;
  severity: SeverityStr;
  firstSeen: string;
  lastSeen: string;
  eventCount: number;
}

export interface PositionView {
  deviceId: string;
  licensePlate: string;
  driverName: string;
  latitude: number;
  longitude: number;
  speedKmh: number;
  headingDegrees: number;
  timestamp: string;
  online: boolean;
}

export interface VehicleDetail {
  deviceId: string;
  licensePlate: string;
  driverName: string;
  lastPosition: PositionView | null;
  recentEvents: EventGroupView[];
}

export interface DashboardStats {
  onlineVehicles: number;
  totalVehicles: number;
  eventsLastHour: number;
  eventsLastDay: number;
  criticalLastDay: number;
}

export function toIso(ts: string | number): string {
  if (typeof ts === 'number') {
    return new Date(ts * 1000).toISOString();
  }
  return ts;
}
