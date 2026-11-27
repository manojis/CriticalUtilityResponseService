import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';
import { Link as RouterLink } from 'react-router';

interface PageHeaderProps {
  title: string;
  subtitle?: ReactNode;
  /** Hide the "Main menu" back link (used by the menu itself). */
  hideBack?: boolean;
}

export function PageHeader({ title, subtitle, hideBack = false }: PageHeaderProps) {
  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: { xs: 'flex-start', sm: 'center' },
        justifyContent: 'space-between',
        flexDirection: { xs: 'column', sm: 'row' },
        gap: 1,
        mb: 3,
      }}
    >
      <Box>
        <Typography variant="h4" component="h1">
          {title}
        </Typography>
        {subtitle && (
          <Typography variant="subtitle1" color="text.secondary">
            {subtitle}
          </Typography>
        )}
      </Box>
      {!hideBack && (
        <Button component={RouterLink} to="/" startIcon={<ArrowBackIcon />}>
          Main menu
        </Button>
      )}
    </Box>
  );
}
