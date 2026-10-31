
import React, { useEffect, useMemo, useState } from 'react';
import { useAuthStore } from '../store/authStore';
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

type TabId =
  | 'dashboard'
  | 'companies'
  | 'accounts'
  | 'transactions'
  | 'payments'
  | 'forecast'
  | 'import'
  | 'profile'
  | 'users'
  | 'references'
  | 'requests'
  | 'reports';

interface Tab {
  id: TabId;
  name: string;
  section: string;
  roles: string[];
  icon: string;
}

const tabs: Tab[] = [
  { id: 'dashboard', name: 'Обзор', section: 'РАБОЧЕЕ ПРОСТРАНСТВО', roles: ['ADMIN', 'FINANCE_MANAGER', 'DIRECTOR'], icon: 'dashboard' },
  { id: 'companies', name: 'Компания', section: 'РАБОЧЕЕ ПРОСТРАНСТВО', roles: ['ADMIN', 'FINANCE_MANAGER'], icon: 'building' },
  { id: 'accounts', name: 'Счета', section: 'РАБОЧЕЕ ПРОСТРАНСТВО', roles: ['ADMIN', 'FINANCE_MANAGER'], icon: 'wallet' },
  { id: 'transactions', name: 'Операции', section: 'РАБОЧЕЕ ПРОСТРАНСТВО', roles: ['ADMIN', 'FINANCE_MANAGER'], icon: 'activity' },
  { id: 'payments', name: 'Платежи', section: 'РАБОЧЕЕ ПРОСТРАНСТВО', roles: ['ADMIN', 'FINANCE_MANAGER'], icon: 'credit-card' },
  { id: 'forecast', name: 'Финансовый прогноз', section: 'АНАЛИТИКА', roles: ['ADMIN', 'FINANCE_MANAGER', 'DIRECTOR'], icon: 'chart' },
  { id: 'reports', name: 'Отчёты', section: 'АНАЛИТИКА', roles: ['ADMIN', 'FINANCE_MANAGER', 'DIRECTOR'], icon: 'file-chart' },
  { id: 'import', name: 'Импорт данных', section: 'УПРАВЛЕНИЕ', roles: ['ADMIN', 'FINANCE_MANAGER'], icon: 'upload' },
  { id: 'references', name: 'Справочники', section: 'УПРАВЛЕНИЕ', roles: ['ADMIN', 'FINANCE_MANAGER'], icon: 'book' },
  { id: 'requests', name: 'Заявки', section: 'УПРАВЛЕНИЕ', roles: ['ADMIN', 'FINANCE_MANAGER'], icon: 'clipboard' },
  { id: 'users', name: 'Пользователи', section: 'АДМИНИСТРИРОВАНИЕ', roles: ['ADMIN'], icon: 'users' },
  { id: 'profile', name: 'Профиль', section: 'АДМИНИСТРИРОВАНИЕ', roles: ['ADMIN', 'FINANCE_MANAGER', 'DIRECTOR'], icon: 'user' },
];

const normalizeRole = (value?: string) => {
  const role = (value || '').replace('ROLE_', '').toUpperCase();

  if (role === 'FINANCIAL_MANAGER' || role === 'FINANCE_MANAGER') {
    return 'FINANCE_MANAGER';
  }

  return role;
};

