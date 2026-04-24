import { Injectable } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import { Observable, Subject } from 'rxjs';
import { GpsPoint, VehicleEvent } from '../models';

const WS_URL = 'ws://localhost:8090/ws/stomp';
const LAST_SEEN_KEY = 'berkut.lastSeen';

export type WsStatus = 'connecting' | 'connected' | 'reconnecting' | 'disconnected';

/**
 * STOMP over native WebSocket. On (re)connect, notifies subscribers via onConnect() so
 * they can run REST backfill. lastSeen timestamp is kept in localStorage; a reloaded
 * tab sees the gap covered by GET /api/events?since=<lastSeen>.
 */
@Injectable({ providedIn: 'root' })
export class WebSocketService {
  private client: Client;
  private gps$ = new Subject<GpsPoint>();
  private events$ = new Subject<VehicleEvent>();
  private status$ = new Subject<WsStatus>();
  private onConnectCallbacks: Array<() => void> = [];

  constructor() {
    this.client = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 2000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => { /* silence */ },
    });

    this.client.onConnect = () => {
      this.status$.next('connected');
      this.client.subscribe('/topic/gps', (msg: IMessage) => {
        try {
          const p: GpsPoint = JSON.parse(msg.body);
          this.updateLastSeen();
          this.gps$.next(p);
        } catch {
          /* ignore malformed */
        }
      });
      this.client.subscribe('/topic/events', (msg: IMessage) => {
        try {
          const e: VehicleEvent = JSON.parse(msg.body);
          this.updateLastSeen();
          this.events$.next(e);
        } catch {
          /* ignore malformed */
        }
      });
      this.onConnectCallbacks.forEach((cb) => cb());
    };

    this.client.onWebSocketClose = () => this.status$.next('reconnecting');
    this.client.onDisconnect = () => this.status$.next('disconnected');
  }

  start(): void {
    if (!this.client.active) {
      this.status$.next('connecting');
      this.client.activate();
    }
  }

  onGps(): Observable<GpsPoint> { return this.gps$.asObservable(); }
  onEvent(): Observable<VehicleEvent> { return this.events$.asObservable(); }
  onStatus(): Observable<WsStatus> { return this.status$.asObservable(); }

  /** Register a callback that fires on every (re)connect — for REST backfill after reconnect. */
  onConnect(cb: () => void): void { this.onConnectCallbacks.push(cb); }

  getLastSeen(): string | null { return localStorage.getItem(LAST_SEEN_KEY); }

  private updateLastSeen(): void {
    localStorage.setItem(LAST_SEEN_KEY, new Date().toISOString());
  }
}
