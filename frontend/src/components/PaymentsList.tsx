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

const PaymentsList: React.FC = () => {
  const { user } = useAuthStore();
  const [allPayments, setAllPayments] = useState<Payment[]>([]);
  const [filteredPayments, setFilteredPayments] = useState<Payment[]>([]);
  const [counterparties, setCounterparties] = useState<Counterparty[]>([]);
  const [loading, setLoading] = useState(true);
  const [viewMode, setViewMode] = useState<'all' | 'upcoming'>('upcoming');
  const [showForm, setShowForm] = useState(false);
  
  const [formData, setFormData] = useState({
    title: '',
    amount: '',
    dueDate: new Date().toISOString().split('T')[0],
    category: '',
    counterpartyId: '',
    description: ''
  });

  const [searchTerm, setSearchTerm] = useState('');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [amountMin, setAmountMin] = useState('');
  const [amountMax, setAmountMax] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');

  const canEdit = ['ROLE_ADMIN', 'ROLE_FINANCIAL_MANAGER', 'ROLE_FINANCE_MANAGER', 'ADMIN', 'FINANCIAL_MANAGER', 'FINANCE_MANAGER'].includes(user?.role || '');

  useEffect(() => {
    loadInitialData();
  }, [viewMode]);

  useEffect(() => {
    applyFilters();
  }, [allPayments, searchTerm, dateFrom, dateTo, amountMin, amountMax, statusFilter]);

  const loadInitialData = async () => {
    await loadCounterparties();
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

  const loadPayments = async () => {
    try {
      setLoading(true);
      const response = await apiClient.get('/payment-requests');
      let data = response.data;

      if (viewMode === 'upcoming') {
        const today = new Date().toISOString().split('T')[0];
        data = data.filter((p: Payment) => p.date >= today && p.status !== 'PAID');
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
    const userId = user?.id;
    if (!userId) return;

    const selectedCounterparty = counterparties.find(c => c.id === formData.counterpartyId);
    
    const payload = {
      title: formData.title.trim(),
      description: (formData.description || '').trim(),
      amount: Number(formData.amount),
      dueDate: formData.dueDate,
      category: (formData.category || '').trim(),
      counterparty: selectedCounterparty?.name || 'Частное лицо'
    };
    
    try {
      await apiClient.post('/payments', payload, { params: { userId } });
      setShowForm(false);
      setFormData({ title: '', amount: '', dueDate: new Date().toISOString().split('T')[0], category: '', counterpartyId: '', description: '' });
      await loadPayments();
      alert(" Платеж успешно создан!");
    } catch (error: any) {
      alert("Ошибка при создании платежа");
    }
  };

  const markAsPaid = async (id: string) => {
    try {
      await apiClient.patch(`/payment-requests/${id}/status`, null, {
        params: { status: 'PAID' }
      });
      loadPayments();
    } catch (error) {
      console.error('Error marking as paid:', error);
    }
  };

  const deletePayment = async (id: string) => {
    if (!confirm('Удалить платеж?')) return;
    try {
      await apiClient.delete(`/payments/${id}`);
      loadPayments();
    } catch (error) {
      console.error('Error deleting payment:', error);
    }
  };

  const applyFilters = () => {
    let filtered = [...allPayments];
    if (searchTerm.trim()) {
      filtered = filtered.filter(p => 
        p.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
        p.counterparty?.toLowerCase().includes(searchTerm.toLowerCase())
      );
    }
    if (dateFrom) filtered = filtered.filter(p => p.date >= dateFrom);
    if (dateTo) filtered = filtered.filter(p => p.date <= dateTo);
    if (amountMin) filtered = filtered.filter(p => p.amount >= parseFloat(amountMin));
    if (amountMax) filtered = filtered.filter(p => p.amount <= parseFloat(amountMax));
    
    if (statusFilter !== 'all') {
      filtered = filtered.filter(p => p.status === statusFilter);
    }
    setFilteredPayments(filtered);
  };

  const resetFilters = () => {
    setSearchTerm(''); setDateFrom(''); setDateTo(''); setAmountMin(''); setAmountMax('');
    setStatusFilter('all');
  };

  const formatNumber = (num: number) => {
    return num.toLocaleString('ru-RU', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  };

  const inputStyle = {
    width: '100%', padding: '12px 14px', border: '1px solid #d5d9de', borderRadius: '14px',
    fontSize: '14px', boxSizing: 'border-box' as const, height: '48px'
  };

  return (
    <div className="card">
      <div className="flex-between" style={{ marginBottom: '20px' }}>
        <h2> Платежный календарь</h2>
        <div style={{ display: 'flex', gap: '8px' }}>
          <button onClick={() => setViewMode('upcoming')} className="btn-secondary" style={viewMode === 'upcoming' ? { background: '#14513f', color: 'white' } : {}}> Предстоящие </button>
          <button onClick={() => setViewMode('all')} className="btn-secondary" style={viewMode === 'all' ? { background: '#14513f', color: 'white' } : {}}> Все платежи </button>
        </div>
      </div>

      <div style={{ marginBottom: '20px', padding: '20px', background: '#f6f7f8', borderRadius: '16px', border: '1px solid #e7e9ec' }}>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '12px', marginBottom: '12px' }}>
          <input type="text" placeholder=" Поиск..." value={searchTerm} onChange={(e) => setSearchTerm(e.target.value)} style={{ ...inputStyle, flex: 3 }} />
          <input type="date" value={dateFrom} onChange={(e) => setDateFrom(e.target.value)} style={{ ...inputStyle, flex: 1 }} />
          <input type="date" value={dateTo} onChange={(e) => setDateTo(e.target.value)} style={{ ...inputStyle, flex: 1 }} />
        </div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '12px', justifyContent: 'space-between' }}>
          <div style={{ display: 'flex', gap: '12px' }}>
            <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} style={{ ...inputStyle, width: '150px', background: 'white' }}>
              <option value="all">Все статусы</option>
              <option value="PENDING">Ожидает</option>
              <option value="APPROVED">Одобрено</option>
              <option value="REJECTED">Отклонено</option>
              <option value="PAID">Оплачено</option>
            </select>
            <input type="number" placeholder="От" value={amountMin} onChange={(e) => setAmountMin(e.target.value)} style={{ ...inputStyle, width: '120px' }} />
            <input type="number" placeholder="До" value={amountMax} onChange={(e) => setAmountMax(e.target.value)} style={{ ...inputStyle, width: '120px' }} />
          </div>
          <button onClick={resetFilters} className="btn-secondary" style={{ padding: '0 24px', height: '48px' }}>Сбросить</button>
        </div>
      </div>

      {canEdit && (
        <button onClick={() => setShowForm(!showForm)} className="btn-secondary" style={{ marginBottom: '16px' }}>
          {showForm ? ' Отмена' : ' Создать новый платеж'}
        </button>
      )}

      {showForm && (
        <form onSubmit={createPayment} style={{ marginBottom: '24px', padding: '20px', background: 'rgba(90, 155, 192, 0.05)', borderRadius: '12px', border: '1px solid rgba(100, 150, 200, 0.2)' }}>
          <div className="form-grid">
            <div className="form-group">
              <label>Название платежа</label>
              <input type="text" value={formData.title} onChange={(e) => setFormData({ ...formData, title: e.target.value })} required style={inputStyle} />
            </div>
            <div className="form-group">
              <label>Сумма</label>
              <input type="number" step="0.01" value={formData.amount} onChange={(e) => setFormData({ ...formData, amount: e.target.value })} required style={inputStyle} />
            </div>
            <div className="form-group">
              <label>Дата</label>
              <input type="date" value={formData.dueDate} onChange={(e) => setFormData({ ...formData, dueDate: e.target.value })} required style={inputStyle} />
            </div>
            <div className="form-group">
              <label>Категория</label>
              <input type="text" value={formData.category} onChange={(e) => setFormData({ ...formData, category: e.target.value })} style={inputStyle} />
            </div>
            <div className="form-group">
              <label>Контрагент</label>
              <select value={formData.counterpartyId} onChange={(e) => setFormData({ ...formData, counterpartyId: e.target.value })} style={inputStyle}>
                <option value="">Выберите контрагента</option>
                {counterparties.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </div>
            <div className="form-group">
              <label>Описание</label>
              <input type="text" value={formData.description} onChange={(e) => setFormData({ ...formData, description: e.target.value })} style={inputStyle} />
            </div>
          </div>
          <button type="submit" className="btn-primary" style={{ marginTop: '12px', height: '48px' }}>Сохранить платеж</button>
        </form>
      )}

      {loading ? <div className="loading">Загрузка...</div> : (
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
              </tr>
            </thead>
            <tbody>
              {filteredPayments.map((p) => {
                const isOverdue = new Date(p.date) < new Date() && p.status === 'PENDING';
                return (
                  <tr key={p.id}>
                    <td>{new Date(p.date).toLocaleDateString('ru-RU')}</td>
                    <td><strong>{p.title}</strong></td>
                    <td className="amount-negative">{formatNumber(p.amount)}</td>
                    <td>{p.category || '—'}</td>
                    <td>{p.counterparty || '—'}</td>
                    <td>
                      <span className={p.status === 'PAID' ? 'badge-paid' : p.status === 'APPROVED' ? 'badge-income' : p.status === 'REJECTED' ? 'badge-expense' : (isOverdue ? 'badge-overdue' : 'badge-pending')}>
                        {p.status === 'PAID' ? 'Оплачено' : p.status === 'APPROVED' ? 'Одобрено' : p.status === 'REJECTED' ? 'Отклонено' : (isOverdue ? 'Просрочено' : 'Ожидает')}
                      </span>
                    </td>
                    <td>
                      {p.status === 'APPROVED' && canEdit && <button onClick={() => markAsPaid(p.id)} className="btn-secondary" style={{ marginRight: '8px' }}>Оплатить</button>}
                      {canEdit && <button onClick={() => deletePayment(p.id)} className="btn-danger">Удалить</button>}
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