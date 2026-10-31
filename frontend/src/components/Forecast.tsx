import React, { useEffect, useMemo, useState } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { useAuthStore } from '../store/authStore';
import apiClient from '../api/client';

interface Company {
  id: string;
  name: string;
}

interface ForecastPoint {
  date: string;
  inflow: number;
  outflow: number;
  netCashFlow: number;
}

interface ForecastResult {
  id: string;
  totalProjectedInflow: number;
  totalProjectedOutflow: number;
  netCashFlow: number;
  strategyUsed: string;
  period: string;
  items?: ForecastPoint[];
}

interface ForecastParameters {
  companyId: string;
  months: number;
  strategy: string;
  scenario: string;
}

const STRATEGY_LABELS: Record<string, string> = {
  gradientBoosting: 'Ансамблевый градиентный бустинг',
  GRADIENT_BOOSTING: 'Ансамблевый градиентный бустинг',
  linearRegression: 'Линейная регрессия',
  LINEAR_REGRESSION: 'Линейная регрессия',
  movingAverage: 'Скользящее среднее',
  MOVING_AVERAGE: 'Скользящее среднее',
};

const SCENARIO_LABELS: Record<string, string> = {
  BASE: 'Базовый',
  OPTIMISTIC: 'Оптимистичный',
  PESSIMISTIC: 'Консервативный',
};

const money = (value: number) =>
  (value || 0).toLocaleString('ru-RU', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });

const monthLabel = (date: string) =>
  new Date(`${date}T12:00:00`).toLocaleDateString('ru-RU', {
    month: 'short',
    year: '2-digit',
  });

