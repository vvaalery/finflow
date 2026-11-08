import React, { useEffect, useRef, useState } from 'react';
import { Icon } from './Icons';
import { timeAgo, useNotificationStore } from '../store/notificationStore';
import type { AppNotification } from '../store/notificationStore';

interface Props {
  canOpen: (tab: string) => boolean;
  onOpenTab: (tab: string) => void;
  onShowAll: () => void;
}

const NotificationBell: React.FC<Props> = ({ canOpen, onOpenTab, onShowAll }) => {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const { items, unread, markRead, markAllRead } = useNotificationStore();

  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => { if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false); };
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false); };
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => { document.removeEventListener('mousedown', onDown); document.removeEventListener('keydown', onKey); };
  }, [open]);

  const latest = items.slice(0, 5);

  const activate = (n: AppNotification) => {
    if (!n.read) markRead(n.id);
    setOpen(false);
    if (n.linkTab && canOpen(n.linkTab)) onOpenTab(n.linkTab); else onShowAll();
  };

  return (
    <div className="bell" ref={ref}>
      <button className="icon-button" onClick={() => setOpen(v => !v)} aria-haspopup="dialog" aria-expanded={open}
        aria-label={unread ? `Уведомления: непрочитанных ${unread}` : 'Уведомления'}>
        <Icon name="bell" size={19} />
        {unread > 0 && <span className="bell-count">{unread > 9 ? '9+' : unread}</span>}
      </button>
      {open && (
        <div className="popover" role="dialog" aria-label="Уведомления">
          <div className="popover-head">
            <strong>Уведомления</strong>
            <button className="btn-ghost" onClick={markAllRead} disabled={!unread}>Прочитать все</button>
          </div>
          <div className="popover-list">
            {latest.length === 0
              ? <div className="empty-line">Всё спокойно — новых уведомлений нет</div>
              : latest.map(n => (
                <div key={n.id} className={`note compact ${n.severity} ${n.read ? 'read' : ''}`} onClick={() => activate(n)}
                  role="button" tabIndex={0} onKeyDown={e => { if (e.key === 'Enter') activate(n); }}>
                  <div className="rule" />
                  <div>
                    <div className="note-title">{!n.read && <span className="unread-mark" />}{n.title}</div>
                    <div className="note-text">{n.message}</div>
                    <div className="note-meta">{timeAgo(n.createdAt)}</div>
                  </div>
                </div>
              ))}
          </div>
          <div className="popover-foot">
            <button className="btn-ghost" onClick={() => { setOpen(false); onShowAll(); }}>Все уведомления</button>
          </div>
        </div>
      )}
    </div>
  );
};

export default NotificationBell;
