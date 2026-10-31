import React, { useEffect, useState } from 'react';
import { useAuthStore } from '../store/authStore';
import apiClient from '../api/client';

interface Account {
  id: string;
  accountName: string;
  accountType: string;
  currency: string;
  balance: number;
  companyName?: string;
}

const AccountsList: React.FC = () => {
  const { user } = useAuthStore();
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [companyName, setCompanyName] = useState<string>('');
  const [companyId, setCompanyId] = useState<string>('');
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState({ accountName: '', accountType: 'BANK', currency: 'BYN' });

  const canEdit = ['ROLE_ADMIN', 'ROLE_FINANCIAL_MANAGER', 'ROLE_FINANCE_MANAGER', 'ADMIN', 'FINANCIAL_MANAGER', 'FINANCE_MANAGER'].includes(user?.role || '');

  
  const commonInputStyle = {
    height: '48px',
    width: '100%',
    padding: '12px 14px',
    border: '1px solid #d4e2ef',
    borderRadius: '12px',
    fontSize: '14px',
    boxSizing: 'border-box' as const, // Критично для одинаковой высоты
    background: 'white',
    color: '#333'
  };

  useEffect(() => {
    loadCompanyAndAccounts();
  }, []);

  const loadCompanyAndAccounts = async () => {
    try {
      const companiesRes = await apiClient.get('/companies', {
        params: { ownerId: user?.id }
      });
      
      if (companiesRes.data.length > 0) {
        const firstCompany = companiesRes.data[0];
        setCompanyName(firstCompany.name);
        setCompanyId(firstCompany.id);
        
        const accountsRes = await apiClient.get(`/accounts?companyId=${firstCompany.id}`);
        const accountsWithCompany = accountsRes.data.map((acc: any) => ({
          ...acc,
          companyName: firstCompany.name
        }));
        setAccounts(accountsWithCompany);
      }
    } catch (error) {
      console.error('Error loading accounts:', error);
    } finally {
      setLoading(false);
    }
  };

  const createAccount = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!companyId) return;
    
    try {
      await apiClient.post(`/accounts?companyId=${companyId}`, {
        accountName: formData.accountName,
        accountType: formData.accountType,
        currency: formData.currency
      });
      setShowForm(false);
      setFormData({ accountName: '', accountType: 'BANK', currency: 'BYN' });
      loadCompanyAndAccounts();
    } catch (error) {
      console.error('Error creating account:', error);
    }
  };

  const deleteAccount = async (id: string) => {
    if (!confirm('Удалить счёт?')) return;
    try {
      await apiClient.delete(`/accounts/${id}`);
      loadCompanyAndAccounts();
    } catch (error) {
      console.error('Error deleting account:', error);
    }
  };

  const formatNumber = (num: number) => {
    return num.toLocaleString('ru-RU', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  };

  return (
    <div className="card">
      {canEdit && (
        <>
          <div className="flex-between" style={{ marginBottom: '16px' }}>
            <h2> Новый счет</h2>
            <button onClick={() => setShowForm(!showForm)} className="btn-secondary">
              {showForm ? 'Отмена' : 'Показать форму'}
            </button>
          </div>
          
          {showForm && (
            <form onSubmit={createAccount} style={{ marginBottom: '32px' }}>
              <div className="form-grid">
                <div className="form-group">
                  <label>Компания</label>
                  <input
                    type="text"
                    value={companyName}
                    disabled
                    style={{ 
                        ...commonInputStyle, 
                        background: '#f5f7fa', 
                        color: '#1c2e43', 
                        fontWeight: '600',
                        border: '1px dashed #cbd5e0'
                    }}
                  />
                </div>
                <div className="form-group">
                  <label>Название счета</label>
                  <input 
                    type="text" 
                    placeholder="Напр. Основной расчетный" 
                    value={formData.accountName} 
                    onChange={(e) => setFormData({ ...formData, accountName: e.target.value })} 
                    required 
                    style={commonInputStyle}
                  />
                </div>
                <div className="form-group">
                  <label>Тип счета</label>
                  <select 
                    value={formData.accountType} 
                    onChange={(e) => setFormData({ ...formData, accountType: e.target.value })}
                    style={commonInputStyle}
                  >
                    <option value="BANK"> Банковский</option>
                    <option value="CASH"> Наличные</option>
                    <option value="CARD"> Карта</option>
                  </select>
                </div>
                <div className="form-group">
                  <label>Валюта</label>
                  <select 
                    value={formData.currency} 
                    onChange={(e) => setFormData({ ...formData, currency: e.target.value })}
                    style={commonInputStyle}
                  >
                    <option value="BYN">BYN</option>
                    <option value="USD">USD</option>
                    <option value="EUR">EUR</option>
                  </select>
                </div>
              </div>
              <button type="submit" className="btn-primary" style={{ height: '48px', padding: '0 30px' }}>
                 Создать счет
              </button>
            </form>
          )}
        </>
      )}
      
      <div className="mt-4">
        <h2> Счета компании</h2>
        {loading ? (
          <div className="loading">Загрузка...</div>
        ) : accounts.length === 0 ? (
          <div className="text-center py-8 text-gray-500"> Нет счетов</div>
        ) : (
          <div className="table-wrapper">
            <table>
              <thead>
                <tr>
                  <th>Название</th>
                  <th>Тип</th>
                  <th>Валюта</th>
                  <th>Баланс</th>
                  {canEdit && <th>Действие</th>}
                </tr>
              </thead>
              <tbody>
                {accounts.map((acc) => (
                  <tr key={acc.id}>
                    <td><strong>{acc.accountName}</strong></td>
                    <td>{acc.accountType === 'BANK' ? 'Банк' : acc.accountType === 'CASH' ? 'Наличные' : 'Карта'}</td>
                    <td>{acc.currency}</td>
                    <td className="amount-positive" style={{ fontWeight: '600' }}>
                        {formatNumber(acc.balance)} {acc.currency}
                    </td>
                    {canEdit && (
                      <td>
                        <button onClick={() => deleteAccount(acc.id)} className="btn-danger" style={{ padding: '6px 12px' }}>
                            Удалить
                        </button>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};

export default AccountsList;