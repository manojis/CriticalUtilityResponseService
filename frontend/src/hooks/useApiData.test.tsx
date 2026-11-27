import { act, renderHook, waitFor } from '@testing-library/react';
import { ApiError } from '../api/errors';
import { useApiData } from './useApiData';

describe('useApiData', () => {
  it('loads data and exposes loading state', async () => {
    const loader = vi.fn(() => Promise.resolve(42));
    const { result } = renderHook(() => useApiData(loader));
    expect(result.current.loading).toBe(true);
    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });
    expect(result.current.data).toBe(42);
    expect(result.current.error).toBeUndefined();
  });

  it('normalises errors and keeps previous data on reload failure', async () => {
    let fail = false;
    const loader = vi.fn(() =>
      fail
        ? Promise.reject(new ApiError({ status: 500, title: 'Server error', detail: 'boom' }))
        : Promise.resolve('first'),
    );
    const { result } = renderHook(() => useApiData(loader));
    await waitFor(() => {
      expect(result.current.data).toBe('first');
    });
    fail = true;
    act(() => {
      result.current.reload();
    });
    expect(result.current.loading).toBe(true);
    await waitFor(() => {
      expect(result.current.error?.detail).toBe('boom');
    });
    expect(result.current.data).toBe('first');
    expect(loader).toHaveBeenCalledTimes(2);
  });

  it('aborts the in-flight request on unmount and ignores the cancellation', async () => {
    let captured: AbortSignal | undefined;
    const loader = (signal: AbortSignal) => {
      captured = signal;
      return new Promise<never>((_resolve, reject) => {
        signal.addEventListener('abort', () => {
          reject(new DOMException('aborted', 'AbortError'));
        });
      });
    };
    const { unmount, result } = renderHook(() => useApiData(loader));
    unmount();
    await waitFor(() => {
      expect(captured?.aborted).toBe(true);
    });
    expect(result.current.error).toBeUndefined();
  });
});
