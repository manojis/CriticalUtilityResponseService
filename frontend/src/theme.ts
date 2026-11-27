import { createTheme } from '@mui/material/styles';

/**
 * Colours carried over from the legacy UI (#2374AB headings, #4DCCBD table headers).
 * System font stack: no web-font download, which keeps the CSP at `font-src 'self'`.
 */
export const theme = createTheme({
  palette: {
    primary: { main: '#2374AB' },
    secondary: { main: '#1F8A7E' },
    background: { default: '#F4F7FA' },
  },
  typography: {
    fontFamily:
      'system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, "Noto Sans", sans-serif',
    h4: { fontWeight: 600 },
  },
  shape: { borderRadius: 8 },
  components: {
    MuiTableHead: {
      styleOverrides: {
        root: {
          '& .MuiTableCell-head': { fontWeight: 600, backgroundColor: '#E3F6F3' },
        },
      },
    },
    MuiTextField: { defaultProps: { variant: 'outlined' } },
  },
});
