import React, { useState, useEffect } from 'react';
import { useAuthStore } from '../store/authStore';
import apiClient from '../api/client';

interface Account {
  id: string;
  accountName: string;
}

interface ImportResult {
  totalRows: number;
  successRows: number;
  errorRows: number;
  errors: string[];
}

const ImportData: React.FC = () => {
  const { user } = useAuthStore();
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [selectedAccount, setSelectedAccount] = useState('');
  const [type, setType] = useState<'transactions' | 'payments'>('transactions');
  const [file, setFile] = useState<File | null>(null);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [companyName, setCompanyName] = useState<string>('');
  const [companyId, setCompanyId] = useState<string>('');

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
        setAccounts(accountsRes.data);
        if (accountsRes.data.length > 0) {
          setSelectedAccount(accountsRes.data[0].id);
        }
      }
    } catch (error) {
      console.error('Error loading company and accounts:', error);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!file) return;

    setLoading(true);
    const formData = new FormData();
    formData.append('file', file);

    if (type === 'transactions') {
      if (!selectedAccount) {
        alert('Выберите счёт');
        setLoading(false);
        return;
      }
      formData.append('accountId', selectedAccount);
    } else if (type === 'payments') {
      if (!companyId) {
        alert('Компания не найдена');
        setLoading(false);
        return;
      }
      formData.append('companyId', companyId);
    }

    try {
      const response = await apiClient.post(`/import/${type}`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      setResult(response.data);
    } catch (error) {
      console.error('Import error:', error);
      alert('Ошибка импорта');
    } finally {
      setLoading(false);
    }
  };

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
      <h2>Импорт данных</h2>

      <form onSubmit={handleSubmit}>
        <div className="form-grid">
          <div className="form-group">
            <label>Тип данных</label>
            <select
              value={type}
              onChange={(e) => {
                setType(e.target.value as 'transactions' | 'payments');
                setFile(null);
                setResult(null);
              }}
              style={inputStyle}
            >
              <option value="transactions">Транзакции (доходы/расходы)</option>
              <option value="payments">Платежи</option>
            </select>
          </div>

          {type === 'transactions' && (
            <>
              <div className="form-group">
                <label>Компания</label>
                <input
                  type="text"
                  value={companyName}
                  disabled
                  style={{
                    ...inputStyle,
                    background: '#f6f7f8',
                    color: '#101418',
                    fontWeight: '500'
                  }}
                />
              </div>

              <div className="form-group">
                <label>Счёт</label>
                <select
                  value={selectedAccount}
                  onChange={(e) => setSelectedAccount(e.target.value)}
                  required
                  style={inputStyle}
                >
                  <option value="">Выберите счёт</option>
                  {accounts.map(acc => (
                    <option key={acc.id} value={acc.id}>{acc.accountName}</option>
                  ))}
                </select>
              </div>
            </>
          )}

          {type === 'payments' && (
            <div className="form-group">
              <label>Компания</label>
              <input
                type="text"
                value={companyName}
                disabled
                style={{
                  ...inputStyle,
                  background: '#f6f7f8',
                  color: '#101418',
                  fontWeight: '500'
                }}
              />
            </div>
          )}

          <div className="form-group">
            <label>Файл (Excel/CSV)</label>
            <label
              htmlFor="import-file"
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '14px',
                minHeight: '48px',
                padding: '6px 12px',
                border: '1px dashed #b9a0a7',
                borderRadius: '14px',
                background: '#fcf8f9',
                cursor: 'pointer',
                boxSizing: 'border-box',
                width: '100%'
              }}
            >
              <input
                id="import-file"
                type="file"
                accept=".xlsx,.xls,.csv"
                onChange={(e) => setFile(e.target.files?.[0] || null)}
                required
                style={{
                  position: 'absolute',
                  width: '1px',
                  height: '1px',
                  padding: 0,
                  margin: '-1px',
                  overflow: 'hidden',
                  clip: 'rect(0, 0, 0, 0)',
                  whiteSpace: 'nowrap',
                  border: 0
                }}
              />
              <span
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  flexShrink: 0,
                  height: '36px',
                  padding: '0 16px',
                  borderRadius: '9px',
                  background: 'linear-gradient(135deg, #7b243c 0%, #55172b 100%)',
                  color: '#ffffff',
                  fontSize: '13px',
                  fontWeight: 600
                }}
              >
                Выбрать файл
              </span>
              <span
                style={{
                  minWidth: 0,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                  color: file ? '#3b434c' : '#7a838d',
                  fontSize: '13px',
                  fontWeight: file ? 500 : 400
                }}
              >
                {file ? file.name : 'Файл не выбран'}
              </span>
            </label>
            <span style={{ color: '#7a838d', fontSize: '12px' }}>
              Поддерживаются форматы XLSX, XLS и CSV
            </span>
          </div>
        </div>

        <button
          type="submit"
          disabled={loading || !file}
          className="btn-primary"
          style={{ height: '48px' }}
        >
          {loading ? 'Импорт...' : 'Импортировать'}
        </button>
      </form>

      {result && (
        <div className="mt-6" style={{
          background: '#f6f7f8',
          borderRadius: '12px',
          padding: '20px',
          border: '1px solid rgba(100, 150, 200, 0.2)'
        }}>
          <h3 style={{ color: '#101418', marginBottom: '16px', fontSize: '16px' }}>
            Результат импорта:
          </h3>
          <div style={{ display: 'flex', gap: '32px', marginBottom: '16px', flexWrap: 'wrap' }}>
            <div>
              <span style={{ color: '#7a838d', fontSize: '12px' }}>Всего строк</span>
              <div style={{ fontSize: '24px', fontWeight: '600', color: '#101418' }}>
                {result.totalRows}
              </div>
            </div>
            <div>
              <span style={{ color: '#7a838d', fontSize: '12px' }}>Успешно</span>
              <div style={{ fontSize: '24px', fontWeight: '600', color: '#1f7a5a' }}>
                {result.successRows}
              </div>
            </div>
            <div>
              <span style={{ color: '#7a838d', fontSize: '12px' }}>Ошибок</span>
              <div style={{ fontSize: '24px', fontWeight: '600', color: '#a4473d' }}>
                {result.errorRows}
              </div>
            </div>
          </div>

          {result.errors.length > 0 && (
            <div>
              <p style={{ color: '#a4473d', fontSize: '13px', marginBottom: '8px' }}>
                Ошибки:
              </p>
              <ul style={{ color: '#a4473d', fontSize: '12px', paddingLeft: '20px' }}>
                {result.errors.slice(0, 5).map((err, i) => (
                  <li key={i}>{err}</li>
                ))}
                {result.errors.length > 5 && (
                  <li>... и ещё {result.errors.length - 5} ошибок</li>
                )}
              </ul>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default ImportData;