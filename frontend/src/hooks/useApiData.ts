import { useCallback, useEffect, useState } from 'react';
import { isCancellation, toApiError, type ApiError } from '../api/errors';

export type Loader<T> = (signal: AbortSignal) => Promise<T>;

export interface ApiData<T> {
  /** Latest successfully loaded value. Kept while reloading so tables don't flash. */
  data: T | undefined;
  error: ApiError | undefined;
  /** True until the current loader (or reload) has settled. */
  loading: boolean;
  /** Re-runs the loader. */
  reload: () => void;
}

interface Settled<T> {
  loader: Loader<T>;
  generation: number;
  data?: T;
  error?: ApiError;
}

/**
 * Runs `loader` on mount, whenever its identity changes, and on `reload()`. In-flight requests
 * are aborted when superseded or on unmount. Pass a stable loader (e.g. from `useCallback`).
 */
export function useApiData<T>(loader: Loader<T>): ApiData<T> {
  const [generation, setGeneration] = useState(0);
  const [settled, setSettled] = useState<Settled<T> | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    loader(controller.signal).then(
      (data) => {
        setSettled({ loader, generation, data });
      },
      (error: unknown) => {
        if (!isCancellation(error)) {
          setSettled((previous) => ({
            loader,
            generation,
            ...(previous?.data !== undefined ? { data: previous.data } : {}),
            error: toApiError(error),
          }));
        }
      },
    );
    return () => {
      controller.abort();
    };
  }, [loader, generation]);

  const reload = useCallback(() => {
    setGeneration((value) => value + 1);
  }, []);

  const current =
    settled !== null && settled.loader === loader && settled.generation === generation;
  return {
    data: settled?.data,
    error: current ? settled.error : undefined,
    loading: !current,
    reload,
  };
}
