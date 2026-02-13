import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { LocationMessage, ConnectionStatus } from '../types/types';
import { haversineDistance } from '../utils/haversine';

interface StatusCardProps {
    myLocation: LocationMessage | null;
    peerLocation: LocationMessage | null;
    connectionStatus: ConnectionStatus;
    deviceId: string;
}

const StatusDot = ({ status }: { status: 'online' | 'offline' | 'connecting' }) => {
    const color =
        status === 'online' ? '#00E676' : status === 'connecting' ? '#FFC107' : '#FF5252';
    return (
        <View style={[styles.dot, { backgroundColor: color, shadowColor: color }]} />
    );
};

const formatCoord = (val: number | undefined | null): string => {
    if (val == null) return '--';
    return val.toFixed(5);
};

const formatSpeed = (val: number | undefined | null): string => {
    if (val == null || val < 0) return '--';
    return `${(val * 3.6).toFixed(1)} km/h`; // m/s to km/h
};

export default function StatusCard({
    myLocation,
    peerLocation,
    connectionStatus,
    deviceId,
}: StatusCardProps) {
    const distance =
        myLocation && peerLocation
            ? haversineDistance(myLocation.lat, myLocation.lng, peerLocation.lat, peerLocation.lng)
            : null;

    return (
        <View style={styles.container}>
            {/* My Device */}
            <View style={styles.card}>
                <View style={styles.cardHeader}>
                    <StatusDot status={myLocation ? 'online' : 'offline'} />
                    <Text style={styles.cardTitle}>MY DEVICE</Text>
                </View>
                <Text style={styles.deviceId}>{deviceId}</Text>
                <View style={styles.dataRow}>
                    <Text style={styles.label}>LAT</Text>
                    <Text style={styles.value}>{formatCoord(myLocation?.lat)}</Text>
                </View>
                <View style={styles.dataRow}>
                    <Text style={styles.label}>LNG</Text>
                    <Text style={styles.value}>{formatCoord(myLocation?.lng)}</Text>
                </View>
                <View style={styles.dataRow}>
                    <Text style={styles.label}>SPD</Text>
                    <Text style={styles.value}>{formatSpeed(myLocation?.speed)}</Text>
                </View>
            </View>

            {/* Peer Device */}
            <View style={styles.card}>
                <View style={styles.cardHeader}>
                    <StatusDot
                        status={
                            connectionStatus === 'connected'
                                ? 'online'
                                : connectionStatus === 'connecting'
                                    ? 'connecting'
                                    : 'offline'
                        }
                    />
                    <Text style={styles.cardTitle}>PEER</Text>
                </View>
                <Text style={styles.deviceId}>
                    {peerLocation?.deviceId || 'Not connected'}
                </Text>
                <View style={styles.dataRow}>
                    <Text style={styles.label}>LAT</Text>
                    <Text style={styles.value}>{formatCoord(peerLocation?.lat)}</Text>
                </View>
                <View style={styles.dataRow}>
                    <Text style={styles.label}>LNG</Text>
                    <Text style={styles.value}>{formatCoord(peerLocation?.lng)}</Text>
                </View>
                <View style={styles.dataRow}>
                    <Text style={styles.label}>SPD</Text>
                    <Text style={styles.value}>{formatSpeed(peerLocation?.speed)}</Text>
                </View>
            </View>

            {/* Distance */}
            {distance !== null && (
                <View style={styles.distanceBadge}>
                    <Text style={styles.distanceText}>
                        📏 {distance < 1000 ? `${distance.toFixed(0)} m` : `${(distance / 1000).toFixed(2)} km`}
                    </Text>
                </View>
            )}
        </View>
    );
}

const styles = StyleSheet.create({
    container: {
        flexDirection: 'row',
        gap: 10,
        flexWrap: 'wrap',
    },
    card: {
        flex: 1,
        minWidth: 140,
        backgroundColor: '#1A1A2E',
        borderRadius: 14,
        padding: 14,
        borderWidth: 1,
        borderColor: '#2A2A4A',
    },
    cardHeader: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: 8,
        marginBottom: 6,
    },
    dot: {
        width: 10,
        height: 10,
        borderRadius: 5,
        shadowOffset: { width: 0, height: 0 },
        shadowOpacity: 0.8,
        shadowRadius: 4,
        elevation: 4,
    },
    cardTitle: {
        color: '#FFFFFF',
        fontSize: 12,
        fontWeight: '700',
        letterSpacing: 1.5,
    },
    deviceId: {
        color: '#7B7BA0',
        fontSize: 10,
        marginBottom: 10,
        fontFamily: 'monospace',
    },
    dataRow: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        marginBottom: 4,
    },
    label: {
        color: '#5B5B8A',
        fontSize: 11,
        fontWeight: '600',
    },
    value: {
        color: '#E0E0FF',
        fontSize: 11,
        fontFamily: 'monospace',
    },
    distanceBadge: {
        width: '100%',
        backgroundColor: '#0D253F',
        borderRadius: 10,
        paddingVertical: 8,
        alignItems: 'center',
        borderWidth: 1,
        borderColor: '#1A4A7A',
    },
    distanceText: {
        color: '#4FC3F7',
        fontSize: 14,
        fontWeight: '600',
    },
});
