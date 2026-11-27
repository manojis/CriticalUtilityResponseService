import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import Alert from '@mui/material/Alert';
import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Container from '@mui/material/Container';
import Paper from '@mui/material/Paper';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { Navigate, useLocation, useNavigate, useSearchParams } from 'react-router';
import { toApiError, type ApiError } from '../../api/errors';
import type { RedirectState } from '../../auth/AuthContext';
import { useAuth } from '../../auth/useAuth';
import { ErrorAlert } from '../../components/ErrorAlert';
import { validatorResolver } from '../../forms/formUtils';
import { validateLogin, type LoginFormValues } from '../../validation/login';

function redirectTarget(state: unknown): string {
  const from = (state as RedirectState | null)?.from;
  if (from && from.pathname !== '/login') {
    return `${from.pathname}${from.search}${from.hash}`;
  }
  return '/';
}

export function LoginPage() {
  const { login, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const [error, setError] = useState<ApiError | null>(null);
  const expired = searchParams.get('expired') === '1';

  const {
    control,
    handleSubmit,
    formState: { isSubmitting },
  } = useForm<LoginFormValues>({
    defaultValues: { username: '', password: '' },
    resolver: validatorResolver(validateLogin),
  });

  if (isAuthenticated) {
    return <Navigate to={redirectTarget(location.state)} replace />;
  }

  const onSubmit = handleSubmit(async (values) => {
    setError(null);
    try {
      await login({ username: values.username.trim(), password: values.password });
      void navigate(redirectTarget(location.state), { replace: true });
    } catch (caught) {
      setError(toApiError(caught));
    }
  });

  return (
    <Container component="main" maxWidth="xs" sx={{ py: { xs: 4, sm: 10 } }}>
      <Paper elevation={3} sx={{ p: 4 }}>
        <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', mb: 2 }}>
          <Avatar sx={{ bgcolor: 'primary.main', mb: 1 }}>
            <LockOutlinedIcon aria-hidden="true" />
          </Avatar>
          <Typography component="h1" variant="h4">
            ERMS
          </Typography>
          <Typography color="text.secondary" align="center">
            Emergency Resource Management System
          </Typography>
        </Box>

        {expired && !error && (
          <Alert severity="info" sx={{ mb: 2 }}>
            Session expired. Please sign in again.
          </Alert>
        )}
        <ErrorAlert error={error} />

        <Box component="form" noValidate onSubmit={onSubmit} aria-label="Sign in">
          <Controller
            name="username"
            control={control}
            render={({ field, fieldState }) => (
              <TextField
                {...field}
                id="username"
                label="Username"
                autoComplete="username"
                fullWidth
                margin="normal"
                required
                error={fieldState.invalid}
                helperText={fieldState.error?.message}
              />
            )}
          />
          <Controller
            name="password"
            control={control}
            render={({ field, fieldState }) => (
              <TextField
                {...field}
                id="password"
                label="Password"
                type="password"
                autoComplete="current-password"
                fullWidth
                margin="normal"
                required
                error={fieldState.invalid}
                helperText={fieldState.error?.message}
              />
            )}
          />
          <Button
            type="submit"
            variant="contained"
            size="large"
            fullWidth
            loading={isSubmitting}
            sx={{ mt: 3 }}
          >
            Sign in
          </Button>
        </Box>
      </Paper>
    </Container>
  );
}
