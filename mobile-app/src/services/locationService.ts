import * as Location from 'expo-location';
import { LocationMessage } from '../types/types';

let watchSubscription: Location.LocationSubscription | null = null;

type LocationCallback = (loc: LocationMessage) => void;
type LogCallback = (message: string, level: 'info' | 'error' | 'warn' | 'success') => void;

export async function requestPermissions(onLog?: LogCallback): Promise<boolean> {
    try {
        const { status } = await Location.requestForegroundPermissionsAsync();
        if (status !== 'granted') {
            onLog?.('❌ Location permission denied', 'error');
            return false;
        }
        onLog?.('✅ Location permission granted', 'success');
        return true;
    } catch (err: any) {
        onLog?.(`Permission error: ${err.message}`, 'error');
        return false;
    }
}

export function startTracking(
    deviceId: string,
    onLocation: LocationCallback,
    onLog?: LogCallback
) {
    if (watchSubscription) {
        onLog?.('Already tracking location', 'warn');
        return;
    }

    Location.watchPositionAsync(
        {
            accuracy: Location.Accuracy.BestForNavigation,
            timeInterval: 1000,
            distanceInterval: 0,
        },
        (location) => {
            const msg: LocationMessage = {
                type: 'location',
                deviceId,
                ts: Date.now(),
                lat: location.coords.latitude,
                lng: location.coords.longitude,
                speed: location.coords.speed,
                heading: location.coords.heading,
                accuracy: location.coords.accuracy,
            };
            onLocation(msg);
        }
    ).then((sub) => {
        watchSubscription = sub;
        onLog?.('🛰️ GPS tracking started', 'success');
    }).catch((err) => {
        onLog?.(`GPS tracking error: ${err.message}`, 'error');
    });
}

export function stopTracking(onLog?: LogCallback) {
    if (watchSubscription) {
        watchSubscription.remove();
        watchSubscription = null;
        onLog?.('🛰️ GPS tracking stopped', 'warn');
    }
}
