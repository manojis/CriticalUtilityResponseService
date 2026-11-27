import Alert from '@mui/material/Alert';
import Snackbar from '@mui/material/Snackbar';
import { useCallback, useState, type ReactNode } from 'react';
import { NotificationContext, type NotificationSeverity } from './notificationContext';

interface Notification {
  key: number;
  message: string;
  severity: NotificationSeverity;
}

/** App-wide success/error snackbar. Newer messages replace older ones. */
export function NotificationProvider({ children }: { children: ReactNode }) {
  const [notification, setNotification] = useState<Notification | null>(null);
  const [open, setOpen] = useState(false);

  const notify = useCallback((message: string, severity: NotificationSeverity = 'success') => {
    setNotification({ key: Date.now(), message, severity });
    setOpen(true);
  }, []);

  const handleClose = (_event?: unknown, reason?: string) => {
    if (reason !== 'clickaway') {
      setOpen(false);
    }
  };

  return (
    <NotificationContext value={notify}>
      {children}
      <Snackbar
        key={notification?.key}
        open={open}
        autoHideDuration={notification?.severity === 'error' ? 10_000 : 6_000}
        onClose={handleClose}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert
          onClose={handleClose}
          severity={notification?.severity ?? 'info'}
          variant="filled"
          sx={{ width: '100%' }}
        >
          {notification?.message}
        </Alert>
      </Snackbar>
    </NotificationContext>
  );
}
