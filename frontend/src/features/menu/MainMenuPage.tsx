import AddBoxIcon from '@mui/icons-material/AddBox';
import AssessmentIcon from '@mui/icons-material/Assessment';
import AssignmentLateIcon from '@mui/icons-material/AssignmentLate';
import ReportProblemIcon from '@mui/icons-material/ReportProblem';
import SearchIcon from '@mui/icons-material/Search';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardActionArea from '@mui/material/CardActionArea';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Grid from '@mui/material/Grid';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';
import { Link as RouterLink } from 'react-router';
import { getMe } from '../../api/me';
import { ErrorAlert } from '../../components/ErrorAlert';
import { Loading } from '../../components/Loading';
import { PageHeader } from '../../components/PageHeader';
import { useApiData } from '../../hooks/useApiData';
import type { UserProfile } from '../../types/api';
import { profileSubtitle, USER_TYPE_LABELS } from '../../utils/format';

interface MenuEntry {
  to: string;
  title: string;
  description: string;
  icon: ReactNode;
}

const MENU: MenuEntry[] = [
  {
    to: '/resources/new',
    title: 'Add Resource',
    description: 'Register equipment or services you can provide.',
    icon: <AddBoxIcon fontSize="large" color="primary" />,
  },
  {
    to: '/incidents/new',
    title: 'Add Emergency Incident',
    description: 'Declare an incident to request resources for it.',
    icon: <ReportProblemIcon fontSize="large" color="primary" />,
  },
  {
    to: '/resources/search',
    title: 'Search Resources',
    description: 'Find resources by keyword, ESF or distance; request or deploy.',
    icon: <SearchIcon fontSize="large" color="primary" />,
  },
  {
    to: '/resource-status',
    title: 'Resource Status',
    description: 'Resources in use, your requests, and requests you received.',
    icon: <AssignmentLateIcon fontSize="large" color="primary" />,
  },
  {
    to: '/reports/resources-by-esf',
    title: 'Resource Report',
    description: 'Your resources by primary ESF and how many are in use.',
    icon: <AssessmentIcon fontSize="large" color="primary" />,
  },
];

function profileDetails(profile: UserProfile): { label: string; value: string }[] {
  const details: { label: string; value: string }[] = [];
  if (profile.jobTitle) details.push({ label: 'Job title', value: profile.jobTitle });
  if (profile.hireDate) details.push({ label: 'Hire date', value: profile.hireDate });
  if (profile.municipalityCategory) {
    details.push({ label: 'Category', value: profile.municipalityCategory });
  }
  if (profile.agencyNameLocalOffice) {
    details.push({ label: 'Local office', value: profile.agencyNameLocalOffice });
  }
  if (profile.headquarters) details.push({ label: 'Headquarters', value: profile.headquarters });
  if (profile.numberOfEmployees !== undefined && profile.numberOfEmployees !== null) {
    details.push({ label: 'Employees', value: String(profile.numberOfEmployees) });
  }
  return details;
}

function ProfileCard({ profile }: { profile: UserProfile }) {
  const subtitle = profileSubtitle(profile);
  return (
    <Paper variant="outlined" sx={{ p: 3, mb: 4 }} aria-label="Your profile" component="section">
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
        <Typography variant="h5" component="h2">
          {profile.name}
        </Typography>
        <Chip label={USER_TYPE_LABELS[profile.userType]} size="small" color="secondary" />
      </Box>
      {subtitle && (
        <Typography variant="subtitle1" color="text.secondary" data-testid="profile-subtitle">
          {subtitle}
        </Typography>
      )}
      <Box component="dl" sx={{ display: 'flex', gap: 3, flexWrap: 'wrap', m: 0, mt: 1 }}>
        <Box>
          <Typography component="dt" variant="caption" color="text.secondary">
            Username
          </Typography>
          <Typography component="dd" sx={{ m: 0 }}>
            {profile.username}
          </Typography>
        </Box>
        {profileDetails(profile).map((detail) => (
          <Box key={detail.label}>
            <Typography component="dt" variant="caption" color="text.secondary">
              {detail.label}
            </Typography>
            <Typography component="dd" sx={{ m: 0 }}>
              {detail.value}
            </Typography>
          </Box>
        ))}
      </Box>
    </Paper>
  );
}

export function MainMenuPage() {
  const { data: profile, error, loading, reload } = useApiData(getMe);

  return (
    <>
      <PageHeader title="Main Menu" hideBack />
      {loading && !profile && <Loading label="Loading your profile…" />}
      <ErrorAlert error={error} onRetry={reload} />
      {profile && <ProfileCard profile={profile} />}

      <Box component="nav" aria-label="Main menu">
        <Grid container spacing={3} component="ul" sx={{ listStyle: 'none', p: 0, m: 0 }}>
          {MENU.map((entry) => (
            <Grid key={entry.to} size={{ xs: 12, sm: 6, md: 4 }} component="li">
              <Card sx={{ height: '100%' }}>
                <CardActionArea component={RouterLink} to={entry.to} sx={{ height: '100%' }}>
                  <CardContent>
                    <Box sx={{ mb: 1 }} aria-hidden="true">
                      {entry.icon}
                    </Box>
                    <Typography variant="h6" component="span" sx={{ display: 'block' }}>
                      {entry.title}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      {entry.description}
                    </Typography>
                  </CardContent>
                </CardActionArea>
              </Card>
            </Grid>
          ))}
        </Grid>
      </Box>
    </>
  );
}
