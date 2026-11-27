import SearchIcon from '@mui/icons-material/Search';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Grid from '@mui/material/Grid';
import InputAdornment from '@mui/material/InputAdornment';
import LinearProgress from '@mui/material/LinearProgress';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TablePagination from '@mui/material/TablePagination';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useCallback, useState, type SubmitEvent } from 'react';
import { useSearchParams } from 'react-router';
import { listMyIncidents } from '../../api/incidents';
import { getEsfs } from '../../api/reference';
import { searchResources } from '../../api/resources';
import { useAuth } from '../../auth/useAuth';
import { EmptyState } from '../../components/EmptyState';
import { ErrorAlert } from '../../components/ErrorAlert';
import { Loading } from '../../components/Loading';
import { PageHeader } from '../../components/PageHeader';
import { useNotify } from '../../components/useNotify';
import { useApiData } from '../../hooks/useApiData';
import type { Esf, Incident, ResourceRequest, ResourceSearchResult } from '../../types/api';
import { availabilityLabel, formatCost, formatDistance } from '../../utils/format';
import { validateSearchDistance, type SearchFilterValues } from '../../validation/request';
import { RequestDialog } from './RequestDialog';
import { PAGE_SIZE, toSearchParams } from './searchParams';
import { resolveRowAction, type RowAction } from './rowAction';

interface FilterOptions {
  esfs: Esf[];
  incidents: Incident[];
}

async function loadFilterOptions(signal: AbortSignal): Promise<FilterOptions> {
  const [esfs, incidents] = await Promise.all([getEsfs(signal), listMyIncidents(signal)]);
  return { esfs, incidents };
}

interface Query {
  filters: SearchFilterValues;
  page: number;
}

interface Pending {
  resource: ResourceSearchResult;
  action: RowAction;
}

