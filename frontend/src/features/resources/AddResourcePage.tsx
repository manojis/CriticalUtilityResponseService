import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Autocomplete from '@mui/material/Autocomplete';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Grid from '@mui/material/Grid';
import InputAdornment from '@mui/material/InputAdornment';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useCallback, useState } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { Link as RouterLink } from 'react-router';
import { toApiError, type ApiError } from '../../api/errors';
import { getMe } from '../../api/me';
import { getCostUnits, getEsfs } from '../../api/reference';
import { createResource } from '../../api/resources';
import { ErrorAlert } from '../../components/ErrorAlert';
import { Loading } from '../../components/Loading';
import { PageHeader } from '../../components/PageHeader';
import { applyServerFieldErrors, validatorResolver } from '../../forms/formUtils';
import { useApiData } from '../../hooks/useApiData';
import type { CostUnit, Esf, ResourceDetail, UserProfile } from '../../types/api';
import {
  MODEL_MAX,
  NAME_MAX,
  toCreateResourceRequest,
  validateResource,
  type ResourceFormValues,
} from '../../validation/resource';
import { CapabilitiesInput } from './CapabilitiesInput';

const EMPTY: ResourceFormValues = {
  resourceName: '',
  model: '',
  primaryEsf: '',
  additionalEsfs: [],
  capabilities: [],
  cost: '',
  costUnit: '',
  latitude: '',
  longitude: '',
  maxDistanceKm: '',
};

const FIELDS = Object.keys(EMPTY) as (keyof ResourceFormValues)[];

interface FormData {
  profile: UserProfile;
  esfs: Esf[];
  costUnits: CostUnit[];
}

async function loadFormData(signal: AbortSignal): Promise<FormData> {
  const [profile, esfs, costUnits] = await Promise.all([
    getMe(signal),
    getEsfs(signal),
    getCostUnits(signal),
  ]);
  return { profile, esfs, costUnits };
}

const esfLabel = (esf: Esf) => `#${String(esf.esfNumber)} ${esf.description}`;

