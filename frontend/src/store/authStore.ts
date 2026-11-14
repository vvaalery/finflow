
import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import { login } from '../api/auth';
import type { AuthResponse } from '../api/auth';

interface AuthState {
  user: AuthResponse | null;
  isLoading: boolean;
  error: string | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
  setUser: (user: AuthResponse | null) => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      isLoading: false,
      error: null,

      login: async (email, password) => {
        set({ isLoading: true, error: null });

        try {
          const response = await login({ email, password });

          set({
            user: response,
            isLoading: false
          });
        } catch (error: any) {
          set({
            error: error?.response?.data?.message || error.message,
            isLoading: false
          });

          throw error;
        }
      },

      logout: () => set({
        user: null,
        error: null,
        isLoading: false
      }),

      setUser: (user) => set({ user })
    }),
    {
      name: 'auth-storage',
      storage: createJSONStorage(() => sessionStorage)
    }
  )
);
