import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import TextField from '@mui/material/TextField';
import { useId, useMemo, useState, type SubmitEvent } from 'react';
import { toApiError, type ApiError } from '../../api/errors';
import { createResourceRequest } from '../../api/requests';
import { ErrorAlert } from '../../components/ErrorAlert';
import type { ResourceRequest, ResourceSearchResult } from '../../types/api';
import { todayIso } from '../../utils/date';
import { validateExpectedReturnDate } from '../../validation/request';
import type { RowAction } from './rowAction';

interface RequestDialogProps {
  resource: ResourceSearchResult;
  incidentId: string;
  action: RowAction;
  onClose: () => void;
  onCompleted: (request: ResourceRequest) => void;
}

/** Asks for the expected return date, then creates the request (or deploys immediately). */
export function RequestDialog({
  resource,
  incidentId,
  action,
  onClose,
  onCompleted,
}: RequestDialogProps) {
  const today = useMemo(() => todayIso(), []);
  const titleId = useId();
  const [date, setDate] = useState('');
  const [fieldError, setFieldError] = useState<string | undefined>();
  const [error, setError] = useState<ApiError | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const verb = action === 'deploy' ? 'Deploy' : 'Request';

  const submit = async (event: SubmitEvent<HTMLFormElement>) => {
    event.preventDefault();
    const problem = validateExpectedReturnDate(date, today);
    setFieldError(problem);
    if (problem) {
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const request = await createResourceRequest({
        resourceId: resource.resourceId,
        incidentId,
        expectedReturnDate: date,
        deployImmediately: action === 'deploy',
      });
      onCompleted(request);
    } catch (caught) {
      const apiError = toApiError(caught);
      setError(apiError);
      setFieldError(apiError.fieldErrors.expectedReturnDate);
      setSubmitting(false);
    }
  };

  return (
    <Dialog
      open
      onClose={submitting ? undefined : onClose}
      aria-labelledby={titleId}
      fullWidth
      maxWidth="xs"
      slotProps={{ paper: { component: 'form', onSubmit: submit, noValidate: true } as object }}
    >
      <DialogTitle id={titleId}>
        {verb} {resource.resourceName}
      </DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          {action === 'deploy'
            ? `Deploy your resource #${String(resource.resourceId)} to incident ${incidentId} now.`
            : `Ask ${resource.ownerName} for resource #${String(resource.resourceId)} for incident ${incidentId}.`}
        </DialogContentText>
        <ErrorAlert error={error} />
        <TextField
          id="expectedReturnDate"
          label="Expected return date"
          type="date"
          value={date}
          onChange={(event) => {
            setDate(event.target.value);
            setFieldError(undefined);
          }}
          required
          fullWidth
          error={Boolean(fieldError)}
          helperText={fieldError ?? 'Today or later'}
          slotProps={{ inputLabel: { shrink: true }, htmlInput: { min: today } }}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={submitting}>
          Cancel
        </Button>
        <Button type="submit" variant="contained" loading={submitting}>
          {verb}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
