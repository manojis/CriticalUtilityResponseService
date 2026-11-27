import Box from '@mui/material/Box';
import LinearProgress from '@mui/material/LinearProgress';
import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { useId, type ReactNode } from 'react';
import type { ApiData } from '../../hooks/useApiData';
import { EmptyState } from '../../components/EmptyState';
import { ErrorAlert } from '../../components/ErrorAlert';
import { Loading } from '../../components/Loading';
import type { ResourceRequest } from '../../types/api';

export interface Column {
  header: string;
  render: (request: ResourceRequest) => ReactNode;
}

interface RequestsTableProps {
  title: string;
  emptyMessage: string;
  columns: Column[];
  state: ApiData<ResourceRequest[]>;
  renderActions: (request: ResourceRequest) => ReactNode;
}

/** One of the three Resource Status tables, with loading, error and empty states. */
export function RequestsTable({
  title,
  emptyMessage,
  columns,
  state,
  renderActions,
}: RequestsTableProps) {
  const headingId = useId();
  const rows = state.data;
  return (
    <Paper component="section" aria-labelledby={headingId} sx={{ mb: 4 }}>
      <Box sx={{ p: 2, display: 'flex', alignItems: 'baseline', gap: 2 }}>
        <Typography variant="h6" component="h2" id={headingId}>
          {title}
        </Typography>
        {rows && (
          <Typography variant="body2" color="text.secondary">
            {rows.length}
          </Typography>
        )}
      </Box>
      {state.loading && rows && <LinearProgress aria-label={`Refreshing ${title}`} />}
      <Box sx={{ px: 2 }}>
        <ErrorAlert error={state.error} onRetry={state.reload} />
      </Box>
      {state.loading && !rows && <Loading label={`Loading ${title.toLowerCase()}…`} />}
      {rows?.length === 0 && <EmptyState message={emptyMessage} />}
      {rows && rows.length > 0 && (
        <TableContainer>
          <Table size="small" aria-labelledby={headingId}>
            <TableHead>
              <TableRow>
                {columns.map((column) => (
                  <TableCell key={column.header}>{column.header}</TableCell>
                ))}
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((request) => (
                <TableRow key={request.requestId} hover>
                  {columns.map((column) => (
                    <TableCell key={column.header}>{column.render(request)}</TableCell>
                  ))}
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    {renderActions(request)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Paper>
  );
}
