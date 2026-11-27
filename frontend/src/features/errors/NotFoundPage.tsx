import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import { Link as RouterLink } from 'react-router';

export function NotFoundPage() {
  return (
    <Box component="main" sx={{ textAlign: 'center', py: 10, px: 2 }}>
      <Typography variant="h2" component="h1" gutterBottom>
        404
      </Typography>
      <Typography variant="h5" component="p" gutterBottom>
        Page not found
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        The page you are looking for does not exist.
      </Typography>
      <Button component={RouterLink} to="/" variant="contained">
        Go to main menu
      </Button>
    </Box>
  );
}
