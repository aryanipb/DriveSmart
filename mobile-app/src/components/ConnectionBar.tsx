import React from 'react';
import { View, TextInput, TouchableOpacity, Text, StyleSheet } from 'react-native';

interface ConnectionBarProps {
    peerIp: string;
    port: string;
    isConnected: boolean;
    onIpChange: (ip: string) => void;
    onPortChange: (port: string) => void;
    onConnect: () => void;
    onDisconnect: () => void;
}

export default function ConnectionBar({
    peerIp,
    port,
    isConnected,
    onIpChange,
    onPortChange,
    onConnect,
    onDisconnect,
}: ConnectionBarProps) {
    return (
        <View style={styles.container}>
            <View style={styles.inputRow}>
                <TextInput
                    style={[styles.input, styles.ipInput]}
                    placeholder="Peer IP (e.g. 192.168.1.5)"
                    placeholderTextColor="#5B5B8A"
                    value={peerIp}
                    onChangeText={onIpChange}
                    keyboardType="numeric"
                    editable={!isConnected}
                />
                <TextInput
                    style={[styles.input, styles.portInput]}
                    placeholder="Port"
                    placeholderTextColor="#5B5B8A"
                    value={port}
                    onChangeText={onPortChange}
                    keyboardType="numeric"
                    editable={!isConnected}
                />
            </View>
            <TouchableOpacity
                style={[styles.button, isConnected ? styles.disconnectBtn : styles.connectBtn]}
                onPress={isConnected ? onDisconnect : onConnect}
                activeOpacity={0.7}
            >
                <Text style={styles.buttonText}>
                    {isConnected ? '⬛ DISCONNECT' : '🟢 CONNECT'}
                </Text>
            </TouchableOpacity>
        </View>
    );
}

const styles = StyleSheet.create({
    container: {
        backgroundColor: '#1A1A2E',
        borderRadius: 14,
        padding: 14,
        borderWidth: 1,
        borderColor: '#2A2A4A',
        gap: 10,
    },
    inputRow: {
        flexDirection: 'row',
        gap: 10,
    },
    input: {
        backgroundColor: '#0F0F23',
        borderRadius: 10,
        paddingHorizontal: 14,
        paddingVertical: 10,
        color: '#E0E0FF',
        fontSize: 14,
        fontFamily: 'monospace',
        borderWidth: 1,
        borderColor: '#2A2A4A',
    },
    ipInput: {
        flex: 3,
    },
    portInput: {
        flex: 1,
    },
    button: {
        borderRadius: 10,
        paddingVertical: 12,
        alignItems: 'center',
    },
    connectBtn: {
        backgroundColor: '#00695C',
    },
    disconnectBtn: {
        backgroundColor: '#B71C1C',
    },
    buttonText: {
        color: '#FFFFFF',
        fontSize: 14,
        fontWeight: '700',
        letterSpacing: 1,
    },
});
