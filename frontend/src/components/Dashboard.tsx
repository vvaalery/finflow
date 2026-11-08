import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuthStore } from '../store/authStore';
import { useNotificationStore } from '../store/notificationStore';
import { Icon } from './Icons';
import type { IconName } from './Icons';
import NotificationBell from './NotificationBell';
import Notifications from './Notifications';
import Profile from './Profile';
import UserManagement from './UserManagement';
import References from './References';
import PaymentRequests from './PaymentRequests';
import Reports from './Reports';
import CompaniesList from './CompaniesList';
import AccountsList from './AccountsList';
import TransactionsList from './TransactionsList';
import PaymentsList from './PaymentsList';
import Forecast from './Forecast';
import DashboardAnalytics from './DashboardAnalytics';
import ImportData from './ImportData';

type TabId = 'dashboard' | 'notifications' | 'companies' | 'accounts' | 'transactions' | 'payments' | 'forecast' | 'import' | 'profile' | 'users' | 'references' | 'requests' | 'approvals' | 'reports';
interface Tab { id: TabId; name: string; section: string; roles: string[]; icon: IconName }

const ALL = ['ADMIN', 'FINANCE_MANAGER', 'DIRECTOR'];
const MANAGERS = ['ADMIN', 'FINANCE_MANAGER'];

const tabs: Tab[] = [
  { id: 'dashboard', name: 'Обзор', section: 'Главное', roles: ALL, icon: 'overview' },
  { id: 'notifications', name: 'Уведомления', section: 'Главное', roles: ALL, icon: 'bell' },
  { id: 'companies', name: 'Компания', section: 'Учёт', roles: MANAGERS, icon: 'company' },
  { id: 'accounts', name: 'Счета', section: 'Учёт', roles: MANAGERS, icon: 'accounts' },
  { id: 'transactions', name: 'Операции', section: 'Учёт', roles: MANAGERS, icon: 'transactions' },
  { id: 'payments', name: 'Платежи', section: 'Учёт', roles: MANAGERS, icon: 'payments' },
  { id: 'forecast', name: 'Финансовый прогноз', section: 'Аналитика', roles: ALL, icon: 'forecast' },
  { id: 'reports', name: 'Отчёты', section: 'Аналитика', roles: ALL, icon: 'reports' },
  { id: 'import', name: 'Импорт данных', section: 'Управление', roles: MANAGERS, icon: 'import' },
  { id: 'references', name: 'Справочники', section: 'Управление', roles: MANAGERS, icon: 'references' },
  { id: 'requests', name: 'Заявки', section: 'Управление', roles: MANAGERS, icon: 'requests' },
  { id: 'approvals', name: 'Одобрение платежей', section: 'Управление', roles: ['ADMIN', 'DIRECTOR'], icon: 'requests' },
  { id: 'users', name: 'Пользователи', section: 'Администрирование', roles: ['ADMIN'], icon: 'users' },
  { id: 'profile', name: 'Профиль', section: 'Администрирование', roles: ALL, icon: 'profile' },
];
const sections = ['Главное', 'Учёт', 'Аналитика', 'Управление', 'Администрирование'];

const roleNames: Record<string, string> = { ADMIN: 'Администратор', FINANCE_MANAGER: 'Финансовый менеджер', DIRECTOR: 'Директор' };

const normalizeRole = (value?: string) => {
  const role = (value || '').replace('ROLE_', '').toUpperCase();
  if (role === 'FINANCIAL_MANAGER' || role === 'FINANCE_MANAGER') return 'FINANCE_MANAGER';
  return role;
};

const POLL_MS = 60_000;

