import CssBaseline from '@mui/material/CssBaseline';
import { ThemeProvider } from '@mui/material/styles';
import { lazy, Suspense } from 'react';
import { Route, Routes } from 'react-router';
import { AuthProvider } from './auth/AuthContext';
import { ProtectedRoute } from './auth/ProtectedRoute';
import { AppLayout } from './components/AppLayout';
import { Loading } from './components/Loading';
import { NotificationProvider } from './components/NotificationProvider';
import { LoginPage } from './features/login/LoginPage';
import { NotFoundPage } from './features/errors/NotFoundPage';
import { theme } from './theme';

// Authenticated pages are code-split; the login page ships in the entry chunk.
const MainMenuPage = lazy(() =>
  import('./features/menu/MainMenuPage').then((m) => ({ default: m.MainMenuPage })),
);
const AddResourcePage = lazy(() =>
  import('./features/resources/AddResourcePage').then((m) => ({ default: m.AddResourcePage })),
);
const AddIncidentPage = lazy(() =>
  import('./features/incidents/AddIncidentPage').then((m) => ({ default: m.AddIncidentPage })),
);
const SearchResourcesPage = lazy(() =>
  import('./features/resources/SearchResourcesPage').then((m) => ({
    default: m.SearchResourcesPage,
  })),
);
const ResourceStatusPage = lazy(() =>
  import('./features/requests/ResourceStatusPage').then((m) => ({ default: m.ResourceStatusPage })),
);
const ResourceReportPage = lazy(() =>
  import('./features/reports/ResourceReportPage').then((m) => ({ default: m.ResourceReportPage })),
);

/** Route table (ARCHITECTURE.md §4.3). Rendered inside a router by main.tsx / tests. */
export function AppRoutes() {
  return (
    <Suspense fallback={<Loading />}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<ProtectedRoute />}>
          <Route element={<AppLayout />}>
            <Route index element={<MainMenuPage />} />
            <Route path="resources/new" element={<AddResourcePage />} />
            <Route path="incidents/new" element={<AddIncidentPage />} />
            <Route path="resources/search" element={<SearchResourcesPage />} />
            <Route path="resource-status" element={<ResourceStatusPage />} />
            <Route path="reports/resources-by-esf" element={<ResourceReportPage />} />
          </Route>
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Suspense>
  );
}

/** Providers + routes. The router itself is supplied by the caller (BrowserRouter in main.tsx). */
export function App() {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <NotificationProvider>
        <AuthProvider>
          <AppRoutes />
        </AuthProvider>
      </NotificationProvider>
    </ThemeProvider>
  );
}
