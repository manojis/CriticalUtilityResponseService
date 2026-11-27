import { useContext } from 'react';
import { NotificationContext, type Notify } from './notificationContext';

export function useNotify(): Notify {
  const notify = useContext(NotificationContext);
  if (notify === null) {
    throw new Error('useNotify must be used inside <NotificationProvider>');
  }
  return notify;
}
