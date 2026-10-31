import React, { useEffect, useState } from 'react';
import {
  BarChart,
  Bar,
  LineChart,
  Line,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts';
import apiClient from '../api/client';

interface Stats {
  totalUsers: number;
  totalCompanies: number;
  totalTransactions: number;
  totalPayments: number;
  monthlyIncome: number;
  monthlyExpense: number;
}

interface MonthlyData {
  month: string;
  income: number;
  expense: number;
}

interface CategoryData {
  name: string;
  value: number;
}

const COLORS = [
  '#1d796c',
  '#7ab3cc',
  '#b66c62',
  '#e0b66d',
  '#8b91c7',
  '#b8c2cc',
];

const DashboardAnalytics: React.FC = () => {
  const [stats, setStats] = useState<Stats>({
    totalUsers: 0,
    totalCompanies: 0,
    totalTransactions: 0,
    totalPayments: 0,
    monthlyIncome: 0,
    monthlyExpense: 0,
  });

  const [monthlyData, setMonthlyData] = useState<MonthlyData[]>([]);
  const [categoryData, setCategoryData] = useState<CategoryData[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchAllAnalytics();
  }, []);

  const fetchAllAnalytics = async () => {
    setLoading(true);

    try {
      const [statsRes, monthlyRes, categoryRes] = await Promise.all([
        apiClient.get('/analytics/stats'),
        apiClient.get('/analytics/monthly-dynamics'),
        apiClient.get('/analytics/category-distribution'),
      ]);

      setStats(statsRes.data);
      setMonthlyData(monthlyRes.data);
      setCategoryData(categoryRes.data);
    } catch (error) {
      console.error('Ошибка при загрузке аналитики:', error);
    } finally {
      setLoading(false);
    }
  };

  const formatNumber = (num: number) =>
    (num || 0).toLocaleString('ru-RU', {
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    });

  // Объединяем похожие категории в крупные направления.
  const normalizeCategory = (name: string): string => {
    const value = (name || '').trim().toLowerCase();

    if (
      /зарплат|заработн|персонал|сотрудник|обучени|тренинг|оклад|преми/.test(
        value
      )
    ) {
      return 'Зарплата и персонал';
    }

    if (
      /разработ|программ|лицензи|подписк|saas|домен|хостинг|\bit\b|software|облачн/.test(
        value
      )
    ) {
      return 'Разработка и IT';
    }

    if (
      /аренд|офис|сервер|оборудован|ремонт|инфраструктур|компьютер|мебел|коммунал/.test(
        value
      )
    ) {
      return 'Офис и инфраструктура';
    }

    if (/налог|банк|комисси|банковск|пошлин/.test(value)) {
      return 'Налоги и банковские услуги';
    }

    if (
      /маркетинг|реклам|командиров|такси|билет|гостиниц|клиентск|встреч|продвижен|транспорт|логистик/.test(
        value
      )
    ) {
      return 'Маркетинг и командировки';
    }

    return 'Прочее';
  };

  // Суммируем значения внутри каждой группы.
  const groupedCategoryData = Object.values(
    categoryData.reduce<Record<string, CategoryData>>((groups, item) => {
      const name = normalizeCategory(item.name);
      const value = Number(item.value) || 0;

      if (!groups[name]) {
        groups[name] = { name, value: 0 };
      }

      groups[name].value += value;
      return groups;
    }, {})
  )
    .filter((item) => item.value > 0)
    .sort((a, b) => b.value - a.value);

  // Оставляем пять основных направлений.
  // Все остальные группы объединяем в «Прочее».
  const mainCategories = groupedCategoryData
    .filter((item) => item.name !== 'Прочее')
    .slice(0, 5);

  const otherValue = groupedCategoryData
    .filter(
      (item) =>
        item.name === 'Прочее' ||
        !mainCategories.some((main) => main.name === item.name)
    )
    .reduce((sum, item) => sum + item.value, 0);

  const chartData: CategoryData[] = [
    ...mainCategories,
    ...(otherValue > 0
      ? [{ name: 'Прочее', value: otherValue }]
      : []),
  ].sort((a, b) => b.value - a.value);

  const totalCategoryValue = chartData.reduce(
    (sum, item) => sum + item.value,
    0
  );

  const CustomTooltip = ({ active, payload, label }: any) => {
    if (!active || !payload || payload.length === 0) {
      return null;
    }

    return (
      <div
        style={{
          background: '#ffffff',
          padding: '10px 14px',
          borderRadius: '12px',
          border: '1px solid #e2edf7',
          boxShadow: '0 4px 12px rgba(0,0,0,0.1)',
        }}
      >
        {label && (
          <div
            style={{
              fontWeight: 600,
              marginBottom: '5px',
              color: '#1c2e43',
            }}
          >
            {label}
          </div>
        )}

        {payload.map((p: any, idx: number) => (
          <div
            key={idx}
            style={{
              color: p.color || p.payload?.fill || '#1c2e43',
              fontSize: '13px',
              margin: '3px 0',
            }}
          >
            {p.name || p.payload?.name}:{' '}
            <strong>{formatNumber(Number(p.value) || 0)} BYN</strong>
          </div>
        ))}
      </div>
    );
  };

  if (loading) {
    return (
      <div
        className="loading"
        style={{ padding: '50px', textAlign: 'center' }}
      >
        ⏳ Считаем ваши деньги...
      </div>
    );
  }

  return (
    <div className="analytics-container">
      
      <div
        className="stats-grid"
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
          gap: '20px',
          marginBottom: '28px',
        }}
      >
        <div className="stat-card">
          <div className="stat-value">{stats.totalTransactions}</div>
          <div className="stat-label">Всего транзакций</div>
        </div>

        <div className="stat-card">
          <div className="stat-value">{stats.totalPayments}</div>
          <div className="stat-label">Ожидаемых платежей</div>
        </div>

        <div className="stat-card">
          <div className="stat-value" style={{ color: '#1d796c' }}>
            {formatNumber(stats.monthlyIncome)} BYN
          </div>
          <div className="stat-label">Доходы за месяц</div>
        </div>

        <div className="stat-card">
          <div className="stat-value" style={{ color: '#b66c62' }}>
            {formatNumber(stats.monthlyExpense)} BYN
          </div>
          <div className="stat-label">Расходы за месяц</div>
        </div>

        <div
          className="stat-card"
          style={{
            background: 'linear-gradient(135deg, #f0f7ff 0%, #ffffff 100%)',
          }}
        >
          <div
            className="stat-value"
            style={{
              color:
                stats.monthlyIncome - stats.monthlyExpense >= 0
                  ? '#1d796c'
                  : '#b66c62',
            }}
          >
            {formatNumber(stats.monthlyIncome - stats.monthlyExpense)} BYN
          </div>
          <div className="stat-label">Текущий остаток</div>
        </div>
      </div>

      
      <div className="card" style={{ marginBottom: '28px' }}>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: '12px',
            marginBottom: '20px',
          }}
        >
          <h2>Динамика денежных потоков</h2>

          <button
            onClick={fetchAllAnalytics}
            className="btn-secondary"
            style={{ fontSize: '12px' }}
          >
            Обновить
          </button>
        </div>

        <div style={{ height: '350px', width: '100%' }}>
          {monthlyData.length > 0 ? (
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={monthlyData}>
                <CartesianGrid
                  strokeDasharray="3 3"
                  stroke="#e2edf7"
                  vertical={false}
                />

                <XAxis
                  dataKey="month"
                  tick={{ fill: '#8792a2', fontSize: 12 }}
                  axisLine={false}
                />

                <YAxis
                  tick={{ fill: '#8792a2', fontSize: 12 }}
                  axisLine={false}
                />

                <Tooltip content={<CustomTooltip />} />
                <Legend iconType="circle" />

                <Line
                  type="monotone"
                  dataKey="income"
                  stroke="#1d796c"
                  strokeWidth={3}
                  name="Доходы"
                  dot={{ r: 4, fill: '#1d796c' }}
                  activeDot={{ r: 6 }}
                />

                <Line
                  type="monotone"
                  dataKey="expense"
                  stroke="#b66c62"
                  strokeWidth={3}
                  name="Расходы"
                  dot={{ r: 4, fill: '#b66c62' }}
                  activeDot={{ r: 6 }}
                />
              </LineChart>
            </ResponsiveContainer>
          ) : (
            <div
              style={{
                textAlign: 'center',
                color: '#8792a2',
                paddingTop: '100px',
              }}
            >
              Нет данных для отображения графика
            </div>
          )}
        </div>
      </div>

      
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 400px), 1fr))',
          gap: '28px',
        }}
      >
        
        <div className="card">
          <h2>Распределение по статьям</h2>

          <div style={{ height: '320px', width: '100%' }}>
            {chartData.length > 0 ? (
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={chartData}
                    cx="50%"
                    cy="45%"
                    innerRadius={55}
                    outerRadius={95}
                    paddingAngle={3}
                    dataKey="value"
                    nameKey="name"
                    labelLine={false}
                    label={({ percent }) =>
                      `${((percent || 0) * 100).toFixed(0)}%`
                    }
                  >
                    {chartData.map((item, index) => (
                      <Cell
                        key={item.name}
                        fill={COLORS[index % COLORS.length]}
                        stroke="#ffffff"
                        strokeWidth={2}
                      />
                    ))}
                  </Pie>

                  <Tooltip content={<CustomTooltip />} />

                  <Legend
                    verticalAlign="bottom"
                    align="center"
                    iconType="circle"
                    formatter={(value: string, entry: any) => {
                      const item = entry.payload as CategoryData;
                      const percent =
                        totalCategoryValue > 0
                          ? (item.value / totalCategoryValue) * 100
                          : 0;

                      return (
                        <span
                          style={{
                            color: '#435166',
                            fontSize: '12px',
                          }}
                        >
                          {value} — {percent.toFixed(1)}%
                        </span>
                      );
                    }}
                  />
                </PieChart>
              </ResponsiveContainer>
            ) : (
              <div
                style={{
                  textAlign: 'center',
                  color: '#8792a2',
                  paddingTop: '80px',
                }}
              >
                Добавьте транзакции, чтобы увидеть структуру
              </div>
            )}
          </div>
        </div>

        
        <div className="card">
          <h2>Сравнение периодов</h2>

          <div style={{ height: '320px', width: '100%' }}>
            {monthlyData.length > 0 ? (
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={monthlyData.slice(-4)}>
                  <CartesianGrid
                    strokeDasharray="3 3"
                    stroke="#e2edf7"
                    vertical={false}
                  />

                  <XAxis
                    dataKey="month"
                    tick={{ fill: '#8792a2' }}
                    axisLine={false}
                  />

                  <YAxis
                    tick={{ fill: '#8792a2' }}
                    axisLine={false}
                  />

                  <Tooltip content={<CustomTooltip />} />
                  <Legend />

                  <Bar
                    dataKey="income"
                    fill="#1d796c"
                    name="Доходы"
                    radius={[6, 6, 0, 0]}
                    barSize={30}
                  />

                  <Bar
                    dataKey="expense"
                    fill="#b66c62"
                    name="Расходы"
                    radius={[6, 6, 0, 0]}
                    barSize={30}
                  />
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <div
                style={{
                  textAlign: 'center',
                  color: '#8792a2',
                  paddingTop: '80px',
                }}
              >
                Нет данных для сравнения
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default DashboardAnalytics;