import React, { useEffect, useState } from 'react';
import { useAuthStore } from '../store/authStore';
import apiClient from '../api/client';

interface Payment {
  id: string;
  title: string;
  amount: number;
  date: string;
  status: string;
  category: string;
  counterparty: string;
  description: string;
}

interface Counterparty {
  id: string;
  name: string;
}

interface CashFlowItem {
  id: string;
  name: string;
  type: string;
}

interface Company {
  id: string;
  name: string;
  ownerId: string;
}

const PaymentsList: React.FC = () => {
  const { user } = useAuthStore();
  const [allPayments, setAllPayments] = useState<Payment[]>([]);
  const [filteredPayments, setFilteredPayments] = useState<Payment[]>([]);
  const [counterparties, setCounterparties] = useState<Counterparty[]>([]);
  const [cashFlowItems, setCashFlowItems] = useState<CashFlowItem[]>([]);
  const [companies, setCompanies] = useState<Company[]>([]);
  const [loading, setLoading] = useState(true);
  const [viewMode, setViewMode] = useState<'all' | 'upcoming'>('upcoming');
  const [showForm, setShowForm] = useState(false);

  const [formData, setFormData] = useState({
    companyId: '',
    title: '',
    amount: '',
    dueDate: new Date().toISOString().split('T')[0],
    cashFlowItemId: '',
    counterpartyId: '',
    description: ''
  });

  const [searchTerm, setSearchTerm] = useState('');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [amountMin, setAmountMin] = useState('');
  const [amountMax, setAmountMax] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');

  const canEdit = [
    'ROLE_ADMIN',
    'ROLE_FINANCIAL_MANAGER',
    'ROLE_FINANCE_MANAGER',
    'ADMIN',
    'FINANCIAL_MANAGER',
    'FINANCE_MANAGER'
  ].includes(user?.role || '');

  useEffect(() => {
    loadInitialData();
  }, []);

  useEffect(() => {
    loadPayments();
  }, [viewMode]);

  useEffect(() => {
    applyFilters();
  }, [
    allPayments,
    searchTerm,
    dateFrom,
    dateTo,
    amountMin,
    amountMax,
    statusFilter
  ]);

  const loadInitialData = async () => {
    await Promise.all([
      loadCounterparties(),
      loadCashFlowItems(),
      loadCompanies()
    ]);
    await loadPayments();
  };

  const loadCounterparties = async () => {
    try {
      const response = await apiClient.get('/references/counterparties');
      setCounterparties(response.data);
    } catch (error) {
      console.error('Error loading counterparties:', error);
    }
  };

  const loadCashFlowItems = async () => {
    try {
      const response = await apiClient.get('/references/cash-flow-items');
      setCashFlowItems(response.data);
    } catch (error) {
      console.error('Error loading cash flow items:', error);
    }
  };

  const loadCompanies = async () => {
  try {
    const response = await apiClient.get('/companies');
    const allCompanies: Company[] = response.data;

    setCompanies(allCompanies);

    if (allCompanies.length > 0) {
      setFormData(previous => ({
        ...previous,
        companyId: previous.companyId || allCompanies[0].id
      }));
    }
  } catch (error) {
    console.error('Error loading companies:', error);
  }
};

  const loadPayments = async () => {
    try {
      setLoading(true);

      const response = await apiClient.get('/payment-requests');
      let data: Payment[] = response.data;

      if (viewMode === 'upcoming') {
        const today = new Date().toISOString().split('T')[0];
        data = data.filter(
          payment => payment.date >= today && payment.status !== 'PAID'
        );
      }

      setAllPayments(data);
    } catch (error) {
      console.error('Error loading payments:', error);
    } finally {
      setLoading(false);
    }
  };

  const createPayment = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!user?.id) {
      alert('Не удалось определить пользователя');
      return;
    }

    if (!formData.companyId) {
      alert('Для создания платежа необходимо выбрать компанию');
      return;
    }

    const selectedItem = cashFlowItems.find(
      item => item.id === formData.cashFlowItemId
    );

    const selectedCounterparty = counterparties.find(
      item => item.id === formData.counterpartyId
    );

    try {
      await apiClient.post(
        '/payments',
        {
          companyId: formData.companyId,
          title: formData.title.trim(),
          description: formData.description.trim(),
          amount: Number(formData.amount),
          dueDate: formData.dueDate,
          category: selectedItem?.name || '',
          cashFlowItemId: formData.cashFlowItemId,
          counterparty: selectedCounterparty?.name || '',
          counterpartyId: formData.counterpartyId || null
        },
        {
          params: { userId: user.id }
        }
      );

      setShowForm(false);

      setFormData(previous => ({
        ...previous,
        title: '',
        amount: '',
        dueDate: new Date().toISOString().split('T')[0],
        cashFlowItemId: '',
        counterpartyId: '',
        description: ''
      }));

      await loadPayments();
      alert('Платеж успешно создан!');
    } catch (error: any) {
      console.error('Error creating payment:', error);
      alert(error.response?.data?.message || 'Ошибка при создании платежа');
    }
  };

  const markAsPaid = async (id: string) => {
    try {
      await apiClient.patch(`/payment-requests/${id}/status`, null, {
        params: { status: 'PAID' }
      });

      await loadPayments();
    } catch (error) {
      console.error('Error marking as paid:', error);
    }
  };

  const deletePayment = async (id: string) => {
    if (!confirm('Удалить платеж?')) return;

    try {
      await apiClient.delete(`/payments/${id}`);
      await loadPayments();
    } catch (error: any) {
      console.error('Error deleting payment:', error);
      alert(error.response?.data?.message || 'Не удалось удалить платеж');
    }
  };

  const applyFilters = () => {
    let filtered = [...allPayments];

    if (searchTerm.trim()) {
      filtered = filtered.filter(payment =>
        payment.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
        payment.counterparty?.toLowerCase().includes(searchTerm.toLowerCase())
      );
    }

    if (dateFrom) {
      filtered = filtered.filter(payment => payment.date >= dateFrom);
    }

    if (dateTo) {
      filtered = filtered.filter(payment => payment.date <= dateTo);
    }

    if (amountMin) {
      filtered = filtered.filter(payment => payment.amount >= parseFloat(amountMin));
    }

    if (amountMax) {
      filtered = filtered.filter(payment => payment.amount <= parseFloat(amountMax));
    }

    if (statusFilter !== 'all') {
      filtered = filtered.filter(payment => payment.status === statusFilter);
    }

    setFilteredPayments(filtered);
  };

  const resetFilters = () => {
    setSearchTerm('');
    setDateFrom('');
    setDateTo('');
    setAmountMin('');
    setAmountMax('');
    setStatusFilter('all');
  };

  const formatNumber = (num: number) => {
    return num.toLocaleString('ru-RU', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    });
  };

  const expenseItems = cashFlowItems.filter(item => item.type === 'EXPENSE');

  const inputStyle = {
    width: '100%',
    padding: '12px 14px',
    border: '1px solid #d5d9de',
    borderRadius: '14px',
    fontSize: '14px',
    boxSizing: 'border-box' as const,
    height: '48px'
  };

  return (
    <div className="card">
      <div className="flex-between" style={{ marginBottom: '20px' }}>
        <h2>Платежный календарь</h2>

        <div style={{ display: 'flex', gap: '8px' }}>
          <button
            onClick={() => setViewMode('upcoming')}
            className="btn-secondary"
            style={viewMode === 'upcoming' ? {
              background: 'linear-gradient(135deg, #171717 0%, #303030 100%)',
              borderColor: '#171717',
              color: '#ffffff'
            } : {}}
          >
            Предстоящие
          </button>

          <button
            onClick={() => setViewMode('all')}
            className="btn-secondary"
            style={viewMode === 'all' ? {
              background: 'linear-gradient(135deg, #171717 0%, #303030 100%)',
              borderColor: '#171717',
              color: '#ffffff'
            } : {}}
          >
            Все платежи
          </button>
        </div>
      </div>

      <div style={{
        marginBottom: '20px',
        padding: '20px',
        background: '#f6f7f8',
        borderRadius: '16px',
        border: '1px solid #e7e9ec'
      }}>
        <div style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: '12px',
          marginBottom: '12px'
        }}>
          <input
            type="text"
            placeholder="Поиск..."
            value={searchTerm}
            onChange={e => setSearchTerm(e.target.value)}
            style={{ ...inputStyle, flex: 3 }}
          />

          <input
            type="date"
            value={dateFrom}
            onChange={e => setDateFrom(e.target.value)}
            style={{ ...inputStyle, flex: 1 }}
          />

          <input
            type="date"
            value={dateTo}
            onChange={e => setDateTo(e.target.value)}
            style={{ ...inputStyle, flex: 1 }}
          />
        </div>

        <div style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: '12px',
          justifyContent: 'space-between'
        }}>
          <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
            <select
              value={statusFilter}
              onChange={e => setStatusFilter(e.target.value)}
              style={{ ...inputStyle, width: '150px', background: 'white' }}
            >
              <option value="all">Все статусы</option>
              <option value="PENDING">Ожидает</option>
              <option value="APPROVED">Одобрено</option>
              <option value="REJECTED">Отклонено</option>
              <option value="PAID">Оплачено</option>
            </select>

            <input
              type="number"
              placeholder="От"
              value={amountMin}
              onChange={e => setAmountMin(e.target.value)}
              style={{ ...inputStyle, width: '120px' }}
            />

            <input
              type="number"
              placeholder="До"
              value={amountMax}
              onChange={e => setAmountMax(e.target.value)}
              style={{ ...inputStyle, width: '120px' }}
            />
          </div>

          <button
            onClick={resetFilters}
            className="btn-secondary"
            style={{ padding: '0 24px', height: '48px' }}
          >
            Сбросить
          </button>
        </div>
      </div>

      {canEdit && (
        <button
          onClick={() => setShowForm(!showForm)}
          className="btn-secondary"
          style={{ marginBottom: '16px' }}
        >
          {showForm ? 'Отмена' : 'Создать новый платеж'}
        </button>
      )}

      {showForm && (
        <form
          onSubmit={createPayment}
          style={{
            marginBottom: '24px',
            padding: '20px',
            background: 'rgba(90, 155, 192, 0.05)',
            borderRadius: '12px',
            border: '1px solid rgba(100, 150, 200, 0.2)'
          }}
        >
          <div className="form-grid">
            <div className="form-group">
              <label>Компания</label>
              <select
                value={formData.companyId}
                onChange={e => setFormData({
                  ...formData,
                  companyId: e.target.value
                })}
                required
                style={inputStyle}
              >
                <option value="">Выберите компанию</option>
                {companies.map(company => (
                  <option key={company.id} value={company.id}>
                    {company.name}
                  </option>
                ))}
              </select>
            </div>

            <div className="form-group">
              <label>Название платежа</label>
              <input
                type="text"
                value={formData.title}
                onChange={e => setFormData({
                  ...formData,
                  title: e.target.value
                })}
                required
                style={inputStyle}
              />
            </div>

            <div className="form-group">
              <label>Сумма</label>
              <input
                type="number"
                step="0.01"
                min="0.01"
                value={formData.amount}
                onChange={e => setFormData({
                  ...formData,
                  amount: e.target.value
                })}
                required
                style={inputStyle}
              />
            </div>

            <div className="form-group">
              <label>Дата</label>
              <input
                type="date"
                value={formData.dueDate}
                onChange={e => setFormData({
                  ...formData,
                  dueDate: e.target.value
                })}
                required
                style={inputStyle}
              />
            </div>

            <div className="form-group">
              <label>Статья движения денежных средств</label>
              <select
                value={formData.cashFlowItemId}
                onChange={e => setFormData({
                  ...formData,
                  cashFlowItemId: e.target.value
                })}
                required
                style={inputStyle}
              >
                <option value="">Выберите статью</option>
                {expenseItems.map(item => (
                  <option key={item.id} value={item.id}>
                    {item.name}
                  </option>
                ))}
              </select>
            </div>

            <div className="form-group">
              <label>Контрагент</label>
              <select
                value={formData.counterpartyId}
                onChange={e => setFormData({
                  ...formData,
                  counterpartyId: e.target.value
                })}
                style={inputStyle}
              >
                <option value="">Не выбран</option>
                {counterparties.map(counterparty => (
                  <option key={counterparty.id} value={counterparty.id}>
                    {counterparty.name}
                  </option>
                ))}
              </select>
            </div>

            <div className="form-group">
              <label>Описание</label>
              <input
                type="text"
                value={formData.description}
                onChange={e => setFormData({
                  ...formData,
                  description: e.target.value
                })}
                style={inputStyle}
              />
            </div>
          </div>

          <button
            type="submit"
            className="btn-primary"
            style={{ marginTop: '12px', height: '48px' }}
            disabled={companies.length === 0}
          >
            Сохранить платеж
          </button>
        </form>
      )}

      {loading ? (
        <div className="loading">Загрузка...</div>
      ) : (
        <div className="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>Дата</th>
                <th>Название</th>
                <th>Сумма</th>
                <th>Категория</th>
                <th>Контрагент</th>
                <th>Статус</th>
                <th>Действия</th>
              </tr>
            </thead>

            <tbody>
              {filteredPayments.map(payment => {
                const isOverdue =
                  new Date(payment.date) < new Date() &&
                  payment.status === 'PENDING';

                return (
                  <tr key={payment.id}>
                    <td>
                      {new Date(payment.date).toLocaleDateString('ru-RU')}
                    </td>

                    <td>
                      <strong>{payment.title}</strong>
                    </td>

                    <td className="amount-negative">
                      {formatNumber(payment.amount)}
                    </td>

                    <td>{payment.category || '—'}</td>

                    <td>{payment.counterparty || '—'}</td>

                    <td>
                      <span className={
                        payment.status === 'PAID'
                          ? 'badge-paid'
                          : payment.status === 'APPROVED'
                            ? 'badge-income'
                            : payment.status === 'REJECTED'
                              ? 'badge-expense'
                              : isOverdue
                                ? 'badge-overdue'
                                : 'badge-pending'
                      }>
                        {payment.status === 'PAID'
                          ? 'Оплачено'
                          : payment.status === 'APPROVED'
                            ? 'Одобрено'
                            : payment.status === 'REJECTED'
                              ? 'Отклонено'
                              : isOverdue
                                ? 'Просрочено'
                                : 'Ожидает'}
                      </span>
                    </td>

                    <td>
                      {payment.status === 'APPROVED' && canEdit && (
                        <button
                          onClick={() => markAsPaid(payment.id)}
                          className="btn-secondary"
                          style={{ marginRight: '8px' }}
                        >
                          Оплатить
                        </button>
                      )}

                      {canEdit && (
                        <button
                          onClick={() => deletePayment(payment.id)}
                          className="btn-danger"
                        >
                          Удалить
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default PaymentsList;