const Dashboard: React.FC = () => {
  const { user, logout } = useAuthStore();
  const { unread, fetch: fetchNotifications, reset: resetNotifications } = useNotificationStore();
  const [activeTab, setActiveTab] = useState<TabId>('dashboard');
  const [isSidebarOpen, setIsSidebarOpen] = useState(() => localStorage.getItem('sidebarOpen') !== 'false');
  const userRole = normalizeRole(user?.role);
  const visibleTabs = useMemo(() => tabs.filter(tab => tab.roles.includes(userRole)), [userRole]);

  useEffect(() => {
    if (!visibleTabs.some(tab => tab.id === activeTab)) setActiveTab(visibleTabs[0]?.id || 'dashboard');
  }, [activeTab, visibleTabs]);

  useEffect(() => {
    fetchNotifications();
    const timer = window.setInterval(() => fetchNotifications(), POLL_MS);
    const onVisible = () => { if (document.visibilityState === 'visible') fetchNotifications(); };
    document.addEventListener('visibilitychange', onVisible);
    return () => { window.clearInterval(timer); document.removeEventListener('visibilitychange', onVisible); };
  }, [fetchNotifications]);

  const toggleSidebar = () => {
    const next = !isSidebarOpen;
    setIsSidebarOpen(next);
    localStorage.setItem('sidebarOpen', String(next));
  };

  const canOpen = useCallback((tab: string) => visibleTabs.some(t => t.id === tab), [visibleTabs]);
  const openTab = useCallback((tab: string) => { if (canOpen(tab)) { setActiveTab(tab as TabId); window.scrollTo({ top: 0 }); } }, [canOpen]);
  const handleLogout = () => { resetNotifications(); logout(); };

  const active = visibleTabs.find(tab => tab.id === activeTab) || visibleTabs[0];
  const initial = (user?.fullName || user?.email || 'U').trim().charAt(0).toUpperCase();

  const renderPage = () => {
    if (!active) return <div className="empty"><h3>Нет доступных разделов</h3><p>Для текущей роли не настроены разрешённые разделы. Обратитесь к администратору системы.</p></div>;
    switch (activeTab) {
      case 'dashboard': return <DashboardAnalytics onOpenTab={openTab} />;
      case 'notifications': return <Notifications canOpen={canOpen} onOpenTab={openTab} />;
      case 'companies': return <CompaniesList />;
      case 'accounts': return <AccountsList />;
      case 'transactions': return <TransactionsList />;
      case 'payments': return <PaymentsList />;
      case 'forecast': return <Forecast />;
      case 'import': return <ImportData />;
      case 'profile': return <Profile />;
      case 'users': return <UserManagement />;
      case 'references': return <References />;
      case 'requests': return <PaymentRequests />;
      case 'approvals': return <PaymentRequests />;
      case 'reports': return <Reports />;
      default: return <DashboardAnalytics onOpenTab={openTab} />;
    }
  };

  return (
    <div className={`app-shell ${isSidebarOpen ? '' : 'sidebar-collapsed'}`}>
      <aside className="app-sidebar">
        <div className="brand">
          <div className="brand-mark" aria-hidden="true" />
          <span className="brand-name">FinFlow</span>
          <button className="icon-button collapse-toggle" onClick={toggleSidebar}
            title={isSidebarOpen ? 'Свернуть меню' : 'Развернуть меню'} aria-label={isSidebarOpen ? 'Свернуть меню' : 'Развернуть меню'}>
            <Icon name={isSidebarOpen ? 'collapse' : 'expand'} size={16} />
          </button>
        </div>
        <nav className="nav" aria-label="Основная навигация">
          {sections.map(section => {
            const items = visibleTabs.filter(tab => tab.section === section);
            if (!items.length) return null;
            return (
              <div className="nav-group" key={section}>
                <div className="nav-group-title">{section}</div>
                {items.map(tab => (
                  <button key={tab.id} className={`nav-item ${activeTab === tab.id ? 'active' : ''}`} onClick={() => setActiveTab(tab.id)}
                    title={!isSidebarOpen ? tab.name : undefined} aria-current={activeTab === tab.id ? 'page' : undefined}>
                    <span className="nav-icon"><Icon name={tab.icon} /></span>
                    <span className="nav-label">{tab.name}</span>
                    {tab.id === 'notifications' && unread > 0 && <span className="nav-badge" aria-label={`Непрочитанных: ${unread}`}>{unread > 99 ? '99+' : unread}</span>}
                  </button>
                ))}
              </div>
            );
          })}
        </nav>
        <div className="sidebar-foot">
          <div className="user-chip">
            <div className="avatar">{initial}</div>
            <div className="user-meta"><strong>{user?.fullName || 'Пользователь'}</strong><span>{roleNames[userRole] || userRole}</span></div>
          </div>
          <button className="logout" onClick={handleLogout} title="Выйти из системы"><Icon name="logout" size={17} /><span className="logout-label">Выйти</span></button>
        </div>
      </aside>

      <main className="app-main">
        <header className="topbar">
          <div>
            <div className="topbar-crumb">{active?.section || 'Платформа'}</div>
            <div className="topbar-title">{active?.name || 'Обзор'}</div>
          </div>
          <div className="topbar-right">
            <span className="secure">Защищённая сессия</span>
            <NotificationBell canOpen={canOpen} onOpenTab={openTab} onShowAll={() => setActiveTab('notifications')} />
          </div>
        </header>
        <div className="page-content">{renderPage()}</div>
        <footer className="app-footer"><span>FinFlow</span><span>Корпоративные финансы, {new Date().getFullYear()}</span></footer>
      </main>
    </div>
  );
};

export default Dashboard;
