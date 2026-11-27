import { createContext } from 'react';

export type NotificationSeverity = 'success' | 'info' | 'warning' | 'error';

export type Notify = (message: string, severity?: NotificationSeverity) => void;

export const NotificationContext = createContext<Notify | null>(null);
