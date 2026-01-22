import { create } from 'zustand';
import { ScanResult } from '../types';

interface ScanStore {
  scanResult: ScanResult | null;
  isLoading: boolean;
  setScanResult: (result: ScanResult | null) => void;
  setLoading: (loading: boolean) => void;
}

export const useScanStore = create<ScanStore>((set) => ({
  scanResult: null,
  isLoading: false,
  setScanResult: (result) => {
    set({ scanResult: result });
  },
  setLoading: (loading) => {
    set({ isLoading: loading });
  },
}));
