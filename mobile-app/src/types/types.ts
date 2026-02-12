export interface LocationMessage {
  type: 'location';
  deviceId: string;
  ts: number;
  lat: number;
  lng: number;
  speed: number | null;
  heading: number | null;
  accuracy: number | null;
}

export type ConnectionStatus = 'disconnected' | 'connecting' | 'connected';

export interface LogEntry {
  id: string;
  timestamp: number;
  message: string;
  level: 'info' | 'error' | 'warn' | 'success';
}
