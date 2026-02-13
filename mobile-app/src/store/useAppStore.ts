import { create } from 'zustand';
import { LocationMessage, ConnectionStatus, LogEntry } from '../types/types';

const MAX_LOGS = 200;

interface AppState {
    // Device
    deviceId: string;

    // Connection
    peerIp: string;
    port: number;
    connectionStatus: ConnectionStatus;

    // Locations
    myLocation: LocationMessage | null;
    peerLocation: LocationMessage | null;

    // Logs
    logs: LogEntry[];

    // Actions
    setPeerIp: (ip: string) => void;
    setPort: (port: number) => void;
    setConnectionStatus: (status: ConnectionStatus) => void;
    setMyLocation: (loc: LocationMessage) => void;
    setPeerLocation: (loc: LocationMessage) => void;
    addLog: (message: string, level?: LogEntry['level']) => void;
    clearLogs: () => void;
}

let logCounter = 0;

export const useAppStore = create<AppState>((set) => ({
    deviceId: `device-${Math.random().toString(36).substring(2, 8)}`,

    peerIp: '',
    port: 9090,
    connectionStatus: 'disconnected',

    myLocation: null,
    peerLocation: null,

    logs: [],

    setPeerIp: (ip) => set({ peerIp: ip }),
    setPort: (port) => set({ port }),
    setConnectionStatus: (status) => set({ connectionStatus: status }),
    setMyLocation: (loc) => set({ myLocation: loc }),
    setPeerLocation: (loc) => set({ peerLocation: loc }),

    addLog: (message, level = 'info') =>
        set((state) => ({
            logs: [
                ...state.logs.slice(-(MAX_LOGS - 1)),
                {
                    id: `log-${++logCounter}`,
                    timestamp: Date.now(),
                    message,
                    level,
                },
            ],
        })),

    clearLogs: () => set({ logs: [] }),
}));
