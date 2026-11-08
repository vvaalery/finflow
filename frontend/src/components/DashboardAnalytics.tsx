import React, { useEffect, useMemo, useState } from 'react';
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import apiClient from '../api/client';
import { Icon } from './Icons';
import { timeAgo, useNotificationStore } from '../store/notificationStore';

interface Stats { totalUsers: number; totalCompanies: number; totalTransactions: number; totalPayments: number; monthlyIncome: number; monthlyExpense: number }
interface MonthlyData { month: string; income: number; expense: number }
interface CategoryData { name: string; value: number }

const nf = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 0 });
const compact = (v: number) => new Intl.NumberFormat('ru-RU', { notation: 'compact', maximumFractionDigits: 1 }).format(v);

const DashboardAnalytics: React.FC<{ onOpenTab?: (tab: string) => void }> = ({ onOpenTab }) => {
  const [stats, setStats] = useState<Stats>({ totalUsers: 0, totalCompanies: 0, totalTransactions: 0, totalPayments: 0, monthlyIncome: 0, monthlyExpense: 0 });
  const [monthlyData, setMonthlyData] = useState<MonthlyData[]>([]);
  const [categoryData, setCategoryData] = useState<CategoryData[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const { items, markRead } = useNotificationStore();

  const load = async () => {
    setLoading(true); setFailed(false);
    try {
      const [s, m, c] = await Promise.all([
        apiClient.get('/analytics/stats'), apiClient.get('/analytics/monthly-dynamics'), apiClient.get('/analytics/category-distribution'),
      ]);
      setStats(s.data); setMonthlyData(m.data); setCategoryData(c.data);
    } catch { setFailed(true); } finally { setLoading(false); }
  };
  useEffect(() => { load(); }, []);

  const categories = useMemo(() => {
    const total = categoryData.reduce((sum, c) => sum + Number(c.value || 0), 0);
    return [...categoryData].sort((a, b) => b.value - a.value).slice(0, 6)
      .map(c => ({ ...c, share: total > 0 ? (c.value / total) * 100 : 0 }));
  }, [categoryData]);

  const attention = items.filter(n => !n.read && n.severity !== 'INFO').slice(0, 3);
  const net = stats.monthlyIncome - stats.monthlyExpense;

  if (loading) return <div className="loading">Загрузка данных…</div>;

  return (
    <div className="stack">
      <section className="page-heading">
        <div>
          <h1>Обзор</h1>
          <p>Денежные потоки за текущий месяц и динамика последних шести месяцев.</p>
        </div>
        <button className="btn-secondary" onClick={load}><Icon name="refresh" size={15} />Обновить</button>
      </section>

      {failed && <div className="inline-error" role="alert" style={{ marginTop: 0 }}>Не удалось загрузить аналитику. Проверьте подключение к серверу.</div>}

      <section className="stats-grid" style={{ marginBottom: 0 }}>
        <div className="stat-card"><div className="stat-value" style={{ color: net >= 0 ? 'var(--positive)' : 'var(--negative)' }}>{nf.format(net)} <small style={{ fontSize: 13, fontFamily: 'var(--sans)', color: 'var(--muted)' }}>BYN</small></div><div className="stat-label">Чистый поток за месяц</div></div>
        <div className="stat-card"><div className="stat-value">{nf.format(stats.monthlyIncome)}</div><div className="stat-label">Доходы за месяц, BYN</div></div>
        <div className="stat-card"><div className="stat-value">{nf.format(stats.monthlyExpense)}</div><div className="stat-label">Расходы за месяц, BYN</div></div>
        <div className="stat-card"><div className="stat-value">{stats.totalTransactions}</div><div className="stat-label">Операций в системе</div></div>
        <div className="stat-card"><div className="stat-value">{stats.totalPayments}</div><div className="stat-label">Платежей в календаре</div></div>
      </section>

      {attention.length > 0 && (
        <section className="panel note-panel" aria-label="Требует внимания">
          <div style={{ padding: '18px 20px 6px' }}><h2 style={{ fontSize: 15, fontWeight: 600 }}>Требует внимания</h2></div>
          {attention.map(n => (
            <div key={n.id} className={`note compact ${n.severity}`} style={{ cursor: 'default' }}>
              <div className="rule" />
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16, alignItems: 'flex-start' }}>
                <div>
                  <div className="note-title">{n.title}</div>
                  <div className="note-text">{n.message}</div>
                  <div className="note-meta">{timeAgo(n.createdAt)}</div>
                </div>
                {n.linkTab && onOpenTab && <button className="btn-ghost" onClick={() => { markRead(n.id); onOpenTab(n.linkTab!); }}>Перейти<Icon name="arrow" size={14} /></button>}
              </div>
            </div>
          ))}
        </section>
      )}

      <div className="grid-main">
        <section className="panel">
          <div className="panel-head"><div><h2>Динамика денежных потоков</h2><p>Доходы и расходы по месяцам, BYN</p></div></div>
          {monthlyData.length > 0 ? (
            <>
              <div className="chart-box">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={monthlyData} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                    <CartesianGrid stroke="#e7e9ec" vertical={false} />
                    <XAxis dataKey="month" tickLine={false} axisLine={false} tick={{ fill: '#7a838d', fontSize: 12 }} />
                    <YAxis tickLine={false} axisLine={false} width={56} tick={{ fill: '#7a838d', fontSize: 12 }} tickFormatter={compact} />
                    <Tooltip formatter={(v) => `${nf.format(Number(v))} BYN`} contentStyle={{ borderRadius: 10, border: '1px solid #e7e9ec', boxShadow: '0 8px 24px rgba(16,20,24,.1)', fontSize: 13 }} />
                    <Line type="monotone" dataKey="income" name="Доходы" stroke="#14513f" strokeWidth={2.25} dot={{ r: 3, fill: '#14513f', strokeWidth: 0 }} />
                    <Line type="monotone" dataKey="expense" name="Расходы" stroke="#a4473d" strokeWidth={2.25} dot={{ r: 3, fill: '#a4473d', strokeWidth: 0 }} />
                  </LineChart>
                </ResponsiveContainer>
              </div>
              <div className="legend"><span><i style={{ borderColor: '#14513f' }} />Доходы</span><span><i style={{ borderColor: '#a4473d' }} />Расходы</span></div>
            </>
          ) : <div className="empty"><p>Нет данных для графика. Добавьте операции или импортируйте историю.</p></div>}
        </section>

        <section className="panel">
          <div className="panel-head"><div><h2>Структура по статьям</h2><p>Доля в общем обороте</p></div></div>
          {categories.length === 0 ? <p className="muted">Добавьте операции с категориями, чтобы увидеть структуру.</p> : (
            <div className="bars">
              {categories.map(c => (
                <div className="bar-row" key={c.name}>
                  <span>{c.name}</span><span className="num">{Math.round(c.share)}%</span>
                  <div className="track"><i style={{ width: `${Math.max(2, c.share)}%` }} /></div>
                </div>
              ))}
            </div>
          )}
        </section>
      </div>
    </div>
  );
};

export default DashboardAnalytics;