export function AddResourcePage() {
  const formData = useApiData(loadFormData);
  const [submitError, setSubmitError] = useState<ApiError | null>(null);
  const [unmapped, setUnmapped] = useState<Record<string, string>>({});
  const [created, setCreated] = useState<ResourceDetail | null>(null);

  const {
    control,
    handleSubmit,
    reset,
    setError,
    setValue,
    getValues,
    formState: { isSubmitting },
  } = useForm<ResourceFormValues>({
    defaultValues: EMPTY,
    resolver: validatorResolver(validateResource),
    mode: 'onTouched',
  });

  const primaryEsf = useWatch({ control, name: 'primaryEsf' });

  // Changing the primary ESF removes it from the additional ESFs (FEATURES.md F-4 UI notes).
  const onPrimaryChange = useCallback(
    (primary: string) => {
      const additional = getValues('additionalEsfs');
      const filtered = additional.filter((esf) => String(esf) !== primary);
      if (filtered.length !== additional.length) {
        setValue('additionalEsfs', filtered, { shouldValidate: true });
      }
    },
    [getValues, setValue],
  );

  const onSubmit = handleSubmit(async (values) => {
    setSubmitError(null);
    setUnmapped({});
    setCreated(null);
    try {
      const resource = await createResource(toCreateResourceRequest(values));
      setCreated(resource);
      reset(EMPTY);
    } catch (caught) {
      const apiError = toApiError(caught);
      setSubmitError(apiError);
      setUnmapped(applyServerFieldErrors(apiError, FIELDS, setError));
    }
  });

  const data = formData.data;
  const esfs = data?.esfs ?? [];

  return (
    <>
      <PageHeader title="Add Resource" subtitle="The resource ID is assigned by the server." />

      {created && (
        <Alert severity="success" sx={{ mb: 3 }} role="status">
          <AlertTitle>Resource #{created.resourceId} created</AlertTitle>
          {created.resourceName} is now available to responders.
        </Alert>
      )}

      <Paper sx={{ p: { xs: 2, sm: 4 } }}>
        {formData.loading && !data && <Loading label="Loading form…" />}
        <ErrorAlert error={formData.error} onRetry={formData.reload} />
        <ErrorAlert error={submitError} unmappedFieldErrors={unmapped} />

        {data && (
          <Box component="form" noValidate onSubmit={onSubmit} aria-label="New resource">
            <Grid container spacing={3}>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  id="resourceId"
                  label="Resource ID"
                  value="(auto assigned)"
                  fullWidth
                  disabled
                />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  id="owner"
                  label="Owner"
                  value={`${data.profile.name} (${data.profile.username})`}
                  fullWidth
                  slotProps={{ htmlInput: { readOnly: true } }}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <Controller
                  name="resourceName"
                  control={control}
                  render={({ field, fieldState }) => (
                    <TextField
                      {...field}
                      id="resourceName"
                      label="Resource name"
                      required
                      fullWidth
                      error={fieldState.invalid}
                      helperText={fieldState.error?.message ?? '3–100 characters'}
                      slotProps={{ htmlInput: { maxLength: NAME_MAX } }}
                    />
                  )}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <Controller
                  name="model"
                  control={control}
                  render={({ field, fieldState }) => (
                    <TextField
                      {...field}
                      id="model"
                      label="Model"
                      fullWidth
                      error={fieldState.invalid}
                      helperText={fieldState.error?.message ?? 'Optional'}
                      slotProps={{ htmlInput: { maxLength: MODEL_MAX } }}
                    />
                  )}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <Controller
                  name="primaryEsf"
                  control={control}
                  render={({ field, fieldState }) => (
                    <TextField
                      {...field}
                      onChange={(event) => {
                        field.onChange(event);
                        onPrimaryChange(event.target.value);
                      }}
                      select
                      id="primaryEsf"
                      label="Primary ESF"
                      required
                      fullWidth
                      error={fieldState.invalid}
                      helperText={fieldState.error?.message}
                    >
                      {esfs.map((esf) => (
                        <MenuItem key={esf.esfNumber} value={String(esf.esfNumber)}>
                          {esfLabel(esf)}
                        </MenuItem>
                      ))}
                    </TextField>
                  )}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <Controller
                  name="additionalEsfs"
                  control={control}
                  render={({ field, fieldState }) => {
                    const options = esfs.filter((esf) => String(esf.esfNumber) !== primaryEsf);
                    const selected = esfs.filter((esf) => field.value.includes(esf.esfNumber));
                    return (
                      <Autocomplete
                        multiple
                        id="additionalEsfs"
                        options={options}
                        value={selected}
                        getOptionLabel={esfLabel}
                        isOptionEqualToValue={(a, b) => a.esfNumber === b.esfNumber}
                        filterSelectedOptions
                        onChange={(_event, next) => {
                          field.onChange(next.map((esf) => esf.esfNumber));
                        }}
                        onBlur={field.onBlur}
                        renderInput={(params) => (
                          <TextField
                            {...params}
                            label="Additional ESFs"
                            error={fieldState.invalid}
                            helperText={
                              fieldState.error?.message ?? 'Optional; excludes the primary ESF'
                            }
                          />
                        )}
                      />
                    );
                  }}
                />
              </Grid>
              <Grid size={12}>
                <Controller
                  name="capabilities"
                  control={control}
                  render={({ field, fieldState }) => (
                    <CapabilitiesInput
                      value={field.value}
                      onChange={field.onChange}
                      error={fieldState.error?.message}
                    />
                  )}
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <Controller
                  name="cost"
                  control={control}
                  render={({ field, fieldState }) => (
                    <TextField
                      {...field}
                      id="cost"
                      label="Cost"
                      required
                      fullWidth
                      error={fieldState.invalid}
                      helperText={fieldState.error?.message ?? '≥ 0, up to 2 decimals'}
                      slotProps={{
                        htmlInput: { inputMode: 'decimal' },
                        input: {
                          startAdornment: <InputAdornment position="start">$</InputAdornment>,
                        },
                      }}
                    />
                  )}
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <Controller
                  name="costUnit"
                  control={control}
                  render={({ field, fieldState }) => (
                    <TextField
                      {...field}
                      select
                      id="costUnit"
                      label="Per"
                      required
                      fullWidth
                      error={fieldState.invalid}
                      helperText={fieldState.error?.message}
                    >
                      {data.costUnits.map((unit) => (
                        <MenuItem key={unit} value={unit}>
                          {unit}
                        </MenuItem>
                      ))}
                    </TextField>
                  )}
                />
              </Grid>
              <Grid size={12}>
                <Typography variant="subtitle2" component="h2">
                  Home location
                </Typography>
              </Grid>
              <Grid size={{ xs: 12, sm: 4 }}>
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
                      helperText={fieldState.error?.message ?? '−90 to 90'}
                      slotProps={{ htmlInput: { inputMode: 'decimal' } }}
                    />
                  )}
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 4 }}>
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
                      helperText={fieldState.error?.message ?? '−180 to 180'}
                      slotProps={{ htmlInput: { inputMode: 'decimal' } }}
                    />
                  )}
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 4 }}>
                <Controller
                  name="maxDistanceKm"
                  control={control}
                  render={({ field, fieldState }) => (
                    <TextField
                      {...field}
                      id="maxDistanceKm"
                      label="Max distance"
                      fullWidth
                      error={fieldState.invalid}
                      helperText={fieldState.error?.message ?? 'Optional; blank = no limit'}
                      slotProps={{
                        htmlInput: { inputMode: 'decimal' },
                        input: { endAdornment: <InputAdornment position="end">km</InputAdornment> },
                      }}
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
        )}
      </Paper>
    </>
  );
}
