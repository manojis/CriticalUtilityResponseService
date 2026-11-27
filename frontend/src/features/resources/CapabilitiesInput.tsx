import AddIcon from '@mui/icons-material/Add';
import CancelIcon from '@mui/icons-material/Cancel';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import FormHelperText from '@mui/material/FormHelperText';
import TextField from '@mui/material/TextField';
import { useId, useState, type KeyboardEvent } from 'react';
import { CAPABILITY_MAX, validateNewCapability } from '../../validation/resource';

interface CapabilitiesInputProps {
  value: string[];
  onChange: (next: string[]) => void;
  /** Validation message for the list as a whole (client or server). */
  error?: string | undefined;
}

/** Add/remove list of capabilities rendered as chips (FEATURES.md F-4 UI notes). */
export function CapabilitiesInput({ value, onChange, error }: CapabilitiesInputProps) {
  const [draft, setDraft] = useState('');
  const [draftError, setDraftError] = useState<string | undefined>();
  const listId = useId();

  const add = () => {
    const problem = validateNewCapability(draft, value);
    if (problem) {
      setDraftError(problem);
      return;
    }
    onChange([...value, draft.trim()]);
    setDraft('');
    setDraftError(undefined);
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      add();
    }
  };

  return (
    <Box>
      <Box sx={{ display: 'flex', gap: 1, alignItems: 'flex-start' }}>
        <TextField
          id="capability-input"
          label="Capability"
          value={draft}
          onChange={(event) => {
            setDraft(event.target.value);
            setDraftError(undefined);
          }}
          onKeyDown={handleKeyDown}
          fullWidth
          error={Boolean(draftError)}
          helperText={draftError ?? 'Press Enter or Add. Optional.'}
          slotProps={{ htmlInput: { maxLength: CAPABILITY_MAX, 'aria-describedby': listId } }}
        />
        <Button
          variant="outlined"
          onClick={add}
          startIcon={<AddIcon />}
          sx={{ mt: 1, flexShrink: 0 }}
        >
          Add
        </Button>
      </Box>
      <Box
        component="ul"
        id={listId}
        aria-label="Capabilities"
        sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, listStyle: 'none', p: 0, m: 0, mt: 1 }}
      >
        {value.map((capability) => (
          <li key={capability}>
            <Chip
              label={capability}
              onDelete={() => {
                onChange(value.filter((item) => item !== capability));
              }}
              deleteIcon={<CancelIcon titleAccess={`Remove ${capability}`} />}
            />
          </li>
        ))}
      </Box>
      {error && <FormHelperText error>{error}</FormHelperText>}
    </Box>
  );
}
