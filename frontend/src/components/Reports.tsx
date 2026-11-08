import React, { useState, useEffect } from 'react';
import { useAuthStore } from '../store/authStore';
import apiClient from '../api/client';

interface Account {
  id: string;
  accountName: string;
}

const Reports: React.FC = () => {
  const { user } = useAuthStore();
  const [companyId, setCompanyId] = useState<string>('');
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [selectedAccount, setSelectedAccount] = useState('');
  const [forecastMethod, setForecastMethod] = useState('gradientBoosting');
  const [forecastMonths, setForecastMonths] = useState(6);
  const [loading, setLoading] = useState(false);
  const [exportType, setExportType] = useState<'transactions' | 'payments' | 'forecast'>('transactions');
  const [message, setMessage] = useState('');

  const inputStyle = {
    width: '100%',
    padding: '12px 14px',
    border: '1px solid #d5d9de',
    borderRadius: '14px',
    fontSize: '14px',
    boxSizing: 'border-box' as const,
    height: '48px',
    background: 'white'
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
        setCompanyId(firstCompany.id);
        
        const accountsRes = await apiClient.get(`/accounts?companyId=${firstCompany.id}`);
        setAccounts(accountsRes.data);
        if (accountsRes.data.length > 0) {
          setSelectedAccount(accountsRes.data[0].id);
        }
      }
    } catch (error) {
      console.error('Error loading company and accounts:', error);
    }
  };

  const exportData = async () => {
    let url = '';
    
    if (exportType === 'transactions') {
      if (!selectedAccount) {
        setMessage(' Выберите счёт');
        setTimeout(() => setMessage(''), 3000);
        return;
      }
      url = `/export/transactions?accountId=${selectedAccount}`;
    } else if (exportType === 'payments') {
      if (!companyId) {
        setMessage(' Компания не найдена');
        setTimeout(() => setMessage(''), 3000);
        return;
      }
      url = `/export/payments?companyId=${companyId}`;
    } else {
      if (!companyId) {
        setMessage(' Компания не найдена');
        setTimeout(() => setMessage(''), 3000);
        return;
      }
      url = `/export/forecast?companyId=${companyId}&months=${forecastMonths}&strategy=${forecastMethod}`;
    }
    
    setLoading(true);
    try {
      const response = await apiClient.get(url, { responseType: 'blob' });
      
      const blob = new Blob([response.data], { 
        type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' 
      });
      
      const link = document.createElement('a');
      const urlBlob = window.URL.createObjectURL(blob);
      link.href = urlBlob;
      const date = new Date().toISOString().slice(0, 19).replace(/:/g, '-');
      const periodText = exportType === 'forecast' ? `_${forecastMonths}m` : '';
      
      link.download = `${exportType}${periodText}_${date}.xlsx`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(urlBlob);
      
      setMessage(` Отчёт успешно скачан в формате Excel`);
      setTimeout(() => setMessage(''), 3000);
    } catch (error) {
      console.error('Export error:', error);
      setMessage(' Ошибка экспорта');
      setTimeout(() => setMessage(''), 3000);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="card">
      <h2> Отчеты и экспорт</h2>
      
      {message && (
        <div style={{ 
          padding: '12px 16px', 
          borderRadius: '12px', 
          marginBottom: '20px', 
          background: message.includes('успешно') ? '#e9f3ee' : '#faf0ee', 
          color: message.includes('успешно') ? '#1f7a5a' : '#a4473d',
          fontSize: '14px'
        }}>
          {message}
        </div>
      )}
      
      <div style={{ display: 'flex', gap: '12px', marginBottom: '24px', flexWrap: 'wrap' }}>
        <button
          onClick={() => setExportType('transactions')}
          className={exportType === 'transactions' ? 'btn-primary' : 'btn-secondary'}
          style={exportType === 'transactions' ? { background: 'linear-gradient(135deg, #1f7a5a 0%, #14513f 100%)', height: '44px' } : { height: '44px' }}
        >
           Транзакции
        </button>
        <button
          onClick={() => setExportType('payments')}
          className={exportType === 'payments' ? 'btn-primary' : 'btn-secondary'}
          style={exportType === 'payments' ? { background: 'linear-gradient(135deg, #1f7a5a 0%, #14513f 100%)', height: '44px' } : { height: '44px' }}
        >
           Платежи
        </button>
        <button
          onClick={() => setExportType('forecast')}
          className={exportType === 'forecast' ? 'btn-primary' : 'btn-secondary'}
          style={exportType === 'forecast' ? { background: 'linear-gradient(135deg, #1f7a5a 0%, #14513f 100%)', height: '44px' } : { height: '44px' }}
        >
           Прогноз
        </button>
      </div>
      
      <div style={{ marginBottom: '24px' }}>
        {exportType === 'transactions' && (
          <div className="form-group" style={{ maxWidth: '400px' }}>
            <label>Счёт</label>
            <select 
              value={selectedAccount} 
              onChange={(e) => setSelectedAccount(e.target.value)}
              style={inputStyle}
            >
              <option value="">Выберите счёт</option>
              {accounts.map(acc => (
                <option key={acc.id} value={acc.id}>{acc.accountName}</option>
              ))}
            </select>
          </div>
        )}
        
        {exportType === 'forecast' && (
          <div style={{ display: 'flex', gap: '20px', flexWrap: 'wrap' }}>
            <div className="form-group" style={{ flex: 1, minWidth: '180px' }}>
              <label>Период</label>
              <div style={{ display: 'flex', gap: '8px' }}>
                {[3, 6, 12].map(months => (
                  <button
                    key={months}
                    onClick={() => setForecastMonths(months)}
                    className={forecastMonths === months ? 'btn-primary' : 'btn-secondary'}
                    style={{ 
                      flex: 1,
                      padding: '12px 0',
                      height: '48px',
                      background: forecastMonths === months ? 'linear-gradient(135deg, #1f7a5a 0%, #14513f 100%)' : undefined
                    }}
                  >
                    {months} мес
                  </button>
                ))}
              </div>
            </div>
            <div className="form-group" style={{ flex: 1, minWidth: '200px' }}>
              <label>Метод</label>
              <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                {[
                  { value: 'gradientBoosting', label: 'Ансамблевый бустинг' },
                  { value: 'linearRegression', label: 'Линейная' },
                  { value: 'movingAverage', label: 'Средняя' },
                ].map(method => <button
                  key={method.value}
                  onClick={() => setForecastMethod(method.value)}
                  className={forecastMethod === method.value ? 'btn-primary' : 'btn-secondary'}
                  style={{ flex: '1 1 110px', padding: '12px 8px', minHeight: '48px' }}
                >{method.label}</button>)}
              </div>
            </div>
          </div>
        )}
      </div>
      
      <button 
        onClick={exportData} 
        className="btn-primary" 
        disabled={loading}
        style={{ minWidth: '220px', height: '48px', fontSize: '15px' }}
      >
        {loading ? 'Формирование...' : ' Экспортировать в Excel'}
      </button>
    </div>
  );
};

export default Reports;