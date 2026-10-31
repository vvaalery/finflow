import React, { useState, useEffect } from 'react';
import apiClient from '../api/client';

interface CFO {
  id: string;
  name: string;
  description: string;
}

interface CashFlowItem {
  id: string;
  name: string;
  type: string;
  description: string;
}

interface Counterparty {
  id: string;
  name: string;
  taxId: string;
  address: string;
  phone: string;
  email: string;
}

const References: React.FC = () => {
  const [activeSection, setActiveSection] = useState<'cfo' | 'cashflow' | 'counterparties'>('cfo');
  const [cfoList, setCfoList] = useState<CFO[]>([]);
  const [cashFlowList, setCashFlowList] = useState<CashFlowItem[]>([]);
  const [counterpartiesList, setCounterpartiesList] = useState<Counterparty[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editingItem, setEditingItem] = useState<any>(null);
  const [formData, setFormData] = useState<any>({});

  useEffect(() => {
    loadData();
  }, [activeSection]);

  const getBaseUrl = () => {
    switch (activeSection) {
      case 'cfo': return '/references/cfo';
      case 'cashflow': return '/references/cash-flow-items';
      case 'counterparties': return '/references/counterparties';
      default: return '';
    }
  };

  const loadData = async () => {
    setLoading(true);
    try {
      const response = await apiClient.get(getBaseUrl());
      if (activeSection === 'cfo') setCfoList(response.data);
      if (activeSection === 'cashflow') setCashFlowList(response.data);
      if (activeSection === 'counterparties') setCounterpartiesList(response.data);
    } catch (error) {
      console.error('Error loading references:', error);
    } finally {
      setLoading(false);
    }
  };

  const validateForm = () => {
    if (activeSection === 'counterparties') {
      const taxIdRegex = /^\d{9}$/;
      if (formData.taxId && !taxIdRegex.test(formData.taxId)) {
        alert('Ошибка: УНП должен содержать ровно 9 цифр.');
        return false;
      }

      const phoneRegex = /^\+?[\d\s-]{7,15}$/;
      if (formData.phone && !phoneRegex.test(formData.phone)) {
        alert('Ошибка: Введите корректный номер телефона (минимум 7 цифр).');
        return false;
      }
    }
    return true;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!validateForm()) return;

    const finalData = activeSection === 'cashflow' 
      ? { type: 'INCOME', ...formData } 
      : formData;

    try {
      const url = getBaseUrl();
      if (editingItem) {
        await apiClient.put(`${url}/${editingItem.id}`, finalData);
      } else {
        await apiClient.post(url, finalData);
      }
      
      setShowForm(false);
      setEditingItem(null);
      setFormData({});
      loadData();
    } catch (error) {
      console.error('Error saving reference:', error);
      alert('Ошибка при сохранении данных. Проверьте соединение с сервером.');
    }
  };

  const handleEdit = (item: any) => {
    setEditingItem(item);
    setFormData(item);
    setShowForm(true);
  };

  const handleDelete = async (id: string) => {
    if (!confirm('Вы уверены, что хотите удалить этот элемент?')) return;
    try {
      await apiClient.delete(`${getBaseUrl()}/${id}`);
      loadData();
    } catch (error) {
      console.error('Error deleting reference:', error);
      alert('Ошибка при удалении.');
    }
  };

  const inputStyle = {
    width: '100%',
    padding: '12px 14px',
    border: '1px solid #d4e2ef',
    borderRadius: '14px',
    fontSize: '14px',
    boxSizing: 'border-box' as const,
    marginBottom: '10px'
  };

  const renderFormFields = () => {
    if (activeSection === 'cfo') {
      return (
        <div className="form-grid">
          <div className="form-group">
            <label>Название ЦФО</label>
            <input
              type="text"
              style={inputStyle}
              value={formData.name || ''}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              required
            />
          </div>
          <div className="form-group">
            <label>Описание</label>
            <textarea
              style={{ ...inputStyle, height: '80px' }}
              value={formData.description || ''}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
            />
          </div>
        </div>
      );
    }

    if (activeSection === 'cashflow') {
      return (
        <div className="form-grid">
          <div className="form-group">
            <label>Название статьи</label>
            <input
              type="text"
              style={inputStyle}
              value={formData.name || ''}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              required
            />
          </div>
          <div className="form-group">
            <label>Тип операции</label>
            <select
              style={inputStyle}
              value={formData.type || 'INCOME'}
              onChange={(e) => setFormData({ ...formData, type: e.target.value })}
            >
              <option value="INCOME"> Доход</option>
              <option value="EXPENSE"> Расход</option>
            </select>
          </div>
          <div className="form-group">
            <label>Описание</label>
            <textarea
              style={{ ...inputStyle, height: '80px' }}
              value={formData.description || ''}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
            />
          </div>
        </div>
      );
    }

    if (activeSection === 'counterparties') {
      return (
        <div className="form-grid">
          <div className="form-group">
            <label>Название компании / ФИО</label>
            <input
              type="text"
              style={inputStyle}
              value={formData.name || ''}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              required
            />
          </div>
          <div className="form-group">
            <label>УНП (9 цифр)</label>
            <input
              type="text"
              style={inputStyle}
              placeholder="Например: 123456789"
              value={formData.taxId || ''}
              onChange={(e) => setFormData({ ...formData, taxId: e.target.value })}
            />
          </div>
          <div className="form-group">
            <label>Телефон</label>
            <input
              type="text"
              style={inputStyle}
              placeholder="+375 (__) ___-__-__"
              value={formData.phone || ''}
              onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
            />
          </div>
          <div className="form-group">
            <label>Адрес</label>
            <input
              type="text"
              style={inputStyle}
              value={formData.address || ''}
              onChange={(e) => setFormData({ ...formData, address: e.target.value })}
            />
          </div>
          <div className="form-group">
            <label>Email</label>
            <input
              type="email"
              style={inputStyle}
              value={formData.email || ''}
              onChange={(e) => setFormData({ ...formData, email: e.target.value })}
            />
          </div>
        </div>
      );
    }
  };

  const renderTable = () => {
    return (
      <div className="table-wrapper">
        <table>
          <thead>
            {activeSection === 'cfo' && <tr><th>Название</th><th>Описание</th><th style={{ width: '200px' }}>Действия</th></tr>}
            {activeSection === 'cashflow' && <tr><th>Название</th><th>Тип</th><th>Описание</th><th style={{ width: '200px' }}>Действия</th></tr>}
            {activeSection === 'counterparties' && <tr><th>Название</th><th>УНП</th><th>Телефон</th><th>Email</th><th style={{ width: '200px' }}>Действия</th></tr>}
          </thead>
          <tbody>
            {activeSection === 'cfo' && cfoList.map(item => (
              <tr key={item.id}>
                <td><strong>{item.name}</strong></td>
                <td>{item.description || '—'}</td>
                <td>
                  <button onClick={() => handleEdit(item)} className="btn-secondary" style={{ marginRight: '8px' }}>Изменить</button>
                  <button onClick={() => handleDelete(item.id)} className="btn-danger">Удалить</button>
                </td>
              </tr>
            ))}
            {activeSection === 'cashflow' && cashFlowList.map(item => (
              <tr key={item.id}>
                <td><strong>{item.name}</strong></td>
                <td>
                  <span className={item.type === 'INCOME' ? 'badge-income' : 'badge-expense'}>
                    {item.type === 'INCOME' ? 'Доход' : 'Расход'}
                  </span>
                </td>
                <td>{item.description || '—'}</td>
                <td>
                  <button onClick={() => handleEdit(item)} className="btn-secondary" style={{ marginRight: '8px' }}>Изменить</button>
                  <button onClick={() => handleDelete(item.id)} className="btn-danger">Удалить</button>
                </td>
              </tr>
            ))}
            {activeSection === 'counterparties' && counterpartiesList.map(item => (
              <tr key={item.id}>
                <td><strong>{item.name}</strong></td>
                <td>{item.taxId || '—'}</td>
                <td>{item.phone || '—'}</td>
                <td>{item.email || '—'}</td>
                <td>
                  <button onClick={() => handleEdit(item)} className="btn-secondary" style={{ marginRight: '8px' }}>Изменить</button>
                  <button onClick={() => handleDelete(item.id)} className="btn-danger">Удалить</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  };

  return (
    <div className="card">
      <h2> Справочники</h2>
      
      <div style={{ display: 'flex', gap: '10px', marginBottom: '25px', borderBottom: '1px solid #e2edf7', paddingBottom: '15px' }}>
        <button
          onClick={() => setActiveSection('cfo')}
          className={activeSection === 'cfo' ? 'btn-primary' : 'btn-secondary'}
          style={activeSection === 'cfo' ? { background: '#1d796c' } : {}}
        >
           ЦФО
        </button>
        <button
          onClick={() => setActiveSection('cashflow')}
          className={activeSection === 'cashflow' ? 'btn-primary' : 'btn-secondary'}
          style={activeSection === 'cashflow' ? { background: '#1d796c' } : {}}
        >
           Статьи ДДС
        </button>
        <button
          onClick={() => setActiveSection('counterparties')}
          className={activeSection === 'counterparties' ? 'btn-primary' : 'btn-secondary'}
          style={activeSection === 'counterparties' ? { background: '#1d796c' } : {}}
        >
           Контрагенты
        </button>
      </div>
      
      <div style={{ marginBottom: '20px' }}>
        <button
          onClick={() => { setShowForm(!showForm); setEditingItem(null); setFormData({}); }}
          className="btn-secondary"
        >
          {showForm ? ' Отмена' : '+ Добавить запись'}
        </button>
      </div>
      
      {showForm && (
        <form 
          onSubmit={handleSubmit} 
          style={{ 
            marginBottom: '30px', 
            padding: '25px', 
            background: '#f8fbfe', 
            borderRadius: '20px', 
            border: '1px solid rgba(90, 155, 192, 0.2)' 
          }}
        >
          <h3 style={{ marginTop: 0, marginBottom: '20px', color: '#1c2e43' }}>
            {editingItem ? ' Редактирование' : ' Новая запись'}
          </h3>
          {renderFormFields()}
          <div style={{ marginTop: '20px', display: 'flex', gap: '10px' }}>
            <button type="submit" className="btn-primary">
              {editingItem ? 'Обновить данные' : 'Добавить в справочник'}
            </button>
            <button 
              type="button" 
              onClick={() => { setShowForm(false); setEditingItem(null); }} 
              className="btn-secondary"
            >
              Отмена
            </button>
          </div>
        </form>
      )}
      
      {loading ? (
        <div className="loading" style={{ textAlign: 'center', padding: '40px', color: '#8792a2' }}>
          ⏳ Загрузка справочника...
        </div>
      ) : (
        renderTable()
      )}
    </div>
  );
};

export default References;