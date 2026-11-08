import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Area, CartesianGrid, ComposedChart, Line, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useAuthStore } from '../store/authStore';
import apiClient from '../api/client';
import { Icon } from './Icons';

interface Company { id: string; name: string }
interface Point {
  date: string; inflow: number; outflow: number; netCashFlow: number;
  netLow?: number | null; netHigh?: number | null; projectedBalance?: number | null;
}
interface HistoryPoint { date: string; inflow: number; outflow: number; netCashFlow: number }
interface Quality { available: boolean; windows: number; mae: number | null; rmse: number | null; wape: number | null; note?: string }
interface ModelScore { method: string; label: string; family: string; selected: boolean; best: boolean; wape: number | null; mae: number | null; rmse: number | null }
interface ForecastResult {
  id: string; totalProjectedInflow: number; totalProjectedOutflow: number; netCashFlow: number;
  method: string; methodLabel: string; scenario: string;
  currentBalance: number; projectedClosingBalance: number;
  items: Point[]; history: HistoryPoint[]; quality?: Quality; comparison?: ModelScore[]; warnings?: string[];
  modelParams?: { inflow?: Record<string, unknown> };
}
interface SeriesStats { trend: string; slopePercent: number; cv: number; r2: number; seasonalStrength: number }
interface Insights {
  sufficient: boolean; months: number; inflow?: SeriesStats; outflow?: SeriesStats;
  savingsRatePercent?: number | null; runwayMonths?: number | null; findings: string[];
  anomalies: { month: string; series: string; value: number; expected: number; score: number }[];
  expenseCategories: { name: string; amount: number; sharePercent: number }[];
}

type ChartView = 'net' | 'flows' | 'balance';

const METHODS = [
  { id: 'GRADIENT_BOOSTING', title: 'Градиентный бустинг', family: 'Машинное обучение', text: 'Тренд плюс деревья решений по остаткам и лагам' },
  { id: 'HOLT_WINTERS', title: 'Хольт-Винтерс', family: 'Временной ряд', text: 'Экспоненциальное сглаживание, сезонность от 24 мес.' },
  { id: 'LINEAR_REGRESSION', title: 'Линейная регрессия', family: 'Статистика', text: 'Линейный тренд по времени методом наименьших квадратов' },
  { id: 'MOVING_AVERAGE', title: 'Скользящее среднее', family: 'Статистика', text: 'Взвешенное среднее последних трёх месяцев' },
];
const SCENARIOS = [
  { id: 'BASE', title: 'Базовый', note: 'Без корректировки допущений' },
  { id: 'OPTIMISTIC', title: 'Оптимистичный', note: 'Поступления +15%' },
  { id: 'PESSIMISTIC', title: 'Консервативный', note: 'Поступления −15%, расходы +10%' },
];

const nf = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 0 });
const nf1 = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 1, minimumFractionDigits: 1 });
const byn = (v: number | null | undefined) => `${nf.format(Math.round(v ?? 0))}`;
const compact = (v: number) => new Intl.NumberFormat('ru-RU', { notation: 'compact', maximumFractionDigits: 1 }).format(v);
const monthLabel = (date: string) => new Date(`${date.slice(0, 10)}T12:00:00`).toLocaleDateString('ru-RU', { month: 'short', year: '2-digit' });
const monthFull = (ym: string) => new Date(`${ym}-01T12:00:00`).toLocaleDateString('ru-RU', { month: 'long', year: 'numeric' });
const signed = (v: number) => `${v > 0 ? '+' : v < 0 ? '−' : ''}${nf1.format(Math.abs(v))}`;

const INK = '#101418', ACCENT = '#14513f', MUTED = '#7a838d', LINE = '#e7e9ec', NEG = '#a4473d', BRASS = '#9a7b3f';