export function SearchResourcesPage() {
  const { user } = useAuth();
  const notify = useNotify();
  const [searchParams] = useSearchParams();
  const options = useApiData(loadFilterOptions);

  const [filters, setFilters] = useState<SearchFilterValues>(() => ({
    keyword: '',
    esf: '',
    incidentId: searchParams.get('incidentId') ?? '',
    distanceKm: '',
  }));
  const [distanceError, setDistanceError] = useState<string | undefined>();
  // The submitted search. With no filters every resource is listed (FEATURES.md F-5 AC1).
  const [query, setQuery] = useState<Query>(() => ({ filters, page: 0 }));
  const [pending, setPending] = useState<Pending | null>(null);

  const loadResults = useCallback(
    (signal: AbortSignal) => searchResources(toSearchParams(query.filters, query.page), signal),
    [query],
  );
  const results = useApiData(loadResults);

  const update = (patch: Partial<SearchFilterValues>) => {
    setFilters((previous) => {
      const next = { ...previous, ...patch };
      // "Within N km" is only meaningful with an incident.
      if (next.incidentId === '') next.distanceKm = '';
      return next;
    });
    setDistanceError(undefined);
  };

  const onSearch = (event: SubmitEvent<HTMLFormElement>) => {
    event.preventDefault();
    const problem = validateSearchDistance(filters);
    setDistanceError(problem);
    if (!problem) {
      setQuery({ filters, page: 0 });
    }
  };

  const onReset = () => {
    const cleared: SearchFilterValues = { keyword: '', esf: '', incidentId: '', distanceKm: '' };
    setFilters(cleared);
    setDistanceError(undefined);
    setQuery({ filters: cleared, page: 0 });
  };

  const onCompleted = (request: ResourceRequest) => {
    setPending(null);
    notify(
      request.status === 'DEPLOYED'
        ? `${request.resourceName} deployed to ${request.incidentId}`
        : `Request #${String(request.requestId)} sent for ${request.resourceName}`,
      'success',
    );
    results.reload();
  };

  const searchedIncidentId = query.filters.incidentId;
  const searchedIncident = options.data?.incidents.find((i) => i.incidentId === searchedIncidentId);
  const withIncident = searchedIncidentId !== '';
  const page = results.data;

  return (
    <>
      <PageHeader title="Search Resources" />

      <Paper sx={{ p: { xs: 2, sm: 3 }, mb: 3 }}>
        <ErrorAlert error={options.error} onRetry={options.reload} />
        <Box component="form" noValidate onSubmit={onSearch} aria-label="Search filters">
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                id="keyword"
                label="Keyword"
                value={filters.keyword}
                onChange={(event) => {
                  update({ keyword: event.target.value });
                }}
                fullWidth
                helperText="Matches name, model or capability"
              />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                select
                id="esf"
                label="ESF"
                value={filters.esf}
                onChange={(event) => {
                  update({ esf: event.target.value });
                }}
                fullWidth
                disabled={!options.data}
                helperText="Primary or additional ESF"
              >
                <MenuItem value="">
                  <em>Any ESF</em>
                </MenuItem>
                {(options.data?.esfs ?? []).map((esf) => (
                  <MenuItem key={esf.esfNumber} value={String(esf.esfNumber)}>
                    #{esf.esfNumber} {esf.description}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                select
                id="incidentId"
                label="Incident"
                value={options.data ? filters.incidentId : ''}
                onChange={(event) => {
                  update({ incidentId: event.target.value });
                }}
                fullWidth
                disabled={!options.data}
                helperText={
                  options.data?.incidents.length === 0
                    ? 'You have no incidents yet'
                    : 'Optional; enables distance and Request/Deploy'
                }
              >
                <MenuItem value="">
                  <em>No incident</em>
                </MenuItem>
                {(options.data?.incidents ?? []).map((incident) => (
                  <MenuItem key={incident.incidentId} value={incident.incidentId}>
                    ({incident.incidentId}) {incident.description}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                id="distanceKm"
                label="Within"
                value={filters.distanceKm}
                onChange={(event) => {
                  update({ distanceKm: event.target.value });
                }}
                fullWidth
                disabled={filters.incidentId === ''}
                error={Boolean(distanceError)}
                helperText={distanceError ?? 'Kilometres of the incident (optional)'}
                slotProps={{
                  htmlInput: { inputMode: 'decimal' },
                  input: { endAdornment: <InputAdornment position="end">km</InputAdornment> },
                }}
              />
            </Grid>
          </Grid>
          <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 2, mt: 2 }}>
            <Button onClick={onReset}>Reset</Button>
            <Button type="submit" variant="contained" startIcon={<SearchIcon />}>
              Search
            </Button>
          </Box>
        </Box>
      </Paper>

      <Paper component="section" aria-labelledby="results-heading">
        <Box sx={{ p: 2, display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
          <Typography variant="h6" component="h2" id="results-heading">
            Results
          </Typography>
          {page && (
            <Typography color="text.secondary" variant="body2">
              {page.totalElements} found
            </Typography>
          )}
          {withIncident && (
            <Chip
              size="small"
              color="secondary"
              label={`Incident ${searchedIncidentId}${searchedIncident ? `: ${searchedIncident.description}` : ''}`}
            />
          )}
        </Box>
        {results.loading && page && <LinearProgress aria-label="Refreshing results" />}
        <Box sx={{ px: 2 }}>
          <ErrorAlert error={results.error} onRetry={results.reload} />
        </Box>
        {results.loading && !page && <Loading label="Searching…" />}
        {page?.content.length === 0 && <EmptyState message="No resources match your filters." />}
        {page && page.content.length > 0 && (
          <>
            <TableContainer>
              <Table size="small" aria-labelledby="results-heading">
                <TableHead>
                  <TableRow>
                    <TableCell>ID</TableCell>
                    <TableCell>Name</TableCell>
                    <TableCell>Owner</TableCell>
                    <TableCell>Cost</TableCell>
                    <TableCell>Status</TableCell>
                    {withIncident && <TableCell align="right">Distance</TableCell>}
                    {withIncident && <TableCell align="right">Action</TableCell>}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {page.content.map((row) => {
                    const action = resolveRowAction(row, user?.username ?? '');
                    return (
                      <TableRow key={row.resourceId} hover>
                        <TableCell>{row.resourceId}</TableCell>
                        <TableCell component="th" scope="row">
                          {row.resourceName}
                        </TableCell>
                        <TableCell>{row.ownerName}</TableCell>
                        <TableCell>{formatCost(row.cost, row.costUnit)}</TableCell>
                        <TableCell>
                          <Chip
                            size="small"
                            variant="outlined"
                            color={row.available ? 'success' : 'warning'}
                            label={availabilityLabel(row)}
                          />
                        </TableCell>
                        {withIncident && (
                          <TableCell align="right">{formatDistance(row.distanceKm)}</TableCell>
                        )}
                        {withIncident && (
                          <TableCell align="right">
                            <Button
                              size="small"
                              variant={action === 'deploy' ? 'contained' : 'outlined'}
                              onClick={() => {
                                setPending({ resource: row, action });
                              }}
                              aria-label={`${action === 'deploy' ? 'Deploy' : 'Request'} ${row.resourceName}`}
                            >
                              {action === 'deploy' ? 'Deploy' : 'Request'}
                            </Button>
                          </TableCell>
                        )}
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
            <TablePagination
              component="div"
              count={page.totalElements}
              page={page.page}
              rowsPerPage={PAGE_SIZE}
              rowsPerPageOptions={[]}
              onPageChange={(_event, nextPage) => {
                setQuery((previous) => ({ ...previous, page: nextPage }));
              }}
            />
          </>
        )}
      </Paper>

      {pending && (
        <RequestDialog
          key={`${String(pending.resource.resourceId)}-${pending.action}`}
          resource={pending.resource}
          incidentId={searchedIncidentId}
          action={pending.action}
          onClose={() => {
            setPending(null);
          }}
          onCompleted={onCompleted}
        />
      )}
    </>
  );
}
