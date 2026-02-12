import React, { useEffect, useCallback, useState } from 'react';
import {
  StyleSheet,
  Text,
  View,
  KeyboardAvoidingView,
  Platform,
  StatusBar,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useAppStore } from '@/store/useAppStore';
import StatusCard from '@/components/StatusCard';
import ConnectionBar from '@/components/ConnectionBar';
import LogViewer from '@/components/LogViewer';
import * as socketService from '@/services/socketService';
import * as locationService from '@/services/locationService';
import { LocationMessage } from '@/types/types';

export default function DriveSmartHome() {
  const {
    deviceId,
    peerIp,
    port,
    connectionStatus,
    myLocation,
    peerLocation,
    logs,
    setPeerIp,
    setPort,
    setConnectionStatus,
    setMyLocation,
    setPeerLocation,
    addLog,
    clearLogs,
  } = useAppStore();

  const [portStr, setPortStr] = useState(port.toString());

  useEffect(() => {
    socketService.setCallbacks(
      (msg: LocationMessage) => {
        setPeerLocation(msg);
      },
      (status) => {
        setConnectionStatus(status === 'connected' ? 'connected' : 'disconnected');
      },
      (message, level) => {
        addLog(message, level);
      }
    );
  }, [setPeerLocation, setConnectionStatus, addLog]);

  useEffect(() => {
    let mounted = true;

    const initGps = async () => {
      const granted = await locationService.requestPermissions((msg, level) =>
        addLog(msg, level)
      );
      if (granted && mounted) {
        locationService.startTracking(
          deviceId,
          (loc) => {
            setMyLocation(loc);
            socketService.sendMessage(loc);
          },
          (msg, level) => addLog(msg, level)
        );
      }
    };

    initGps();
    addLog(`Device ID: ${deviceId}`, 'info');

    return () => {
      mounted = false;
      locationService.stopTracking((msg, level) => addLog(msg, level));
    };
  }, [deviceId, setMyLocation, addLog]);

  const handleConnect = useCallback(() => {
    if (!peerIp.trim()) {
      addLog('Please enter peer IP address', 'error');
      return;
    }
    const p = parseInt(portStr, 10) || 9090;
    setPort(p);
    setConnectionStatus('connecting');
    addLog(`Starting server on port ${p}...`, 'info');
    socketService.startServer(p);
    addLog(`Connecting to peer ${peerIp}:${p}...`, 'info');
    socketService.connectToPeer(peerIp.trim(), p);
  }, [peerIp, portStr, setPort, setConnectionStatus, addLog]);

  const handleDisconnect = useCallback(() => {
    socketService.disconnect();
    setConnectionStatus('disconnected');
  }, [setConnectionStatus]);

  const isConnected = connectionStatus === 'connected' || connectionStatus === 'connecting';

  return (
    <View style={styles.container}>
      <StatusBar barStyle="light-content" />
      <SafeAreaView style={styles.safeArea} edges={['top', 'left', 'right']}>
        <View style={styles.header}>
          <Text style={styles.title}>DriveSmart</Text>
          <Text style={styles.subtitle}>P2P LOCATION SHARE</Text>
        </View>

        <StatusCard
          myLocation={myLocation}
          peerLocation={peerLocation}
          connectionStatus={connectionStatus}
          deviceId={deviceId}
        />

        <ConnectionBar
          peerIp={peerIp}
          port={portStr}
          isConnected={isConnected}
          onIpChange={setPeerIp}
          onPortChange={setPortStr}
          onConnect={handleConnect}
          onDisconnect={handleDisconnect}
        />
      </SafeAreaView>

      <KeyboardAvoidingView
        style={styles.logSection}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <LogViewer logs={logs} onClear={clearLogs} />
      </KeyboardAvoidingView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0A0A1A',
  },
  safeArea: {
    paddingHorizontal: 16,
    paddingTop: 8,
    gap: 12,
  },
  header: {
    paddingVertical: 10,
  },
  title: {
    color: '#FFFFFF',
    fontSize: 28,
    fontWeight: '900',
    letterSpacing: 1,
  },
  subtitle: {
    color: '#5B5B8A',
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 3,
    marginTop: 2,
  },
  logSection: {
    flex: 1,
    padding: 16,
    paddingTop: 8,
  },
});