const NavIcon: React.FC<{ name: string }> = ({ name }) => {
  const common = {
    width: 19,
    height: 19,
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 1.7,
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
    'aria-hidden': true as const,
  };

  const paths: Record<string, React.ReactNode> = {
    dashboard: (
      <>
        <rect x="3" y="3" width="7" height="7" rx="1.5" />
        <rect x="14" y="3" width="7" height="7" rx="1.5" />
        <rect x="3" y="14" width="7" height="7" rx="1.5" />
        <rect x="14" y="14" width="7" height="7" rx="1.5" />
      </>
    ),
    building: (
      <>
        <rect x="4" y="3" width="16" height="18" rx="2" />
        <path d="M9 21v-4h6v4M8 7h2m4 0h2M8 11h2m4 0h2" />
      </>
    ),
    wallet: (
      <>
        <rect x="3" y="5" width="18" height="15" rx="2" />
        <path d="M3 9h18M16 14h2M6 5V3h12" />
      </>
    ),
    activity: <path d="M3 12h4l3-8 4 16 3-8h4" />,
    'credit-card': (
      <>
        <rect x="2.5" y="4.5" width="19" height="15" rx="2" />
        <path d="M3 9h18M7 15h4" />
      </>
    ),
    chart: (
      <>
        <path d="M3 3v18h18" />
        <path d="m7 14 4-4 4 3 5-7" />
      </>
    ),
    'file-chart': (
      <>
        <path d="M6 3h8l5 5v13H6z" />
        <path d="M14 3v5h5M9 17v-3m3 3v-5m3 5v-2" />
      </>
    ),
    upload: (
      <>
        <path d="M12 16V4m-5 5 5-5 5 5" />
        <path d="M4 15v5h16v-5" />
      </>
    ),
    book: (
      <>
        <path d="M4 4.5A2.5 2.5 0 0 1 6.5 2H20v18H6.5A2.5 2.5 0 0 1 4 17.5z" />
        <path d="M4 17.5A2.5 2.5 0 0 1 6.5 15H20M8 6h7" />
      </>
    ),
    clipboard: (
      <>
        <rect x="5" y="4" width="14" height="18" rx="2" />
        <path d="M9 4V2h6v2M8 10h8m-8 4h8m-8 4h5" />
      </>
    ),
    users: (
      <>
        <path d="M16 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
        <circle cx="10" cy="7" r="4" />
        <path d="M20 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" />
      </>
    ),
    user: (
      <>
        <circle cx="12" cy="8" r="4" />
        <path d="M4 21a8 8 0 0 1 16 0" />
      </>
    ),
  };

  return <svg {...common}>{paths[name] ?? paths.dashboard}</svg>;
};

