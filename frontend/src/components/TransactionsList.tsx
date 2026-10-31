import React, { useEffect, useState } from 'react';
import { useAuthStore } from '../store/authStore';
import apiClient from '../api/client';

interface Transaction {
  id: string;
  date: string;
  transactionType: string;
  amount: number;
  category: string;
  description: string;
  accountId: string;
  accountName?: string;
  cfoId?: string;
  cfoName?: string;
}

interface Account {
  id: string;
  accountName: string;
}

interface CashFlowItem {
  id: string;
  name: string;
  type: string;
}

interface CFO {
  id: string;
  name: string;
  description: string;
}

const TransactionsList: React.FC = () => {
  const { user } = useAuthStore();
  const [allTransactions, setAllTransactions] = useState<Transaction[]>([]);
  const [filteredTransactions, setFilteredTransactions] = useState<Transaction[]>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [cashFlowItems, setCashFlowItems] = useState<CashFlowItem[]>([]);
  const [cfoList, setCfoList] = useState<CFO[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  
  
  const [searchTerm, setSearchTerm] = useState('');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [amountMin, setAmountMin] = useState('');
  const [amountMax, setAmountMax] = useState('');
  const [typeFilter, setTypeFilter] = useState('all');
  const [categoryFilter, setCategoryFilter] = useState('all');
  const [cfoFilter, setCfoFilter] = useState('all');
  
  const [formData, setFormData] = useState({
    accountId: '',
    type: 'INCOME',
    amount: '',
    category: '',
    description: '',
    date: new Date().toISOString().split('T')[0],
    cfoId: ''
  });

  const canEdit = ['ROLE_ADMIN', 'ROLE_FINANCIAL_MANAGER', 'ROLE_FINANCE_MANAGER', 'ADMIN', 'FINANCIAL_MANAGER', 'FINANCE_MANAGER'].includes(user?.role || '');

  useEffect(() => {
    const loadAll = async () => {
      await loadAccounts();
      await loadCashFlowItems();
      await loadCFO();
      await loadTransactions();
    };
    loadAll();
  }, []);

  useEffect(() => {
    applyFilters();
  }, [allTransactions, searchTerm, dateFrom, dateTo, amountMin, amountMax, typeFilter, categoryFilter, cfoFilter]);

  const loadAccounts = async () => {
    try {
      const companiesRes = await apiClient.get('/companies', {
        params: { ownerId: user?.id }
      });
      let allAccounts: Account[] = [];
      for (const company of companiesRes.data) {
        const accountsRes = await apiClient.get(`/accounts?companyId=${company.id}`);
        allAccounts = [...allAccounts, ...accountsRes.data];
      }
      setAccounts(allAccounts);
    } catch (error) {
      console.error('Error loading accounts:', error);
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

  const loadCFO = async () => {
    try {
      const response = await apiClient.get('/references/cfo');
      setCfoList(response.data);
    } catch (error) {
      console.error('Error loading CFO:', error);
    }
  };

  const loadTransactions = async () => {
    try {
      if (cfoList.length === 0) {
        await new Promise(resolve => setTimeout(resolve, 500));
        if (cfoList.length === 0) await loadCFO();
      }
      
      const companiesRes = await apiClient.get('/companies', {
        params: { ownerId: user?.id }
      });
      let allTransactions: Transaction[] = [];
      for (const company of companiesRes.data) {
        const accountsRes = await apiClient.get(`/accounts?companyId=${company.id}`);
        for (const account of accountsRes.data) {
          const transRes = await apiClient.get(`/transactions?accountId=${account.id}`);
          const accountTransactions = transRes.data.map((t: any) => ({
            ...t,
            accountName: account.accountName
          }));
          allTransactions = [...allTransactions, ...accountTransactions];
        }
      }
      
      for (const t of allTransactions) {
        if (t.cfoId) {
          const foundCfo = cfoList.find(cfo => cfo.id === t.cfoId);
          if (foundCfo) t.cfoName = foundCfo.name;
        }
      }
      
      allTransactions.sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
      setAllTransactions(allTransactions);
      setFilteredTransactions(allTransactions);
    } catch (error) {
      console.error('Error loading transactions:', error);
    } finally {
      setLoading(false);
    }
  };

  const applyFilters = () => {
    let filtered = [...allTransactions];
    if (searchTerm.trim()) {
      filtered = filtered.filter(t => 
        t.category?.toLowerCase().includes(searchTerm.toLowerCase()) ||
        t.description?.toLowerCase().includes(searchTerm.toLowerCase()) ||
        t.accountName?.toLowerCase().includes(searchTerm.toLowerCase()) ||
        t.cfoName?.toLowerCase().includes(searchTerm.toLowerCase())
      );
    }
    if (dateFrom) filtered = filtered.filter(t => t.date >= dateFrom);
    if (dateTo) filtered = filtered.filter(t => t.date <= dateTo);
    if (amountMin) filtered = filtered.filter(t => t.amount >= parseFloat(amountMin));
    if (amountMax) filtered = filtered.filter(t => t.amount <= parseFloat(amountMax));
    if (typeFilter !== 'all') filtered = filtered.filter(t => t.transactionType === typeFilter);
    if (categoryFilter !== 'all') filtered = filtered.filter(t => t.category === categoryFilter);
    if (cfoFilter !== 'all') filtered = filtered.filter(t => t.cfoId === cfoFilter);
    setFilteredTransactions(filtered);
  };

  const resetFilters = () => {
    setSearchTerm(''); setDateFrom(''); setDateTo(''); setAmountMin(''); setAmountMax('');
    setTypeFilter('all'); setCategoryFilter('all'); setCfoFilter('all');
    setFilteredTransactions(allTransactions);
  };

  const createTransaction = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await apiClient.post('/transactions', {
        accountId: formData.accountId,
        type: formData.type,
        amount: parseFloat(formData.amount),
        category: formData.category,
        description: formData.description,
        date: formData.date,
        cfoId: formData.cfoId || null
      });
      setShowForm(false);
      setFormData({ accountId: '', type: 'INCOME', amount: '', category: '', description: '', date: new Date().toISOString().split('T')[0], cfoId: '' });
      loadTransactions();
      loadAccounts();
    } catch (error) {
      console.error('Error creating transaction:', error);
    }
  };

  const formatNumber = (num: number) => {
    return num.toLocaleString('ru-RU', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  };

  const uniqueCategories = Array.from(new Set(allTransactions.map(t => t.category).filter(Boolean)));
  const incomeItems = cashFlowItems.filter(item => item.type === 'INCOME');
  const expenseItems = cashFlowItems.filter(item => item.type === 'EXPENSE');

  const inputStyle = {
    width: '100%', padding: '12px 14px', border: '1px solid #d4e2ef', borderRadius: '14px',
    fontSize: '14px', boxSizing: 'border-box' as const, height: '48px'
  };

  return (
    <div className="card">
      {canEdit && (
        <>
          <div className="flex-between" style={{ marginBottom: '16px' }}>
            <h2> Новая транзакция</h2>
            <button onClick={() => setShowForm(!showForm)} className="btn-secondary">
              {showForm ? 'Отмена' : 'Показать форму'}
            </button>
          </div>
          {showForm && (
            <form onSubmit={createTransaction} className="mb-6">
              <div className="form-grid">
                <div className="form-group">
                  <label>Счет</label>
                  <select value={formData.accountId} onChange={(e) => setFormData({ ...formData, accountId: e.target.value })} required style={inputStyle}>
                    <option value="">Выберите счет</option>
                    {accounts.map(acc => <option key={acc.id} value={acc.id}>{acc.accountName}</option>)}
                  </select>
                </div>
                <div className="form-group">
                  <label>Тип</label>
                  <select value={formData.type} onChange={(e) => setFormData({ ...formData, type: e.target.value, category: '' })} style={inputStyle}>
                    <option value="INCOME"> Доход</option>
                    <option value="EXPENSE"> Расход</option>
                  </select>
                </div>
                <div className="form-group">
                  <label>Сумма</label>
                  <input type="number" step="0.01" placeholder="1000.00" value={formData.amount} onChange={(e) => setFormData({ ...formData, amount: e.target.value })} required style={inputStyle} />
                </div>
                <div className="form-group">
                  <label>Категория</label>
                  <select value={formData.category} onChange={(e) => setFormData({ ...formData, category: e.target.value })} required style={inputStyle}>
                    <option value="">Выберите категорию</option>
                    {(formData.type === 'INCOME' ? incomeItems : expenseItems).map(item => <option key={item.id} value={item.name}>{item.name}</option>)}
                  </select>
                </div>
                <div className="form-group">
                  <label>ЦФО</label>
                  <select value={formData.cfoId} onChange={(e) => setFormData({ ...formData, cfoId: e.target.value })} style={inputStyle}>
                    <option value="">Выберите ЦФО</option>
                    {cfoList.map(cfo => <option key={cfo.id} value={cfo.id}>{cfo.name}</option>)}
                  </select>
                </div>
                <div className="form-group">
                  <label>Описание</label>
                  <input type="text" placeholder="Описание операции" value={formData.description} onChange={(e) => setFormData({ ...formData, description: e.target.value })} style={inputStyle} />
                </div>
                <div className="form-group">
                  <label>Дата</label>
                  <input type="date" value={formData.date} onChange={(e) => setFormData({ ...formData, date: e.target.value })} style={inputStyle} />
                </div>
              </div>
              <button type="submit" className="btn-primary" style={{ height: '48px' }}> Добавить транзакцию</button>
            </form>
          )}
        </>
      )}

      
      <div style={{ marginBottom: '20px', padding: '20px', background: '#f8fbfe', borderRadius: '16px', border: '1px solid #e2edf7', marginTop: '20px' }}>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '12px', marginBottom: '12px' }}>
          <div style={{ flex: 2, minWidth: '200px' }}>
            <input type="text" placeholder=" Поиск..." value={searchTerm} onChange={(e) => setSearchTerm(e.target.value)} style={{ ...inputStyle, width: '100%' }} />
          </div>
          <div style={{ display: 'flex', gap: '12px' }}>
            <input type="date" value={dateFrom} onChange={(e) => setDateFrom(e.target.value)} style={{ ...inputStyle, width: '140px' }} />
            <input type="date" value={dateTo} onChange={(e) => setDateTo(e.target.value)} style={{ ...inputStyle, width: '140px' }} />
          </div>
        </div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '12px', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
            <select value={typeFilter} onChange={(e) => setTypeFilter(e.target.value)} style={{ ...inputStyle, width: '130px', background: 'white' }}>
              <option value="all">Все типы</option>
              <option value="INCOME">Доходы</option>
              <option value="EXPENSE">Расходы</option>
            </select>
            <select value={categoryFilter} onChange={(e) => setCategoryFilter(e.target.value)} style={{ ...inputStyle, width: '150px', background: 'white' }}>
              <option value="all">Все категории</option>
              {uniqueCategories.map(cat => <option key={cat} value={cat}>{cat}</option>)}
            </select>
            <div style={{ display: 'flex', gap: '8px' }}>
              <input type="number" placeholder="От" value={amountMin} onChange={(e) => setAmountMin(e.target.value)} style={{ ...inputStyle, width: '100px' }} />
              <input type="number" placeholder="До" value={amountMax} onChange={(e) => setAmountMax(e.target.value)} style={{ ...inputStyle, width: '100px' }} />
            </div>
          </div>
          <button onClick={resetFilters} className="btn-secondary" style={{ padding: '0 20px', height: '48px' }}>Сбросить</button>
        </div>
      </div>

      <div className="mt-4">
        <h2> История операций</h2>
        {loading ? <div className="loading">Загрузка...</div> : (
          <div className="table-wrapper">
            <table>
              <thead>
                <tr>
                  <th>Дата</th>
                  <th>Счет</th>
                  <th>Тип</th>
                  <th>Категория</th>
                  <th>ЦФО</th>
                  <th>Сумма</th>
                  <th>Описание</th>
                </tr>
              </thead>
              <tbody>
                {filteredTransactions.map((t) => {
                  const isIncome = t.transactionType === 'INCOME';
                  return (
                    <tr key={t.id}>
                      <td>{new Date(t.date).toLocaleDateString('ru-RU')}</td>
                      <td>{t.accountName || '—'}</td>
                      <td><span className={isIncome ? 'badge-income' : 'badge-expense'}>{isIncome ? 'Доход' : 'Расход'}</span></td>
                      <td>{t.category || '—'}</td>
                      <td>{t.cfoName || '—'}</td>
                      <td 
                        className={isIncome ? 'amount-positive' : 'amount-negative'} 
                        style={{ whiteSpace: 'nowrap' }}
                      >
                        {isIncome ? '+\u00A0' : '-\u00A0'}{formatNumber(t.amount)}
                      </td>
                      <td>{t.description || '—'}</td>
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

export default TransactionsList;