import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import type { ApiError } from '../api/errors';

interface ErrorAlertProps {
  error: ApiError | undefined | null;
  /** Shows a Retry button when provided. */
  onRetry?: () => void;
  /** Field errors that could not be attached to a visible input are listed here. */
  unmappedFieldErrors?: Readonly<Record<string, string>>;
}

/** Renders a normalised ProblemDetail: title, detail, unmapped field errors and the request id. */
export function ErrorAlert({ error, onRetry, unmappedFieldErrors }: ErrorAlertProps) {
  if (!error) {
    return null;
  }
  const extra = Object.entries(unmappedFieldErrors ?? {});
  return (
    <Alert
      severity="error"
      sx={{ mb: 2 }}
      action={
        onRetry ? (
          <Button color="inherit" size="small" onClick={onRetry}>
            Retry
          </Button>
        ) : undefined
      }
    >
      <AlertTitle>{error.title}</AlertTitle>
      {error.detail}
      {extra.length > 0 && (
        <ul style={{ margin: '8px 0 0', paddingLeft: 20 }}>
          {extra.map(([field, message]) => (
            <li key={field}>
              <strong>{field}</strong>: {message}
            </li>
          ))}
        </ul>
      )}
      {error.requestId && (
        <Typography variant="caption" component="div" sx={{ mt: 1, opacity: 0.8 }}>
          Reference: {error.requestId}
        </Typography>
      )}
    </Alert>
  );
}