const Dashboard: React.FC = () => {
  const { user, logout } = useAuthStore();

  const [activeTab, setActiveTab] = useState<TabId>('dashboard');
  const [isSidebarOpen, setIsSidebarOpen] = useState(true);
  const [showNotifications, setShowNotifications] = useState(false);

  const userRole = normalizeRole(user?.role);

  const visibleTabs = useMemo(
    () => tabs.filter((tab) => tab.roles.includes(userRole)),
    [userRole],
  );

  useEffect(() => {
    const saved = localStorage.getItem('sidebarOpen');

    if (saved !== null) {
      setIsSidebarOpen(saved === 'true');
    }
  }, []);

  useEffect(() => {
    if (!visibleTabs.some((tab) => tab.id === activeTab)) {
      setActiveTab(visibleTabs[0]?.id || 'dashboard');
    }
  }, [activeTab, visibleTabs]);

  const toggleSidebar = () => {
    const next = !isSidebarOpen;

    setIsSidebarOpen(next);
    localStorage.setItem('sidebarOpen', String(next));
  };

  const active =
    visibleTabs.find((tab) => tab.id === activeTab) || visibleTabs[0];

  const renderPage = () => {
    if (!active) {
      return (
        <div className="forecast-empty">
          <h3>Нет доступных разделов</h3>
          <p>
            Для текущей роли не настроены разрешённые разделы. Обратитесь к
            администратору системы.
          </p>
        </div>
      );
    }

    switch (activeTab) {
      case 'dashboard':
        return <DashboardAnalytics />;
      case 'companies':
        return <CompaniesList />;
      case 'accounts':
        return <AccountsList />;
      case 'transactions':
        return <TransactionsList />;
      case 'payments':
        return <PaymentsList />;
      case 'forecast':
        return <Forecast />;
      case 'import':
        return <ImportData />;
      case 'profile':
        return <Profile />;
      case 'users':
        return <UserManagement />;
      case 'references':
        return <References />;
      case 'requests':
        return <PaymentRequests />;
      case 'reports':
        return <Reports />;
      default:
        return <DashboardAnalytics />;
    }
  };

  const sections = [
    'РАБОЧЕЕ ПРОСТРАНСТВО',
    'АНАЛИТИКА',
    'УПРАВЛЕНИЕ',
    'АДМИНИСТРИРОВАНИЕ',
  ];

  return (
    <div className={`app-shell ${isSidebarOpen ? '' : 'sidebar-collapsed'}`}>
      <aside className="app-sidebar">
        <div className="brand-lockup">
          <div className="brand-symbol">
            <span />
          </div>

          {isSidebarOpen && (
            <div className="brand-copy">
              <strong>FinFlow</strong>
              <small>FINANCE PLATFORM</small>
            </div>
          )}

          <button
            className="sidebar-toggle"
            onClick={toggleSidebar}
            title={isSidebarOpen ? 'Свернуть меню' : 'Развернуть меню'}
            aria-label={isSidebarOpen ? 'Свернуть меню' : 'Развернуть меню'}
          >
            {isSidebarOpen ? '‹' : '›'}
          </button>
        </div>

        <div className="workspace-label">
          {isSidebarOpen ? 'НАВИГАЦИЯ' : 'МЕНЮ'}
        </div>

        <nav className="primary-nav" aria-label="Основная навигация">
          {sections.map((section) => {
            const sectionTabs = visibleTabs.filter(
              (tab) => tab.section === section,
            );

            if (!sectionTabs.length) return null;

            return (
              <div className="nav-section" key={section}>
                {isSidebarOpen && (
                  <div className="nav-section-title">{section}</div>
                )}

                {sectionTabs.map((tab) => (
                  <button
                    key={tab.id}
                    className={`nav-item ${activeTab === tab.id ? 'active' : ''}`}
                    onClick={() => setActiveTab(tab.id)}
                    title={!isSidebarOpen ? tab.name : undefined}
                    aria-current={activeTab === tab.id ? 'page' : undefined}
                  >
                    <span className="nav-mark">
                      <NavIcon name={tab.icon} />
                    </span>

                    {isSidebarOpen && (
                      <span className="nav-item-label">{tab.name}</span>
                    )}

                    {isSidebarOpen && activeTab === tab.id && (
                      <span className="nav-active-indicator" />
                    )}
                  </button>
                ))}
              </div>
            );
          })}
        </nav>

        <div className="sidebar-bottom">
          <div className="user-card">
            <div className="user-avatar">
              {(user?.fullName || user?.email || 'U')
                .trim()
                .charAt(0)
                .toUpperCase()}
            </div>

            {isSidebarOpen && (
              <div className="user-details">
                <strong>{user?.fullName || 'Пользователь'}</strong>
                <span>{userRole.replace('_', ' ')}</span>
              </div>
            )}
          </div>

          <button className="logout-button" onClick={logout}>
            <span className="logout-symbol">↗</span>
            {isSidebarOpen && 'Выйти из системы'}
          </button>
        </div>
      </aside>

      <main className="app-main">
        <header className="topbar">
          <div>
            <div className="topbar-overline">
              FINFLOW / {active?.section || 'ПЛАТФОРМА'}
            </div>
            <div className="topbar-title">{active?.name || 'Обзор'}</div>
          </div>

          <div className="topbar-right">
            <span className="secure-indicator">
              <span />
              Защищённая сессия
            </span>

            <div className="notification-wrap">
              <button
                className="notification-button"
                onClick={() => setShowNotifications((value) => !value)}
                aria-label="Уведомления"
                aria-expanded={showNotifications}
              >
                <svg
                  viewBox="0 0 24 24"
                  width="17"
                  height="17"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="1.8"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  aria-hidden="true"
                >
                  <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9" />
                  <path d="M10 21h4" />
                </svg>
                <i>0</i>
              </button>

              {showNotifications && (
                <div className="notification-panel">
                  <strong>Уведомления</strong>
                  <p>Новых уведомлений нет.</p>
                  <small>
                    События платежей и заявок появятся здесь.
                  </small>
                </div>
              )}
            </div>

            <div className="topbar-avatar">
              {(user?.fullName || user?.email || 'U')
                .trim()
                .charAt(0)
                .toUpperCase()}
            </div>
          </div>
        </header>

        <div className="page-content">
          {activeTab === 'dashboard' && (
            <section className="dashboard-hero">
              <div className="dashboard-hero-copy">
                <span className="dashboard-hero-eyebrow">
                  FINFLOW · FINANCIAL INTELLIGENCE
                </span>
                <h1>Финансы под контролем.</h1>
                <p>
                  Единое пространство для денежных потоков, платежей и
                  стратегического планирования.
                </p>
                <button
                  className="dashboard-hero-action"
                  onClick={() => setActiveTab('forecast')}
                >
                  Открыть финансовый прогноз <span>↗</span>
                </button>
              </div>

              <div className="dashboard-hero-art" aria-hidden="true">
                <div className="hero-orbit orbit-one" />
                <div className="hero-orbit orbit-two" />
                <div className="hero-orbit orbit-three" />
                <div className="hero-art-core">
                  <span>F</span>
                </div>
                <div className="hero-art-tag">
                  CASH FLOW <b>↗</b>
                </div>
              </div>
            </section>
          )}

          {renderPage()}
        </div>
      </main>
    </div>
  );
};

export default Dashboard;