const Forecast: React.FC = () => {
  const { user } = useAuthStore();
  const [companies, setCompanies] = useState<Company[]>([]);
  const [companyId, setCompanyId] = useState('');
  const [months, setMonths] = useState(6);
  const [method, setMethod] = useState('GRADIENT_BOOSTING');
  const [scenario, setScenario] = useState('BASE');
  const [view, setView] = useState<ChartView>('net');
  const [loading, setLoading] = useState(false);
  const [loadingCompanies, setLoadingCompanies] = useState(true);
  const [error, setError] = useState('');
  const [result, setResult] = useState<ForecastResult | null>(null);
  const [insights, setInsights] = useState<Insights | null>(null);

  useEffect(() => {
    if (!user?.id) { setLoadingCompanies(false); return; }
    apiClient.get('/companies', { params: { ownerId: user.id } })
      .then(({ data }) => {
        const list: Company[] = Array.isArray(data) ? data : [];
        setCompanies(list);
        if (list.length) setCompanyId(list[0].id);
        else setError('Для пользователя не найдена компания. Проверьте данные компании в базе данных.');
      })
      .catch(() => setError('Не удалось загрузить компании. Проверьте подключение и права доступа.'))
      .finally(() => setLoadingCompanies(false));
  }, [user?.id]);

  useEffect(() => {
    if (!companyId) return;
    let cancelled = false;
    setResult(null); setInsights(null);
    apiClient.get('/forecast/latest', { params: { companyId } })
      .then(({ data }) => { if (!cancelled) { setResult(data); setMethod(data.method); setScenario(data.scenario); setMonths(data.items?.length || 6); } })
      .catch(() => { });
    apiClient.get('/analytics/insights', { params: { companyId } })
      .then(({ data }) => { if (!cancelled) setInsights(data); })
      .catch(() => {  });
    return () => { cancelled = true; };
  }, [companyId]);

  const generate = useCallback(async (override?: { method?: string }) => {
    if (!companyId) { setError('Сначала выберите компанию.'); return; }
    const m = override?.method ?? method;
    setLoading(true); setError('');
    try {
      const { data } = await apiClient.get('/forecast/generate', { params: { companyId, months, method: m, scenario } });
      setResult(data); setMethod(data.method);
    } catch (e: any) {
      setError(e?.response?.data?.message || 'Не удалось построить прогноз. Проверьте права пользователя и наличие операций.');
    } finally { setLoading(false); }
  }, [companyId, months, method, scenario]);

  const chart = useMemo(() => {
    if (!result) return [];
    const hist = result.history.map(h => ({
      label: monthLabel(h.date), net: h.netCashFlow, inH: h.inflow, outH: h.outflow,
    })) as Record<string, any>[];
    const last = hist[hist.length - 1];
    if (last) { last.netF = last.net; last.inF = last.inH; last.outF = last.outH; last.band = [last.net, last.net]; last.bal = result.currentBalance; }
    const fc = result.items.map(p => ({
      label: monthLabel(p.date), netF: p.netCashFlow, inF: p.inflow, outF: p.outflow,
      band: [p.netLow ?? p.netCashFlow, p.netHigh ?? p.netCashFlow], bal: p.projectedBalance ?? null,
    }));
    return [...hist, ...fc];
  }, [result]);

  const scenarioNote = SCENARIOS.find(s => s.id === scenario)?.note;
  const bestModel = result?.comparison?.find(c => c.best);
  const boostingOff = result?.method === 'GRADIENT_BOOSTING' && result.modelParams?.inflow?.boostingActive === false;
  const negBalance = result ? result.items.some(p => (p.projectedBalance ?? 0) < 0) : false;

  const tooltipFormatter = (value: unknown, name: unknown) => {
    if (Array.isArray(value)) return [`${byn(value[0])} … ${byn(value[1])} BYN`, 'Интервал 80%'];
    return [`${byn(Number(value))} BYN`, String(name)];
  };

  return (
    <div className="stack">
      <section className="page-heading">
        <div>
          <h1>Прогноз денежных потоков</h1>
          <p>Сценарное моделирование по истории операций: статистика, временные ряды и градиентный бустинг с оценкой точности на исторических окнах.</p>
        </div>
      </section>

      <section className="panel">
        <div className="controls">
          <label className="field-label">Компания
            <select value={companyId} onChange={e => setCompanyId(e.target.value)} disabled={loadingCompanies || !companies.length}>
              {companies.map(c => <option value={c.id} key={c.id}>{c.name}</option>)}
            </select>
          </label>
          <label className="field-label">Горизонт
            <select value={months} onChange={e => setMonths(Number(e.target.value))}>
              {[3, 6, 12, 18, 24].map(m => <option key={m} value={m}>{m} мес.</option>)}
            </select>
          </label>
          <label className="field-label">Сценарий
            <select value={scenario} onChange={e => setScenario(e.target.value)}>
              {SCENARIOS.map(s => <option key={s.id} value={s.id}>{s.title}</option>)}
            </select>
          </label>
        </div>

        <div className="method-picker" role="group" aria-label="Метод прогнозирования">
          {METHODS.map(m => (
            <button key={m.id} className="method" aria-pressed={method === m.id} onClick={() => setMethod(m.id)}>
              <span className={`tag ${m.id === 'GRADIENT_BOOSTING' ? 'tag-brass' : ''}`}>{m.family}</span>
              <strong>{m.title}</strong>
              <span>{m.text}</span>
            </button>
          ))}
        </div>

        <div className="actions-row">
          <p>{scenarioNote}</p>
          <button className="btn-primary" onClick={() => generate()} disabled={loading || loadingCompanies || !companyId}>
            {loading ? 'Выполняется расчёт…' : result ? 'Пересчитать прогноз' : 'Рассчитать прогноз'}
          </button>
        </div>
        {error && <div className="inline-error" role="alert">{error}</div>}
      </section>

      {!result && !loading && !error && (
        <div className="empty"><h3>Прогноз ещё не рассчитан</h3><p>Выберите метод и сценарий, затем рассчитайте прогноз — будущие поступления и расходы появятся на графике вместе с доверительным интервалом.</p></div>
      )}

      {result && (
        <>
          <section className="hero" aria-label="Итоги прогноза">
            <div>
              <span className="k">Чистый денежный поток за {result.items.length} мес.</span>
              <span className="v">{byn(result.netCashFlow)}<small>BYN</small></span>
              <span className="s">{result.methodLabel}, {SCENARIOS.find(s => s.id === result.scenario)?.title.toLowerCase()} сценарий</span>
            </div>
            <div>
              <span className="k">Поступления</span>
              <span className="v positive-value" style={{ fontWeight: 500 }}>{byn(result.totalProjectedInflow)}<small>BYN</small></span>
              <span className="s">Суммарно за период</span>
            </div>
            <div>
              <span className="k">Расходы</span>
              <span className="v negative-value" style={{ fontWeight: 500 }}>{byn(result.totalProjectedOutflow)}<small>BYN</small></span>
              <span className="s">Суммарно за период</span>
            </div>
            <div>
              <span className="k">Остаток на конец периода</span>
              <span className="v" style={{ color: negBalance ? NEG : undefined }}>{byn(result.projectedClosingBalance)}<small>BYN</small></span>
              <span className="s">Сейчас на счетах в BYN: {byn(result.currentBalance)}</span>
            </div>
          </section>

          {negBalance && <div className="inline-error" role="alert" style={{ marginTop: 0 }}>По прогнозу остаток на счетах уходит в минус. Проверьте график остатка и платёжный календарь.</div>}
          {result.warnings?.map(w => <div className="inline-warning" key={w} style={{ marginTop: 0 }}>{w}</div>)}

          <section className="panel">
            <div className="panel-head">
              <div>
                <h2>{view === 'net' ? 'Чистый денежный поток' : view === 'flows' ? 'Поступления и расходы' : 'Остаток на счетах'}</h2>
                <p>Факт за последние месяцы и прогноз в BYN</p>
              </div>
              <div className="segmented" role="group" aria-label="Вид графика">
                <button aria-pressed={view === 'net'} onClick={() => setView('net')}>Чистый поток</button>
                <button aria-pressed={view === 'flows'} onClick={() => setView('flows')}>Поступления и расходы</button>
                <button aria-pressed={view === 'balance'} onClick={() => setView('balance')}>Остаток</button>
              </div>
            </div>
            <div className="chart-box">
              <ResponsiveContainer width="100%" height="100%">
                <ComposedChart data={chart} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                  <CartesianGrid stroke={LINE} vertical={false} />
                  <XAxis dataKey="label" tickLine={false} axisLine={false} tick={{ fill: MUTED, fontSize: 12 }} />
                  <YAxis tickLine={false} axisLine={false} width={56} tick={{ fill: MUTED, fontSize: 12 }} tickFormatter={compact} />
                  <Tooltip formatter={tooltipFormatter} contentStyle={{ borderRadius: 10, border: `1px solid ${LINE}`, boxShadow: '0 8px 24px rgba(16,20,24,.1)', fontSize: 13 }} />
                  {view !== 'flows' && <ReferenceLine y={0} stroke="#cfd4da" />}
                  {view === 'net' && <>
                    <Area dataKey="band" name="Интервал 80%" stroke="none" fill={ACCENT} fillOpacity={0.14} isAnimationActive={false} connectNulls />
                    <Line dataKey="net" name="Факт" stroke={INK} strokeWidth={2} dot={{ r: 3, fill: INK, strokeWidth: 0 }} isAnimationActive={false} connectNulls />
                    <Line dataKey="netF" name="Прогноз" stroke={ACCENT} strokeWidth={2.25} strokeDasharray="6 4" dot={{ r: 3, fill: ACCENT, strokeWidth: 0 }} isAnimationActive={false} connectNulls />
                  </>}
                  {view === 'flows' && <>
                    <Line dataKey="inH" name="Поступления, факт" stroke={ACCENT} strokeWidth={2} dot={false} isAnimationActive={false} connectNulls />
                    <Line dataKey="inF" name="Поступления, прогноз" stroke={ACCENT} strokeWidth={2} strokeDasharray="6 4" dot={{ r: 3, fill: ACCENT, strokeWidth: 0 }} isAnimationActive={false} connectNulls />
                    <Line dataKey="outH" name="Расходы, факт" stroke={NEG} strokeWidth={2} dot={false} isAnimationActive={false} connectNulls />
                    <Line dataKey="outF" name="Расходы, прогноз" stroke={NEG} strokeWidth={2} strokeDasharray="6 4" dot={{ r: 3, fill: NEG, strokeWidth: 0 }} isAnimationActive={false} connectNulls />
                  </>}
                  {view === 'balance' && <>
                    <Area dataKey="bal" name="Остаток" stroke={BRASS} strokeWidth={2} fill={BRASS} fillOpacity={0.1} dot={{ r: 3, fill: BRASS, strokeWidth: 0 }} isAnimationActive={false} connectNulls />
                  </>}
                </ComposedChart>
              </ResponsiveContainer>
            </div>
            <div className="legend">
              {view === 'net' && <><span><i style={{ borderColor: INK }} />Факт</span><span><i className="dash" style={{ borderColor: ACCENT }} />Прогноз</span><span><i className="band" />Ориентировочный интервал 80% по ошибкам бэктеста</span></>}
              {view === 'flows' && <><span><i style={{ borderColor: ACCENT }} />Поступления</span><span><i style={{ borderColor: NEG }} />Расходы</span><span><i className="dash" style={{ borderColor: MUTED }} />Прогноз</span></>}
              {view === 'balance' && <span><i style={{ borderColor: BRASS }} />Текущий остаток плюс накопленный чистый поток</span>}
            </div>
          </section>

          <div className="grid-main">
            <section className="panel">
              <div className="panel-head">
                <div><h2>Точность моделей</h2><p>Скользящий бэктест по чистому потоку: меньше ошибка — лучше. Нажмите на строку, чтобы пересчитать этим методом.</p></div>
              </div>
              {result.comparison && result.comparison.length > 0 ? (
                <div className="table-wrapper">
                  <table className="score-table">
                    <thead><tr><th>Модель</th><th className="right">Ошибка WAPE</th><th className="right">MAE, BYN</th><th /></tr></thead>
                    <tbody>
                      {result.comparison.map(c => (
                        <tr key={c.method} className={`clickable ${c.selected ? 'selected' : ''}`} onClick={() => { if (!c.selected && !loading) generate({ method: c.method }); }}>
                          <td><div className="name"><strong>{c.label}</strong><small>{c.family}</small></div></td>
                          <td className="right num">{c.wape == null ? '—' : `${nf1.format(c.wape)}%`}</td>
                          <td className="right num">{c.mae == null ? '—' : byn(c.mae)}</td>
                          <td className="right">{c.selected && <span className="tag">Выбрана</span>} {c.best && <span className="tag tag-brass">Лучшая</span>}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : <div className="inline-info" style={{ marginTop: 0 }}>Сравнение моделей появится после расчёта прогноза.</div>}
              {result.quality?.available && (
                <p className="muted" style={{ marginTop: 16, fontSize: 13 }}>
                  Выбранная модель: ошибка {result.quality.wape == null ? '—' : `${nf1.format(result.quality.wape)}%`} от оборота поступлений,
                  в среднем {byn(result.quality.mae)} BYN в месяц, окон проверки — {result.quality.windows}.
                  {bestModel && !bestModel.selected && <> На этих данных точнее «{bestModel.label}».</>}
                </p>
              )}
              {boostingOff && <p className="muted" style={{ marginTop: 8, fontSize: 13 }}>На этих данных бустинг не улучшил валидацию, поэтому прогноз опирается на затухающий тренд — так модель защищается от подгонки под шум.</p>}
            </section>

            <section className="panel">
              <div className="panel-head"><div><h2>Статистика ряда</h2><p>{insights?.sufficient ? `${insights.months} мес. истории` : 'Анализ истории операций'}</p></div></div>
              {!insights ? <p className="muted">Загрузка…</p> : !insights.sufficient ? (
                <p className="muted">{insights.findings[0]}</p>
              ) : (
                <div className="stack" style={{ gap: 18 }}>
                  <div className="kv">
                    <div><span className="k">Тренд поступлений</span><span className="v">{insights.inflow ? `${signed(insights.inflow.slopePercent)}% / мес.` : '—'}</span></div>
                    <div><span className="k">Тренд расходов</span><span className="v">{insights.outflow ? `${signed(insights.outflow.slopePercent)}% / мес.` : '—'}</span></div>
                    <div><span className="k">Норма накопления</span><span className="v">{insights.savingsRatePercent == null ? '—' : `${nf1.format(insights.savingsRatePercent)}%`}</span></div>
                    <div><span className="k">Запас хода</span><span className="v">{insights.runwayMonths == null ? 'Дефицита нет' : `${nf1.format(insights.runwayMonths)} мес.`}</span></div>
                    <div><span className="k">Волатильность поступлений</span><span className="v">{insights.inflow ? `${Math.round(insights.inflow.cv * 100)}%` : '—'}</span></div>
                    <div><span className="k">Сезонность</span><span className="v">{insights.inflow ? (insights.inflow.seasonalStrength > 0 ? `${Math.round(insights.inflow.seasonalStrength * 100)}%` : 'Нужно 24+ мес.') : '—'}</span></div>
                  </div>
                  <ul className="finding-list">{insights.findings.map(f => <li key={f}>{f}</li>)}</ul>
                </div>
              )}
            </section>
          </div>

          {insights?.sufficient && (insights.anomalies.length > 0 || insights.expenseCategories.length > 0) && (
            <div className="grid-2">
              <section className="panel">
                <div className="panel-head"><div><h2>Структура расходов</h2><p>Последние 12 месяцев</p></div></div>
                <div className="bars">
                  {insights.expenseCategories.map(c => (
                    <div className="bar-row" key={c.name}>
                      <span>{c.name}</span><span className="num">{byn(c.amount)} BYN · {Math.round(c.sharePercent)}%</span>
                      <div className="track"><i style={{ width: `${Math.max(2, c.sharePercent)}%` }} /></div>
                    </div>
                  ))}
                </div>
              </section>
              <section className="panel">
                <div className="panel-head"><div><h2>Аномальные месяцы</h2><p>Выбросы относительно тренда (критерий Айглевича–Хоаглина)</p></div></div>
                {insights.anomalies.length === 0 ? <p className="muted">Аномалий не обнаружено.</p> : (
                  <ul className="finding-list">
                    {insights.anomalies.map(a => (
                      <li key={`${a.month}-${a.series}`}><strong>{monthFull(a.month)}</strong>, {a.series.toLowerCase()}: {byn(a.value)} BYN при ожидаемых около {byn(a.expected)}</li>
                    ))}
                  </ul>
                )}
              </section>
            </div>
          )}

          <p className="muted" style={{ fontSize: 12.5, display: 'flex', gap: 8, alignItems: 'center' }}>
            <Icon name="lock" size={14} /> Расчёт выполняется на сервере по операциям компании в BYN; интервал показывает диапазон, в который по бэктесту попадает около 80% фактических значений.
          </p>
        </>
      )}
    </div>
  );
};

export default Forecast;
