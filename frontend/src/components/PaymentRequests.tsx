import React, { useEffect, useState } from 'react';
import { useAuthStore } from '../store/authStore';
import apiClient from '../api/client';

interface Request {
  id: string;
  title: string;
  amount: number;
  date: string;
  status: string; 
  requester: string;
  counterparty: string;
  category: string;
  description?: string;
}

const PaymentRequests: React.FC = () => {
  const { user } = useAuthStore();
  const [allRequests, setAllRequests] = useState<Request[]>([]);
  const [filteredRequests, setFilteredRequests] = useState<Request[]>([]);
  const [loading, setLoading] = useState(true);

  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');
  const [categoryFilter, setCategoryFilter] = useState('all');

  const normalizedRole = (user?.role || '').replace('ROLE_', '').replace('FINANCIAL_MANAGER', 'FINANCE_MANAGER');
  const isDirector = normalizedRole === 'ADMIN' || normalizedRole === 'DIRECTOR';

  useEffect(() => {
    if (isDirector) loadRequests();
  }, [isDirector]);

  useEffect(() => {
    applyFilters();
  }, [allRequests, searchTerm, statusFilter, categoryFilter]);

  const loadRequests = async () => {
    try {
      setLoading(true);
      const response = await apiClient.get('/payment-requests');
      setAllRequests(response.data);
    } catch (error) {
      console.error('Error loading requests:', error);
    } finally {
      setLoading(false);
    }
  };

  const applyFilters = () => {
    let filtered = [...allRequests];
    if (searchTerm.trim()) {
      filtered = filtered.filter(r => 
        r.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
        r.counterparty.toLowerCase().includes(searchTerm.toLowerCase())
      );
    }
    if (statusFilter !== 'all') filtered = filtered.filter(r => r.status === statusFilter);
    if (categoryFilter !== 'all') filtered = filtered.filter(r => r.category === categoryFilter);
    setFilteredRequests(filtered);
  };

  const resetFilters = () => {
    setSearchTerm(''); setStatusFilter('all'); setCategoryFilter('all');
  };

  const handleStatusUpdate = async (id: string, newStatus: 'APPROVED' | 'REJECTED') => {
    try {
      await apiClient.patch(`/payment-requests/${id}/status`, null, {
        params: { status: newStatus }
      });
      await loadRequests(); 
    } catch (error: any) {
      const status = error.response?.status;
      const data = error.response?.data;
      const serverMessage = typeof data === 'object' ? (data?.message || data?.error) : data;
      const messageString = String(serverMessage || "");

      if (messageString.includes('StaleObjectStateException') || messageString.includes('OptimisticLocking')) {
        alert('Данные в базе обновились. Таблица будет перезагружена.');
        loadRequests();
      } else {
        alert(`Ошибка ${status || ''}: ${messageString || 'Непредвиденная ошибка на сервере'}`);
      }
    }
  };

  const formatNumber = (num: number) => {
    return num.toLocaleString('ru-RU', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  };

  const uniqueCategories = Array.from(new Set(allRequests.map(r => r.category).filter(Boolean)));

  const inputStyle = {
    width: '100%', padding: '12px 14px', border: '1px solid #d5d9de', borderRadius: '14px',
    fontSize: '14px', boxSizing: 'border-box' as const, height: '48px', background: 'white'
  };

  if (!isDirector) return null; 

  return (
    <div className="card">
      <div className="flex-between" style={{ marginBottom: '24px' }}>
        <h2 style={{ fontSize: '20px', fontWeight: '600' }}> Согласование платежей</h2>
      </div>

      <div style={{ marginBottom: '20px', padding: '20px', background: '#f6f7f8', borderRadius: '16px', border: '1px solid #e7e9ec' }}>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '12px', marginBottom: '12px' }}>
          <div style={{ flex: 2, minWidth: '200px' }}>
            <input type="text" placeholder=" Поиск..." value={searchTerm} onChange={(e) => setSearchTerm(e.target.value)} style={inputStyle} />
          </div>
          <div style={{ display: 'flex', gap: '12px', flex: 1.5 }}>
            <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} style={inputStyle}>
              <option value="all">Все статусы</option>
              <option value="PENDING">Ожидают</option>
              <option value="APPROVED">Одобрено</option>
              <option value="REJECTED">Отклонено</option>
              <option value="PAID">Оплачено</option>
            </select>
            <select value={categoryFilter} onChange={(e) => setCategoryFilter(e.target.value)} style={inputStyle}>
              <option value="all">Все категории</option>
              {uniqueCategories.map(cat => <option key={cat} value={cat}>{cat}</option>)}
            </select>
          </div>
          <button onClick={resetFilters} className="btn-secondary" style={{ height: '48px', padding: '0 20px' }}>Сбросить</button>
        </div>
      </div>

      <div className="mt-4">
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
                  <th>Действия</th>
                </tr>
              </thead>
              <tbody>
                {filteredRequests.map((req) => {
                  const isPending = req.status === 'PENDING';
                  const reqDate = new Date(req.date);
                  const today = new Date();
                  today.setHours(0, 0, 0, 0);
                  const isOverdue = isPending && reqDate < today;

                  return (
                    <tr key={req.id}>
                      <td>
                        <span style={isOverdue ? { color: '#a4473d', fontWeight: '700' } : {}}>{reqDate.toLocaleDateString('ru-RU')}</span>
                      </td>
                      <td><strong>{req.title}</strong></td>
                      <td className="amount-negative">{formatNumber(req.amount)}</td>
                      <td>{req.category || '—'}</td>
                      <td>{req.counterparty || '—'}</td>
                      <td>
                        <span className={req.status === 'PAID' ? 'badge-paid' : req.status === 'APPROVED' ? 'badge-income' : req.status === 'REJECTED' ? 'badge-expense' : (isOverdue ? 'badge-overdue' : 'badge-pending')}>
                          {req.status === 'PAID' ? 'Оплачено' : req.status === 'APPROVED' ? 'Одобрено' : req.status === 'REJECTED' ? 'Отклонено' : (isOverdue ? 'Просрочено' : 'Ожидает')}
                        </span>
                      </td>
                      <td style={{ textAlign: 'center' }}>
                        {isPending ? (
                          <div style={{ display: 'flex', gap: '8px', justifyContent: 'center' }}>
                            <button 
                              onClick={() => handleStatusUpdate(req.id, 'APPROVED')}
                              className="btn-primary"
                              style={{ height: '36px', fontSize: '12px', margin: 0 }}
                            >
                              Одобрить
                            </button>
                            <button 
                              onClick={() => handleStatusUpdate(req.id, 'REJECTED')}
                              className="btn-secondary"
                              style={{ height: '36px', fontSize: '12px', margin: 0 }}
                            >
                              Отклонить
                            </button>
                          </div>
                        ) : (
                          <span style={{ color: '#d5d9de', fontSize: '12px' }}>Обработано</span>
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
    </div>
  );
};

export default PaymentRequests;