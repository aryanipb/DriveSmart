import TcpSocket from 'react-native-tcp-socket';
import { LocationMessage } from '../types/types';

type MessageCallback = (msg: LocationMessage) => void;
type StatusCallback = (status: 'connected' | 'disconnected') => void;
type LogCallback = (message: string, level: 'info' | 'error' | 'warn' | 'success') => void;

let server: any = null;
let client: any = null;
let serverClient: any = null; // client connected to our server
let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
let buffer = '';

let onMessageCb: MessageCallback | null = null;
let onStatusCb: StatusCallback | null = null;
let onLogCb: LogCallback | null = null;

function log(msg: string, level: 'info' | 'error' | 'warn' | 'success' = 'info') {
    onLogCb?.(msg, level);
}

function processBuffer(data: string) {
    buffer += data;
    const lines = buffer.split('\n');
    buffer = lines.pop() || ''; // keep incomplete line in buffer

    for (const line of lines) {
        if (!line.trim()) continue;
        try {
            const msg = JSON.parse(line) as LocationMessage;
            if (msg.type === 'location') {
                onMessageCb?.(msg);
                log(`📍 Recv: ${msg.lat.toFixed(4)}, ${msg.lng.toFixed(4)}`, 'info');
            }
        } catch (e) {
            log(`Parse error: ${line.substring(0, 50)}`, 'error');
        }
    }
}

export function setCallbacks(
    onMessage: MessageCallback,
    onStatus: StatusCallback,
    onLog: LogCallback
) {
    onMessageCb = onMessage;
    onStatusCb = onStatus;
    onLogCb = onLog;
}

export function startServer(port: number) {
    if (server) {
        log('Server already running', 'warn');
        return;
    }

    try {
        server = TcpSocket.createServer((socket: any) => {
            const addr = socket.remoteAddress;
            log(`🔌 Peer connected from ${addr}`, 'success');
            serverClient = socket;
            onStatusCb?.('connected');

            socket.on('data', (data: Buffer | string) => {
                processBuffer(data.toString());
            });

            socket.on('error', (err: Error) => {
                log(`Server socket error: ${err.message}`, 'error');
            });

            socket.on('close', () => {
                log(`🔌 Peer disconnected`, 'warn');
                serverClient = null;
            });
        });

        server.listen({ port, host: '0.0.0.0' }, () => {
            log(`🟢 Server listening on port ${port}`, 'success');
        });

        server.on('error', (err: Error) => {
            log(`Server error: ${err.message}`, 'error');
        });
    } catch (err: any) {
        log(`Failed to start server: ${err.message}`, 'error');
    }
}

export function connectToPeer(ip: string, port: number) {
    if (client) {
        log('Already connecting/connected to peer', 'warn');
        return;
    }

    log(`🔄 Connecting to ${ip}:${port}...`, 'info');
    onStatusCb?.('connected'); // will be overridden below

    try {
        client = TcpSocket.createConnection(
            { host: ip, port },
            () => {
                log(`✅ Connected to peer ${ip}:${port}`, 'success');
                onStatusCb?.('connected');
            }
        );

        client.on('data', (data: Buffer | string) => {
            processBuffer(data.toString());
        });

        client.on('error', (err: Error) => {
            log(`Client error: ${err.message}`, 'error');
            cleanupClient();
            scheduleReconnect(ip, port);
        });

        client.on('close', () => {
            log('Client connection closed', 'warn');
            cleanupClient();
            scheduleReconnect(ip, port);
        });

        client.on('timeout', () => {
            log('Connection timeout', 'error');
            cleanupClient();
            scheduleReconnect(ip, port);
        });
    } catch (err: any) {
        log(`Connection failed: ${err.message}`, 'error');
        cleanupClient();
        scheduleReconnect(ip, port);
    }
}

function cleanupClient() {
    if (client) {
        try {
            client.destroy();
        } catch (_) { }
        client = null;
    }
    onStatusCb?.('disconnected');
}

function scheduleReconnect(ip: string, port: number) {
    if (reconnectTimer) return;
    log('⏳ Reconnecting in 3s...', 'info');
    reconnectTimer = setTimeout(() => {
        reconnectTimer = null;
        connectToPeer(ip, port);
    }, 3000);
}

export function sendMessage(msg: LocationMessage) {
    const payload = JSON.stringify(msg) + '\n';

    // Send to both server-side client and our outgoing client
    if (serverClient) {
        try {
            serverClient.write(payload);
        } catch (err: any) {
            log(`Send (server) error: ${err.message}`, 'error');
        }
    }

    if (client) {
        try {
            client.write(payload);
        } catch (err: any) {
            log(`Send (client) error: ${err.message}`, 'error');
        }
    }
}

export function disconnect() {
    if (reconnectTimer) {
        clearTimeout(reconnectTimer);
        reconnectTimer = null;
    }

    if (client) {
        try {
            client.destroy();
        } catch (_) { }
        client = null;
    }

    if (serverClient) {
        try {
            serverClient.destroy();
        } catch (_) { }
        serverClient = null;
    }

    if (server) {
        try {
            server.close();
        } catch (_) { }
        server = null;
    }

    buffer = '';
    onStatusCb?.('disconnected');
    log('🔴 Disconnected', 'warn');
}
