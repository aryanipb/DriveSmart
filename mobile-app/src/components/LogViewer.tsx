import React, { useRef, useEffect } from 'react';
import { View, Text, FlatList, StyleSheet, TouchableOpacity } from 'react-native';
import { LogEntry } from '../types/types';

interface LogViewerProps {
    logs: LogEntry[];
    onClear: () => void;
}

const levelColors: Record<LogEntry['level'], string> = {
    info: '#8B8BBA',
    error: '#FF5252',
    warn: '#FFC107',
    success: '#00E676',
};

const formatTime = (ts: number): string => {
    const d = new Date(ts);
    return `${d.getHours().toString().padStart(2, '0')}:${d
        .getMinutes()
        .toString()
        .padStart(2, '0')}:${d.getSeconds().toString().padStart(2, '0')}`;
};

const LogItem = React.memo(({ item }: { item: LogEntry }) => (
    <View style={styles.logRow}>
        <Text style={styles.logTime}>{formatTime(item.timestamp)}</Text>
        <Text style={[styles.logMessage, { color: levelColors[item.level] }]}>
            {item.message}
        </Text>
    </View>
));

export default function LogViewer({ logs, onClear }: LogViewerProps) {
    const flatListRef = useRef<FlatList>(null);

    useEffect(() => {
        if (logs.length > 0) {
            setTimeout(() => {
                flatListRef.current?.scrollToEnd({ animated: true });
            }, 100);
        }
    }, [logs.length]);

    return (
        <View style={styles.container}>
            <View style={styles.header}>
                <Text style={styles.title}>LOGS</Text>
                <TouchableOpacity onPress={onClear} activeOpacity={0.7}>
                    <Text style={styles.clearBtn}>CLEAR</Text>
                </TouchableOpacity>
            </View>
            <FlatList
                ref={flatListRef}
                data={logs}
                keyExtractor={(item) => item.id}
                renderItem={({ item }) => <LogItem item={item} />}
                style={styles.list}
                contentContainerStyle={styles.listContent}
                initialNumToRender={30}
                maxToRenderPerBatch={20}
            />
        </View>
    );
}

const styles = StyleSheet.create({
    container: {
        flex: 1,
        backgroundColor: '#0D0D1A',
        borderRadius: 14,
        borderWidth: 1,
        borderColor: '#2A2A4A',
        overflow: 'hidden',
    },
    header: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        paddingHorizontal: 14,
        paddingVertical: 10,
        borderBottomWidth: 1,
        borderBottomColor: '#1A1A2E',
    },
    title: {
        color: '#5B5B8A',
        fontSize: 12,
        fontWeight: '700',
        letterSpacing: 1.5,
    },
    clearBtn: {
        color: '#FF5252',
        fontSize: 11,
        fontWeight: '600',
        letterSpacing: 1,
    },
    list: {
        flex: 1,
    },
    listContent: {
        paddingHorizontal: 12,
        paddingVertical: 6,
    },
    logRow: {
        flexDirection: 'row',
        paddingVertical: 3,
        gap: 10,
    },
    logTime: {
        color: '#3A3A5A',
        fontSize: 11,
        fontFamily: 'monospace',
        minWidth: 60,
    },
    logMessage: {
        fontSize: 11,
        fontFamily: 'monospace',
        flex: 1,
    },
});
