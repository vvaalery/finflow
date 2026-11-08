import { create } from 'zustand';
import apiClient from '../api/client';

export type Severity = 'CRITICAL' | 'WARNING' | 'INFO';

export interface AppNotification {
  id: string;
  type: string;
  severity: Severity;
  title: string;
  message: string;
  linkTab: string | null;
  read: boolean;
  createdAt: string | null;
}

interface NotificationState {
  items: AppNotification[];
  unread: number;
  loaded: boolean;
  loading: boolean;
  error: string | null;
  fetch: (refresh?: boolean) => Promise<void>;
  markRead: (id: string, read?: boolean) => Promise<void>;
  markAllRead: () => Promise<void>;
  dismiss: (id: string) => Promise<void>;
  reset: () => void;
}

const countUnread = (items: AppNotification[]) => items.filter(n => !n.read).length;

export const useNotificationStore = create<NotificationState>((set, get) => ({
  items: [],
  unread: 0,
  loaded: false,
  loading: false,
  error: null,

  fetch: async (refresh = false) => {
    set({ loading: true });
    try {
      const { data } = await apiClient.get('/notifications', { params: { refresh } });
      set({ items: data.items ?? [], unread: data.unreadCount ?? 0, loaded: true, loading: false, error: null });
    } catch {
      set({ loading: false, error: 'Не удалось загрузить уведомления' });
    }
  },

  // Оптимистичное обновление с откатом при ошибке сервера.
  markRead: async (id, read = true) => {
    const prev = get().items;
    const items = prev.map(n => (n.id === id ? { ...n, read } : n));
    set({ items, unread: countUnread(items) });
    try { await apiClient.post(`/notifications/${id}/read`, null, { params: { read } }); }
    catch { set({ items: prev, unread: countUnread(prev) }); }
  },

  markAllRead: async () => {
    const prev = get().items;
    const items = prev.map(n => ({ ...n, read: true }));
    set({ items, unread: 0 });
    try { await apiClient.post('/notifications/read-all'); }
    catch { set({ items: prev, unread: countUnread(prev) }); }
  },

  dismiss: async (id) => {
    const prev = get().items;
    const items = prev.filter(n => n.id !== id);
    set({ items, unread: countUnread(items) });
    try { await apiClient.delete(`/notifications/${id}`); }
    catch { set({ items: prev, unread: countUnread(prev) }); }
  },

  reset: () => set({ items: [], unread: 0, loaded: false, loading: false, error: null }),
}));

const rtf = new Intl.RelativeTimeFormat('ru', { numeric: 'auto' });

export const timeAgo = (iso: string | null): string => {
  if (!iso) return '';
  const diff = (new Date(iso).getTime() - Date.now()) / 1000;
  const abs = Math.abs(diff);
  if (abs < 60) return 'только что';
  if (abs < 3600) return rtf.format(Math.round(diff / 60), 'minute');
  if (abs < 86400) return rtf.format(Math.round(diff / 3600), 'hour');
  return rtf.format(Math.round(diff / 86400), 'day');
};

export const severityLabel: Record<Severity, string> = {
  CRITICAL: 'Критично',
  WARNING: 'Требует внимания',
  INFO: 'Информация',
};
