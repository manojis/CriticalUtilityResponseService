import LogoutIcon from '@mui/icons-material/Logout';
import AppBar from '@mui/material/AppBar';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Container from '@mui/material/Container';
import Link from '@mui/material/Link';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import { Outlet, Link as RouterLink } from 'react-router';
import { useAuth } from '../auth/useAuth';
import { USER_TYPE_LABELS } from '../utils/format';

/** Shell for authenticated pages: app bar with user name, user type and Logout. */
export function AppLayout() {
  const { user, logout } = useAuth();
  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      <Link
        href="#main-content"
        sx={{
          position: 'absolute',
          left: -9999,
          '&:focus': { left: 16, top: 16, zIndex: 2000, bgcolor: 'background.paper', p: 1 },
        }}
      >
        Skip to main content
      </Link>
      <AppBar position="static" elevation={1}>
        <Toolbar sx={{ gap: 2 }}>
          <Typography
            variant="h6"
            component={RouterLink}
            to="/"
            sx={{ color: 'inherit', textDecoration: 'none', fontWeight: 700, flexGrow: 1 }}
          >
            ERMS
          </Typography>
          {user && (
            <Box sx={{ textAlign: 'right', display: { xs: 'none', sm: 'block' } }}>
              <Typography variant="body2" component="div" sx={{ fontWeight: 600 }}>
                {user.name}
              </Typography>
              <Typography variant="caption" component="div" sx={{ opacity: 0.85 }}>
                {USER_TYPE_LABELS[user.userType]}
              </Typography>
            </Box>
          )}
          <Button color="inherit" startIcon={<LogoutIcon />} onClick={logout}>
            Logout
          </Button>
        </Toolbar>
      </AppBar>
      <Container
        component="main"
        id="main-content"
        tabIndex={-1}
        maxWidth="lg"
        sx={{ py: 4, flexGrow: 1 }}
      >
        <Outlet />
      </Container>
    </Box>
  );
}
