import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { toApiError } from '../../api/errors';
import { listResourceRequests, performRequestAction } from '../../api/requests';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { PageHeader } from '../../components/PageHeader';
import { useNotify } from '../../components/useNotify';
import { useApiData } from '../../hooks/useApiData';
import type { RequestAction, ResourceRequest } from '../../types/api';
import { RequestsTable, type Column } from './RequestsTable';

const loadInUse = (signal: AbortSignal) => listResourceRequests('requester', ['DEPLOYED'], signal);
const loadRequestedByMe = (signal: AbortSignal) =>
  listResourceRequests('requester', ['PENDING'], signal);
const loadReceived = (signal: AbortSignal) => listResourceRequests('owner', ['PENDING'], signal);

interface ActionMeta {
  label: string;
  confirmLabel: string;
  confirmTitle: string;
  confirmColor: 'primary' | 'error' | 'warning' | 'success';
  message: (r: ResourceRequest) => string;
  success: (r: ResourceRequest) => string;
}

const ACTIONS: Record<RequestAction, ActionMeta> = {
  return: {
    label: 'Return',
    confirmLabel: 'Return',
    confirmTitle: 'Return resource?',
    confirmColor: 'primary',
    message: (r) => `Mark ${r.resourceName} as returned from incident ${r.incidentId}?`,
    success: (r) => `${r.resourceName} returned`,
  },
  cancel: {
    label: 'Cancel',
    confirmLabel: 'Cancel request',
    confirmTitle: 'Cancel request?',
    confirmColor: 'warning',
    message: (r) => `Cancel your request for ${r.resourceName} (incident ${r.incidentId})?`,
    success: (r) => `Request #${String(r.requestId)} cancelled`,
  },
  deploy: {
    label: 'Deploy',
    confirmLabel: 'Deploy',
    confirmTitle: 'Deploy resource?',
    confirmColor: 'success',
    message: (r) => `Deploy ${r.resourceName} to incident ${r.incidentId} for ${r.requestedBy}?`,
    success: (r) => `${r.resourceName} deployed to ${r.incidentId}`,
  },
  reject: {
    label: 'Reject',
    confirmLabel: 'Reject',
    confirmTitle: 'Reject request?',
    confirmColor: 'error',
    message: (r) => `Reject ${r.requestedBy}'s request for ${r.resourceName}?`,
    success: (r) => `Request #${String(r.requestId)} rejected`,
  },
};

function ResourceCell({ request }: { request: ResourceRequest }) {
  return (
    <>
      {request.resourceName}{' '}
      <Typography component="span" variant="caption" color="text.secondary">
        #{request.resourceId}
      </Typography>
    </>
  );
}

function IncidentCell({ request }: { request: ResourceRequest }) {
  return (
    <>
      {request.incidentId}
      <Typography variant="caption" color="text.secondary" component="div">
        {request.incidentDescription}
      </Typography>
    </>
  );
}

const ID: Column = { header: 'ID', render: (r) => r.requestId };
const RESOURCE: Column = { header: 'Resource', render: (r) => <ResourceCell request={r} /> };
const INCIDENT: Column = { header: 'Incident', render: (r) => <IncidentCell request={r} /> };
const OWNER: Column = { header: 'Owner', render: (r) => r.ownerUsername };
const REQUESTED_BY: Column = { header: 'Requested by', render: (r) => r.requestedBy };
const DEPLOY_DATE: Column = { header: 'Deployed', render: (r) => r.deployDate ?? '—' };
const RETURN_BY: Column = { header: 'Expected return', render: (r) => r.expectedReturnDate };

interface PendingAction {
  request: ResourceRequest;
  action: RequestAction;
}

export function ResourceStatusPage() {
  const notify = useNotify();
  const inUse = useApiData(loadInUse);
  const requestedByMe = useApiData(loadRequestedByMe);
  const received = useApiData(loadReceived);
  const [pending, setPending] = useState<PendingAction | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [busy, setBusy] = useState(false);

  const reloadAll = () => {
    inUse.reload();
    requestedByMe.reload();
    received.reload();
  };

  const confirm = async () => {
    if (!pending) return;
    const { request, action } = pending;
    setBusy(true);
    try {
      const updated = await performRequestAction(request.requestId, action);
      notify(ACTIONS[action].success(updated), 'success');
    } catch (caught) {
      notify(toApiError(caught).detail, 'error');
    } finally {
      setBusy(false);
      setDialogOpen(false);
      reloadAll();
    }
  };

  const actionButton = (
    request: ResourceRequest,
    action: RequestAction,
    disabledReason?: string,
  ) => {
    const button = (
      <Button
        size="small"
        variant={action === 'deploy' ? 'contained' : 'outlined'}
        color={action === 'reject' ? 'error' : 'primary'}
        disabled={busy || disabledReason !== undefined}
        onClick={() => {
          setPending({ request, action });
          setDialogOpen(true);
        }}
        aria-label={`${ACTIONS[action].label} request ${String(request.requestId)}`}
        sx={{ ml: 1 }}
      >
        {ACTIONS[action].label}
      </Button>
    );
    return disabledReason ? (
      <Tooltip title={disabledReason}>
        {/* Disabled buttons don't fire events; the wrapper keeps the tooltip reachable. */}
        <span>{button}</span>
      </Tooltip>
    ) : (
      button
    );
  };

  const meta = pending ? ACTIONS[pending.action] : null;

  return (
    <>
      <PageHeader
        title="Resource Status"
        subtitle="Resources you are using, requests you made, and requests for your resources."
      />

      <RequestsTable
        title="Resources in use"
        emptyMessage="You are not using any resources."
        columns={[ID, RESOURCE, INCIDENT, OWNER, DEPLOY_DATE, RETURN_BY]}
        state={inUse}
        renderActions={(r) => actionButton(r, 'return')}
      />
      <RequestsTable
        title="Resources requested by me"
        emptyMessage="You have no pending requests."
        columns={[ID, RESOURCE, INCIDENT, OWNER, RETURN_BY]}
        state={requestedByMe}
        renderActions={(r) => actionButton(r, 'cancel')}
      />
      <RequestsTable
        title="Requests received"
        emptyMessage="Nobody has requested your resources."
        columns={[ID, RESOURCE, INCIDENT, REQUESTED_BY, RETURN_BY]}
        state={received}
        renderActions={(r) => (
          <Box component="span">
            {actionButton(
              r,
              'deploy',
              r.resourceAvailable ? undefined : 'Resource is currently deployed elsewhere',
            )}
            {actionButton(r, 'reject')}
          </Box>
        )}
      />

      <ConfirmDialog
        open={dialogOpen}
        title={meta?.confirmTitle ?? ''}
        message={pending && meta ? meta.message(pending.request) : ''}
        confirmLabel={meta?.confirmLabel ?? 'Confirm'}
        confirmColor={meta?.confirmColor ?? 'primary'}
        busy={busy}
        onConfirm={() => {
          void confirm();
        }}
        onCancel={() => {
          setDialogOpen(false);
        }}
      />
    </>
  );
}