const Forecast: React.FC = () => {
  const { user } = useAuthStore();

  const [companies, setCompanies] = useState<Company[]>([]);
  const [companyId, setCompanyId] = useState('');
  const [months, setMonths] = useState(6);
  const [strategy, setStrategy] = useState('gradientBoosting');
  const [scenario, setScenario] = useState('BASE');

  const [loading, setLoading] = useState(false);
  const [loadingCompanies, setLoadingCompanies] = useState(true);
  const [error, setError] = useState('');

  const [result, setResult] = useState<ForecastResult | null>(null);
  const [calculatedParameters, setCalculatedParameters] =
    useState<ForecastParameters | null>(null);

  useEffect(() => {
    if (!user?.id) {
      setLoadingCompanies(false);
      return;
    }

    let cancelled = false;

    apiClient
      .get('/companies', { params: { ownerId: user.id } })
      .then(({ data }) => {
        if (cancelled) return;

        const list: Company[] = Array.isArray(data) ? data : [];
        setCompanies(list);

        if (list.length) {
          setCompanyId(list[0].id);
        } else {
          setError(
            'Для пользователя не найдена компания. Проверьте данные компании в базе данных.',
          );
        }
      })
      .catch(() => {
        if (!cancelled) {
          setError(
            'Не удалось загрузить компании. Проверьте подключение и права доступа.',
          );
        }
      })
      .finally(() => {
        if (!cancelled) setLoadingCompanies(false);
      });

    return () => {
      cancelled = true;
    };
  }, [user?.id]);

  const resetResult = () => {
    setResult(null);
    setCalculatedParameters(null);
    setError('');
  };

  const generateForecast = async () => {
    if (!companyId) {
      setError('Сначала выберите компанию.');
      return;
    }

    setLoading(true);
    setError('');
    setResult(null);
    setCalculatedParameters(null);

    const requestParameters: ForecastParameters = {
      companyId,
      months,
      strategy,
      scenario,
    };

    try {
      const multiplier =
        scenario === 'OPTIMISTIC'
          ? 1.15
          : scenario === 'PESSIMISTIC'
            ? 0.85
            : 1;

      const { data } = await apiClient.get('/forecast/generate', {
        params: {
          companyId,
          months,
          strategy,
          multiplier,
        },
      });

      setResult(data);
      setCalculatedParameters(requestParameters);
    } catch (e: any) {
      setResult(null);
      setCalculatedParameters(null);
      setError(
        e?.response?.data?.message ||
          'Не удалось построить прогноз. Проверьте права пользователя и наличие транзакций.',
      );
    } finally {
      setLoading(false);
    }
  };

  const chartData = useMemo(
    () =>
      (result?.items || []).map((point) => ({
        ...point,
        label: monthLabel(point.date),
        inflow: Number(point.inflow || 0),
        outflow: Number(point.outflow || 0),
        netCashFlow: Number(point.netCashFlow || 0),
      })),
    [result],
  );

  const selectedCompanyName =
    companies.find(
      (company) => company.id === calculatedParameters?.companyId,
    )?.name || '';

  const calculatedStrategy =
    calculatedParameters?.strategy || strategy;

  const calculatedScenario =
    calculatedParameters?.scenario || scenario;

  const isParametersChanged =
    !!calculatedParameters &&
    (calculatedParameters.companyId !== companyId ||
      calculatedParameters.months !== months ||
      calculatedParameters.strategy !== strategy ||
      calculatedParameters.scenario !== scenario);

  return (
    <div className="forecast-page">
      <section className="page-heading">
        <div>
          <div className="eyebrow">ФИНАНСОВОЕ ПЛАНИРОВАНИЕ</div>
          <h1>Прогноз денежных потоков</h1>
          <p>
            Сценарное моделирование на основе исторических операций компании.
          </p>
        </div>

        <div className="model-chip">
          <span className="model-dot" /> Модели прогнозирования
        </div>
      </section>

      <section className="forecast-control-panel">
        <div className="section-heading">
          <div>
            <h2>Параметры расчёта</h2>
            <p>Выберите компанию, горизонт и модель прогнозирования.</p>
          </div>
        </div>

        <div className="forecast-controls">
          <label className="field-label">
            Компания
            <select
              value={companyId}
              onChange={(e) => {
                setCompanyId(e.target.value);
                resetResult();
              }}
              disabled={loadingCompanies || !companies.length || loading}
            >
              {companies.map((company) => (
                <option value={company.id} key={company.id}>
                  {company.name}
                </option>
              ))}
            </select>
          </label>

          <label className="field-label">
            Горизонт прогноза
            <select
              value={months}
              onChange={(e) => {
                setMonths(Number(e.target.value));
                resetResult();
              }}
              disabled={loading}
            >
              <option value={3}>3 месяца</option>
              <option value={6}>6 месяцев</option>
              <option value={12}>12 месяцев</option>
              <option value={18}>18 месяцев</option>
              <option value={24}>24 месяца</option>
            </select>
          </label>

          <label className="field-label">
            Метод прогнозирования
            <select
              value={strategy}
              onChange={(e) => {
                setStrategy(e.target.value);
                resetResult();
              }}
              disabled={loading}
            >
              <option value="gradientBoosting">
                Ансамблевый градиентный бустинг
              </option>
              <option value="linearRegression">
                Линейная регрессия
              </option>
              <option value="movingAverage">
                Скользящее среднее
              </option>
            </select>
          </label>

          <label className="field-label">
            Сценарий
            <select
              value={scenario}
              onChange={(e) => {
                setScenario(e.target.value);
                resetResult();
              }}
              disabled={loading}
            >
              <option value="BASE">Базовый</option>
              <option value="OPTIMISTIC">Оптимистичный</option>
              <option value="PESSIMISTIC">Консервативный</option>
            </select>
          </label>
        </div>

        <div className="forecast-actions">
          <div className="scenario-note">
            {scenario === 'BASE'
              ? 'Без корректировки исходных допущений'
              : scenario === 'OPTIMISTIC'
                ? 'Доходы увеличены на 15%'
                : 'Доходы снижены на 15%, расходы увеличены на 10%'}
          </div>

          <button
            className="btn-primary forecast-submit"
            onClick={generateForecast}
            disabled={loading || loadingCompanies || !companyId}
          >
            {loading ? 'Выполняется расчёт…' : 'Рассчитать прогноз'}
          </button>
        </div>

        {error && (
          <div className="inline-error" role="alert">
            {error}
          </div>
        )}
      </section>

      {loading && (
        <div className="forecast-empty">
          Расчёт прогнозной модели выполняется…
        </div>
      )}

      {!loading && !result && !error && (
        <div className="forecast-empty">
          <h3>Прогноз ещё не рассчитан</h3>
          <p>
            Настройте параметры выше, чтобы сформировать прогноз и сравнить
            будущие доходы с расходами.
          </p>
        </div>
      )}

      {!loading && result && (
        <>
          {isParametersChanged && (
            <div className="inline-warning">
              Параметры изменены. Рассчитайте прогноз повторно, чтобы увидеть
              результат для новых настроек.
            </div>
          )}

          <div className="forecast-kpis">
            <article className="forecast-kpi forecast-kpi-primary">
              <span>Чистый денежный поток</span>
              <strong>{money(Number(result.netCashFlow))} BYN</strong>
              <small>
                За {calculatedParameters?.months ?? months} мес.
              </small>
            </article>

            <article className="forecast-kpi">
              <span>Прогнозируемые поступления</span>
              <strong className="positive-value">
                {money(Number(result.totalProjectedInflow))} BYN
              </strong>
              <small>Суммарно за период</small>
            </article>

            <article className="forecast-kpi">
              <span>Прогнозируемые расходы</span>
              <strong className="negative-value">
                {money(Number(result.totalProjectedOutflow))} BYN
              </strong>
              <small>Суммарно за период</small>
            </article>
          </div>

          <section className="forecast-chart-panel">
            <div className="section-heading">
              <div>
                <h2>Помесячный прогноз</h2>
                <p>Оценка будущих поступлений и расходов в BYN.</p>
              </div>

              <span className="chart-period">{result.period}</span>
            </div>

            {chartData.length ? (
              <div className="forecast-chart">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart
                    data={chartData}
                    margin={{ top: 12, right: 16, left: 8, bottom: 4 }}
                  >
                    <CartesianGrid
                      strokeDasharray="3 3"
                      vertical={false}
                      stroke="#e7ebf1"
                    />

                    <XAxis
                      dataKey="label"
                      tickLine={false}
                      axisLine={false}
                      tick={{ fill: '#7c8798', fontSize: 12 }}
                    />

                    <YAxis
                      tickLine={false}
                      axisLine={false}
                      tick={{ fill: '#7c8798', fontSize: 12 }}
                      tickFormatter={(value) =>
                        new Intl.NumberFormat('ru-RU', {
                          notation: 'compact',
                          maximumFractionDigits: 1,
                        }).format(Number(value))
                      }
                    />

                    <Tooltip
                      formatter={(value) =>
                        `${money(Number(value))} BYN`
                      }
                      contentStyle={{
                        borderRadius: 12,
                        border: '1px solid #e3e8ef',
                        boxShadow: '0 8px 24px rgba(16,24,40,.08)',
                      }}
                    />

                    <Legend />

                    <Bar
                      dataKey="inflow"
                      name="Поступления"
                      fill="#1d796c"
                      radius={[5, 5, 0, 0]}
                    />

                    <Bar
                      dataKey="outflow"
                      name="Расходы"
                      fill="#b66c62"
                      radius={[5, 5, 0, 0]}
                    />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            ) : (
              <div className="inline-warning">
                Итоговые значения рассчитаны, но помесячные данные не вернулись
                от сервера. Проверьте формат ответа API.
              </div>
            )}

            <div className="forecast-footnote">
              <div>
                <strong>Модель:</strong>{' '}
                {STRATEGY_LABELS[calculatedStrategy] ||
                  result.strategyUsed ||
                  calculatedStrategy}
              </div>

              <div>
                <strong>Сценарий:</strong>{' '}
                {SCENARIO_LABELS[calculatedScenario] || calculatedScenario}
              </div>

              {selectedCompanyName && (
                <div>
                  <strong>Компания:</strong> {selectedCompanyName}
                </div>
              )}
            </div>
          </section>
        </>
      )}
    </div>
  );
};

export default Forecast;