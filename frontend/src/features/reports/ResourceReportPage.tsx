import Button from '@mui/material/Button';
import LinearProgress from '@mui/material/LinearProgress';
import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableFooter from '@mui/material/TableFooter';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import { Link as RouterLink } from 'react-router';
import { getResourcesByEsfReport } from '../../api/reports';
import { EmptyState } from '../../components/EmptyState';
import { ErrorAlert } from '../../components/ErrorAlert';
import { Loading } from '../../components/Loading';
import { PageHeader } from '../../components/PageHeader';
import { useApiData } from '../../hooks/useApiData';

export function ResourceReportPage() {
  const { data: report, error, loading, reload } = useApiData(getResourcesByEsfReport);

  return (
    <>
      <PageHeader
        title="Resource Report"
        subtitle="Your resources by primary Emergency Support Function"
      />
      <ErrorAlert error={error} onRetry={reload} />
      {loading && !report && <Loading label="Loading report…" />}
      {report && (
        <Paper>
          {loading && <LinearProgress aria-label="Refreshing report" />}
          {report.totals.totalResources === 0 && (
            <EmptyState
              message="You have not registered any resources yet."
              action={
                <Button component={RouterLink} to="/resources/new" variant="outlined">
                  Add a resource
                </Button>
              }
            />
          )}
          <TableContainer>
            <Table size="small" aria-label="Resources by primary ESF">
              <TableHead>
                <TableRow>
                  <TableCell>ESF #</TableCell>
                  <TableCell>Primary Emergency Support Function</TableCell>
                  <TableCell align="right">Total resources</TableCell>
                  <TableCell align="right">Resources in use</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {report.rows.map((row) => (
                  <TableRow key={row.esfNumber} hover>
                    <TableCell>{row.esfNumber}</TableCell>
                    <TableCell component="th" scope="row">
                      {row.esfDescription}
                    </TableCell>
                    <TableCell align="right">{row.totalResources}</TableCell>
                    <TableCell align="right">{row.resourcesInUse}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
              <TableFooter>
                <TableRow
                  sx={{
                    '& td, & th': { fontWeight: 700, color: 'text.primary', fontSize: '0.875rem' },
                  }}
                >
                  <TableCell />
                  <TableCell component="th" scope="row">
                    Total
                  </TableCell>
                  <TableCell align="right" data-testid="total-resources">
                    {report.totals.totalResources}
                  </TableCell>
                  <TableCell align="right" data-testid="total-in-use">
                    {report.totals.resourcesInUse}
                  </TableCell>
                </TableRow>
              </TableFooter>
            </Table>
          </TableContainer>
        </Paper>
      )}
    </>
  );
}
