import React, { useEffect, useMemo, useState } from 'react';
import { Icon } from './Icons';
import { severityLabel, timeAgo, useNotificationStore } from '../store/notificationStore';
import type { Severity } from '../store/notificationStore';

type Filter = 'ALL' | 'UNREAD' | Severity;

interface Props {
  canOpen: (tab: string) => boolean;
  onOpenTab: (tab: string) => void;
}

const tabTitles: Record<string, string> = {
  payments: 'Платежи', requests: 'Заявки', forecast: 'Прогноз', reports: 'Отчёты', transactions: 'Операции', import: 'Импорт данных',
};

const Notifications: React.FC<Props> = ({ canOpen, onOpenTab }) => {
  const { items, unread, loaded, loading, error, fetch, markRead, markAllRead, dismiss } = useNotificationStore();
  const [filter, setFilter] = useState<Filter>('ALL');

  useEffect(() => { fetch(true); }, [fetch]);

  const counts = useMemo(() => ({
    CRITICAL: items.filter(n => n.severity === 'CRITICAL').length,
    WARNING: items.filter(n => n.severity === 'WARNING').length,
  }), [items]);

  const visible = useMemo(() => items.filter(n =>
    filter === 'ALL' ? true : filter === 'UNREAD' ? !n.read : n.severity === filter), [items, filter]);

  return (
    <div className="stack">
      <section className="page-heading">
        <div>
          <h1>Уведомления</h1>
          <p>Сигналы формируются из ваших данных: платежи, заявки, прогноз ликвидности и аномалии в операциях. Уведомление исчезает само, когда причина устранена.</p>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button className="btn-secondary" onClick={() => fetch(true)} disabled={loading}><Icon name="refresh" size={15} />{loading ? 'Обновление…' : 'Обновить'}</button>
          <button className="btn-primary" onClick={markAllRead} disabled={!unread}><Icon name="check" size={15} />Прочитать все</button>
        </div>
      </section>

      <div className="stats-grid" style={{ marginBottom: 0 }}>
        <div className="stat-card"><div className="stat-value">{unread}</div><div className="stat-label">Непрочитанные</div></div>
        <div className="stat-card"><div className="stat-value" style={{ color: counts.CRITICAL ? 'var(--negative)' : undefined }}>{counts.CRITICAL}</div><div className="stat-label">Критичные</div></div>
        <div className="stat-card"><div className="stat-value">{counts.WARNING}</div><div className="stat-label">Требуют внимания</div></div>
        <div className="stat-card"><div className="stat-value">{items.length}</div><div className="stat-label">Всего активных</div></div>
      </div>

      <div>
        <div className="segmented" role="group" aria-label="Фильтр уведомлений">
          {([['ALL', 'Все'], ['UNREAD', 'Непрочитанные'], ['CRITICAL', 'Критичные'], ['WARNING', 'Внимание'], ['INFO', 'Инфо']] as [Filter, string][]).map(([id, label]) => (
            <button key={id} aria-pressed={filter === id} onClick={() => setFilter(id)}>{label}</button>
          ))}
        </div>
      </div>

      {error && <div className="inline-error" role="alert" style={{ marginTop: 0 }}>{error}</div>}

      {!loaded && loading ? <div className="loading">Загрузка…</div>
        : visible.length === 0 ? (
          <div className="empty">
            <h3>{items.length === 0 ? 'Всё в порядке' : 'Нет уведомлений в этой категории'}</h3>
            <p>{items.length === 0 ? 'Просроченных платежей, рисков по прогнозу и аномалий не обнаружено.' : 'Измените фильтр, чтобы увидеть остальные.'}</p>
          </div>
        ) : (
          <section className="panel note-panel" aria-live="polite">
            {visible.map(n => (
              <article key={n.id} className={`note ${n.severity} ${n.read ? 'read' : ''}`}>
                <div className="rule" />
                <div>
                  <div className="note-title">{!n.read && <span className="unread-mark" aria-label="Не прочитано" />}{n.title}</div>
                  <div className="note-text">{n.message}</div>
                  <div className="note-meta">
                    <span className={n.severity === 'CRITICAL' ? 'badge-overdue' : n.severity === 'WARNING' ? 'badge-pending' : 'tag'}>{severityLabel[n.severity]}</span>
                    <span>{timeAgo(n.createdAt)}</span>
                  </div>
                </div>
                <div className="note-actions">
                  {n.linkTab && canOpen(n.linkTab) && (
                    <button className="btn-ghost" onClick={() => { if (!n.read) markRead(n.id); onOpenTab(n.linkTab!); }}>
                      {tabTitles[n.linkTab] ?? 'Открыть'}<Icon name="arrow" size={14} />
                    </button>
                  )}
                  <button className="btn-ghost" onClick={() => markRead(n.id, !n.read)}>{n.read ? 'Отметить непрочитанным' : 'Прочитано'}</button>
                  <button className="btn-ghost" onClick={() => dismiss(n.id)}>Скрыть</button>
                </div>
              </article>
            ))}
          </section>
        )}
    </div>
  );
};

export default Notifications;
