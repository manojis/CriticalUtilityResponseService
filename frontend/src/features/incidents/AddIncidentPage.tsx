import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Grid from '@mui/material/Grid';
import Link from '@mui/material/Link';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useMemo, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { Link as RouterLink } from 'react-router';
import { toApiError, type ApiError } from '../../api/errors';
import { createIncident } from '../../api/incidents';
import { getDeclarations } from '../../api/reference';
import { ErrorAlert } from '../../components/ErrorAlert';
import { Loading } from '../../components/Loading';
import { PageHeader } from '../../components/PageHeader';
import { applyServerFieldErrors, validatorResolver } from '../../forms/formUtils';
import { useApiData } from '../../hooks/useApiData';
import type { Incident } from '../../types/api';
import { todayIso } from '../../utils/date';
import {
  DESCRIPTION_MAX,
  toCreateIncidentRequest,
  validateIncident,
  type IncidentFormValues,
} from '../../validation/incident';

const FIELDS: (keyof IncidentFormValues)[] = [
  'declarationAbbreviation',
  'incidentDate',
  'description',
  'latitude',
  'longitude',
];

function emptyValues(today: string): IncidentFormValues {
  return {
    declarationAbbreviation: '',
    incidentDate: today,
    description: '',
    latitude: '',
    longitude: '',
  };
}

export function AddIncidentPage() {
  const today = useMemo(() => todayIso(), []);
  const declarations = useApiData(getDeclarations);
  const [submitError, setSubmitError] = useState<ApiError | null>(null);
  const [unmapped, setUnmapped] = useState<Record<string, string>>({});
  const [created, setCreated] = useState<Incident | null>(null);

  const {
    control,
    handleSubmit,
    reset,
    setError,
    formState: { isSubmitting },
  } = useForm<IncidentFormValues>({
    defaultValues: emptyValues(today),
    resolver: validatorResolver((values: IncidentFormValues) => validateIncident(values, today)),
    mode: 'onTouched',
  });

  const onSubmit = handleSubmit(async (values) => {
    setSubmitError(null);
    setUnmapped({});
    setCreated(null);
    try {
      const incident = await createIncident(toCreateIncidentRequest(values));
      setCreated(incident);
      reset(emptyValues(today));
    } catch (caught) {
      const apiError = toApiError(caught);
      setSubmitError(apiError);
      setUnmapped(applyServerFieldErrors(apiError, FIELDS, setError));
    }
  });

  return (
    <>
      <PageHeader
        title="Add Emergency Incident"
        subtitle="The incident ID is assigned by the server."
      />

      {created && (
        <Alert severity="success" sx={{ mb: 3 }} role="status">
          <AlertTitle>Incident {created.incidentId} created</AlertTitle>
          {created.description} ({created.incidentDate}).{' '}
          <Link
            component={RouterLink}
            to={`/resources/search?incidentId=${encodeURIComponent(created.incidentId)}`}
          >
            Search resources for this incident
          </Link>
        </Alert>
      )}

      <Paper sx={{ p: { xs: 2, sm: 4 } }}>
        {declarations.loading && !declarations.data && <Loading label="Loading declarations…" />}
        <ErrorAlert error={declarations.error} onRetry={declarations.reload} />
        <ErrorAlert error={submitError} unmappedFieldErrors={unmapped} />

        <Box component="form" noValidate onSubmit={onSubmit} aria-label="New incident">
          <Grid container spacing={3}>
            <Grid size={{ xs: 12, md: 6 }}>
              <Controller
                name="declarationAbbreviation"
                control={control}
                render={({ field, fieldState }) => (
                  <TextField
                    {...field}
                    select
                    id="declarationAbbreviation"
                    label="Declaration"
                    required
                    fullWidth
                    disabled={!declarations.data}
                    error={fieldState.invalid}
                    helperText={fieldState.error?.message}
                  >
                    {(declarations.data ?? []).map((declaration) => (
                      <MenuItem key={declaration.abbreviation} value={declaration.abbreviation}>
                        ({declaration.abbreviation}) {declaration.name}
                      </MenuItem>
                    ))}
                  </TextField>
                )}
              />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                id="incidentId"
                label="Incident ID"
                value="(auto assigned)"
                fullWidth
                disabled
              />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <Controller
                name="incidentDate"
                control={control}
                render={({ field, fieldState }) => (
                  <TextField
                    {...field}
                    id="incidentDate"
                    label="Incident date"
                    type="date"
                    required
                    fullWidth
                    error={fieldState.invalid}
                    helperText={fieldState.error?.message ?? 'Cannot be in the future'}
                    slotProps={{ inputLabel: { shrink: true }, htmlInput: { max: today } }}
                  />
                )}
              />
            </Grid>
            <Grid size={12}>
              <Controller
                name="description"
                control={control}
                render={({ field, fieldState }) => (
                  <TextField
                    {...field}
                    id="description"
                    label="Description"
                    required
                    fullWidth
                    multiline
                    minRows={2}
                    error={fieldState.invalid}
                    helperText={
                      fieldState.error?.message ??
                      `${String(field.value.trim().length)}/${String(DESCRIPTION_MAX)} characters (min 6)`
                    }
                    slotProps={{ htmlInput: { maxLength: DESCRIPTION_MAX } }}
                  />
                )}
              />
            </Grid>
            <Grid size={12}>
              <Typography variant="subtitle2" component="h2">
                Location
              </Typography>
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <Controller
                name="latitude"
                control={control}
                render={({ field, fieldState }) => (
                  <TextField
                    {...field}
                    id="latitude"
                    label="Latitude"
                    required
                    fullWidth
                    error={fieldState.invalid}
                    helperText={fieldState.error?.message ?? 'Decimal degrees, −90 to 90'}
                    slotProps={{ htmlInput: { inputMode: 'decimal' } }}
                  />
                )}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <Controller
                name="longitude"
                control={control}
                render={({ field, fieldState }) => (
                  <TextField
                    {...field}
                    id="longitude"
                    label="Longitude"
                    required
                    fullWidth
                    error={fieldState.invalid}
                    helperText={fieldState.error?.message ?? 'Decimal degrees, −180 to 180'}
                    slotProps={{ htmlInput: { inputMode: 'decimal' } }}
                  />
                )}
              />
            </Grid>
          </Grid>
          <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 2, mt: 4 }}>
            <Button component={RouterLink} to="/">
              Cancel
            </Button>
            <Button type="submit" variant="contained" loading={isSubmitting}>
              Save
            </Button>
          </Box>
        </Box>
      </Paper>
    </>
  );
}
