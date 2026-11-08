import React from 'react';

export type IconName =
  | 'overview' | 'company' | 'accounts' | 'transactions' | 'payments' | 'forecast' | 'reports' | 'import'
  | 'references' | 'requests' | 'users' | 'profile' | 'bell' | 'logout' | 'collapse' | 'expand' | 'check'
  | 'close' | 'refresh' | 'arrow' | 'lock';

const paths: Record<IconName, React.ReactNode> = {
  overview: <><rect x="3" y="3" width="7" height="8" rx="1.5" /><rect x="14" y="3" width="7" height="5" rx="1.5" /><rect x="14" y="12" width="7" height="9" rx="1.5" /><rect x="3" y="15" width="7" height="6" rx="1.5" /></>,
  company: <><path d="M4 21V5.5A1.5 1.5 0 0 1 5.5 4h7A1.5 1.5 0 0 1 14 5.5V21" /><path d="M14 10h4.5A1.5 1.5 0 0 1 20 11.5V21" /><path d="M2.5 21h19M8 8h2M8 12h2M8 16h2" /></>,
  accounts: <><rect x="3" y="6" width="18" height="13" rx="2" /><path d="M3 10h18M7 15h3" /></>,
  transactions: <><path d="M7 4 3.5 7.5 7 11M3.5 7.5H20" /><path d="m17 13 3.5 3.5L17 20M20.5 16.5H4" /></>,
  payments: <><rect x="3.5" y="5" width="17" height="15" rx="2" /><path d="M3.5 10h17M8 3v4M16 3v4M8 14.5h3" /></>,
  forecast: <><path d="M3 20h18" /><path d="m4 15 4.5-5 4 3.5L20 5" /><path d="M15.5 5H20v4.5" /></>,
  reports: <><path d="M7 3h7l5 5v12a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1Z" /><path d="M14 3v5h5M9 13h6M9 17h4" /></>,
  import: <><path d="M12 15V4M8 8l4-4 4 4" /><path d="M4 15v3a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-3" /></>,
  references: <><path d="M5 4.5A1.5 1.5 0 0 1 6.5 3H19v15H6.5A1.5 1.5 0 0 0 5 19.5V4.5Z" /><path d="M5 19.5A1.5 1.5 0 0 0 6.5 21H19v-3M9 7h6" /></>,
  requests: <><path d="M4 13.5 6.5 5h11L20 13.5" /><path d="M4 13.5V18a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-4.5h-4.5a2.5 2.5 0 0 1-5 0H4Z" /></>,
  users: <><circle cx="9" cy="8" r="3.5" /><path d="M2.5 20a6.5 6.5 0 0 1 13 0" /><path d="M16 4.6a3.5 3.5 0 0 1 0 6.8M18 14.3a6.5 6.5 0 0 1 3.5 5.7" /></>,
  profile: <><circle cx="12" cy="8" r="3.8" /><path d="M4.5 20.5a7.5 7.5 0 0 1 15 0" /></>,
  bell: <><path d="M6 17V11a6 6 0 1 1 12 0v6l1.5 2h-15L6 17Z" /><path d="M10 21a2 2 0 0 0 4 0" /></>,
  logout: <><path d="M14 4h4a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-4" /><path d="m9.5 8-4 4 4 4M5.5 12H15" /></>,
  collapse: <path d="m14.5 6-6 6 6 6" />,
  expand: <path d="m9.5 6 6 6-6 6" />,
  check: <path d="m5 12.5 4.5 4.5L19 7.5" />,
  close: <path d="M6 6l12 12M18 6 6 18" />,
  refresh: <><path d="M20 11a8 8 0 0 0-14.3-4.2L4 9" /><path d="M4 4v5h5M4 13a8 8 0 0 0 14.3 4.2L20 15" /><path d="M20 20v-5h-5" /></>,
  arrow: <path d="M5 12h14M13 6l6 6-6 6" />,
  lock: <><rect x="5" y="10.5" width="14" height="10" rx="2" /><path d="M8 10.5V8a4 4 0 0 1 8 0v2.5" /></>,
};

export const Icon: React.FC<{ name: IconName; size?: number }> = ({ name, size = 18 }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5"
    strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">{paths[name]}</svg>
);
