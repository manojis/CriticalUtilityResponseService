import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import Typography from '@mui/material/Typography';

/** Centered spinner with an accessible status label. */
export function Loading({ label = 'Loading…' }: { label?: string }) {
  return (
    <Box
      role="status"
      aria-live="polite"
      sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 2, py: 6 }}
    >
      <CircularProgress size={28} aria-hidden="true" />
      <Typography color="text.secondary">{label}</Typography>
    </Box>
  );
}
