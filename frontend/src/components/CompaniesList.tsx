import React, { useEffect, useState } from 'react';
import { useAuthStore } from '../store/authStore';
import apiClient from '../api/client';

interface Company {
  id: string;
  name: string;
  taxId: string;
  address: string;
  phone: string;
  description?: string;
  logoUrl?: string;
  status?: string;
}

const CompaniesList: React.FC = () => {
  const { user } = useAuthStore();
  const [companies, setCompanies] = useState<Company[]>([]);
  const [loading, setLoading] = useState(true);
  const [editingCompany, setEditingCompany] = useState<Company | null>(null);
  const [editFormData, setEditFormData] = useState({ 
    name: '', taxId: '', address: '', phone: '', description: '', logoUrl: '' 
  });

  const canEdit = ['ROLE_ADMIN', 'ROLE_FINANCIAL_MANAGER', 'ROLE_FINANCE_MANAGER', 'ADMIN', 'FINANCIAL_MANAGER', 'FINANCE_MANAGER'].includes(user?.role || '');
  
  const tags = ['UI/UX Design', 'Branding', 'Mobile Apps', 'Web Design', 'SaaS Systems', 'Prototyping'];

  useEffect(() => {
    loadCompanies();
  }, []);

  const loadCompanies = async () => {
    try {
      const response = await apiClient.get('/companies', {
        params: { ownerId: user?.id }
      });
      setCompanies(response.data);
    } catch (error) {
      console.error('Error loading companies:', error);
    } finally {
      setLoading(false);
    }
  };

  const startEdit = (company: Company) => {
    setEditingCompany(company);
    setEditFormData({
      name: company.name,
      taxId: company.taxId || '',
      address: company.address || '',
      phone: company.phone || '',
      description: company.description || '',
      logoUrl: company.logoUrl || ''
    });
  };

  const updateCompany = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingCompany) return;
    try {
      await apiClient.put(`/companies/${editingCompany.id}`, editFormData);
      setEditingCompany(null);
      loadCompanies();
    } catch (error) {
      console.error('Error updating company:', error);
    }
  };

  if (loading) return <div className="loading">Загрузка данных студии...</div>;

  return (
    <div className="company-container" style={{ padding: '20px', minHeight: '100vh', background: '#f6f7f8' }}>
      {companies.map((company) => (
        <div key={company.id} className="card" style={{ 
          maxWidth: '1000px', 
          margin: '0 auto', 
          padding: '40px',
          borderRadius: '14px',
          boxShadow: '0 10px 30px rgba(0,0,0,0.05)',
          background: 'white'
        }}>
          
          {/* Header Section */}
          <div style={{ 
            display: 'flex', 
            justifyContent: 'space-between', 
            alignItems: 'flex-start', 
            marginBottom: '40px' 
          }}>
            <div style={{ display: 'flex', gap: '24px', alignItems: 'center' }}>
              <div style={{ 
                width: '100px', 
                height: '100px', 
                borderRadius: '14px', 
                background: 'linear-gradient(135deg, #1f7a5a 0%, #2c8b7e 100%)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                overflow: 'hidden',
                boxShadow: '0 8px 20px rgba(99, 102, 241, 0.3)'
              }}>
                {company.logoUrl ? (
                  <img src={company.logoUrl} alt="Logo" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                ) : (
                  <span style={{ fontSize: '40px', color: 'white', fontWeight: 'bold' }}>{company.name.charAt(0)}</span>
                )}
              </div>
              <div>
                <h1 style={{ margin: '0 0 8px 0', fontSize: '32px', color: '#101418', fontWeight: '800' }}>{company.name}</h1>
                <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                  <span style={statusBadgeStyle}>● {company.status || 'ACTIVE'}</span>
                  <span style={{ color: '#7a838d', fontSize: '14px' }}>IT & Digital Design</span>
                </div>
              </div>
            </div>
            {canEdit && (
              <button onClick={() => startEdit(company)} className="btn-secondary" style={{ borderRadius: '12px', padding: '10px 20px' }}>
                 Настроить профиль
              </button>
            )}
          </div>

          {/* Main Layout */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 320px', gap: '40px' }}>
            
            <div className="left-col">
              <section style={{ marginBottom: '32px' }}>
                <h3 style={sectionTitleStyle}>О компании</h3>
                <p style={{ lineHeight: '1.8', color: '#3b434c', fontSize: '16px', margin: 0 }}>
                  {company.description || 'Описание в процессе наполнения...'}
                </p>
              </section>

              <section style={{ marginBottom: '32px' }}>
                <h3 style={sectionTitleStyle}>Компетенции</h3>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '10px' }}>
                  {tags.map(tag => (
                    <span key={tag} style={tagStyle}>{tag}</span>
                  ))}
                </div>
              </section>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' }}>
                <div style={infoCardStyle}>
                  <span style={infoLabelStyle}>Налоговый номер</span>
                  <span style={infoValueStyle}>{company.taxId || '—'}</span>
                </div>
                <div style={infoCardStyle}>
                  <span style={infoLabelStyle}>Контактный телефон</span>
                  <a href={`tel:${company.phone}`} style={{ ...infoValueStyle, color: '#1f7a5a', textDecoration: 'none' }}>{company.phone || '—'}</a>
                </div>
              </div>
            </div>

            <div className="right-col">
              <h3 style={sectionTitleStyle}>Локация</h3>
              <div style={{ 
                borderRadius: '12px', 
                overflow: 'hidden', 
                height: '220px', 
                border: '1px solid #e7e9ec',
                marginBottom: '16px'
              }}>
                <iframe 
                  width="100%" 
                  height="100%" 
                  frameBorder="0" 
                  src={`https://maps.google.com/maps?q=${encodeURIComponent(company.address || 'London')}&t=&z=14&ie=UTF8&iwloc=&output=embed`}
                ></iframe>
              </div>
              <p style={{ fontSize: '14px', color: '#7a838d', display: 'flex', alignItems: 'flex-start', gap: '8px' }}>
                <span></span> {company.address || 'Адрес не указан'}
              </p>
            </div>
          </div>

          {/* Modal Edit Form */}
          {editingCompany && (
            <div style={modalOverlayStyle}>
              <div className="card" style={{ width: '550px', padding: '32px', borderRadius: '14px' }}>
                <h2 style={{ marginBottom: '24px' }}>Редактирование профиля</h2>
                <form onSubmit={updateCompany} className="form-grid" style={{ gridTemplateColumns: '1fr' }}>
                  <div className="form-group">
                    <label>Название студии</label>
                    <input type="text" value={editFormData.name} onChange={(e) => setEditFormData({...editFormData, name: e.target.value})} style={inputStyle} />
                  </div>
                  <div className="form-group">
                    <label>Описание деятельности</label>
                    <textarea 
                      style={{ ...inputStyle, minHeight: '120px', resize: 'none' }}
                      value={editFormData.description} 
                      onChange={(e) => setEditFormData({...editFormData, description: e.target.value})} 
                    />
                  </div>
                  <div className="form-group">
                    <label>Ссылка на логотип (URL)</label>
                    <input type="text" value={editFormData.logoUrl} onChange={(e) => setEditFormData({...editFormData, logoUrl: e.target.value})} style={inputStyle} />
                  </div>
                  <div className="form-group">
                    <label>Юридический адрес</label>
                    <input type="text" value={editFormData.address} onChange={(e) => setEditFormData({...editFormData, address: e.target.value})} style={inputStyle} />
                  </div>
                  <div style={{ display: 'flex', gap: '12px', marginTop: '20px' }}>
                    <button type="submit" className="btn-primary" style={{ flex: 2, height: '48px' }}>Обновить данные</button>
                    <button type="button" onClick={() => setEditingCompany(null)} className="btn-secondary" style={{ flex: 1 }}>Отмена</button>
                  </div>
                </form>
              </div>
            </div>
          )}
        </div>
      ))}
    </div>
  );
};

const statusBadgeStyle = {
  background: '#e9f3ee',
  color: '#059669',
  padding: '4px 12px',
  borderRadius: '12px',
  fontSize: '12px',
  fontWeight: '700',
};

const sectionTitleStyle = {
  fontSize: '13px',
  textTransform: 'uppercase' as const,
  letterSpacing: '1px',
  color: '#a4abb3',
  marginBottom: '16px'
};

const tagStyle = {
  background: '#f1f3f4',
  color: '#3b434c',
  padding: '6px 14px',
  borderRadius: '10px',
  fontSize: '13px',
  fontWeight: '600',
  border: '1px solid #e7e9ec'
};

const infoCardStyle = {
  background: '#f6f7f8',
  padding: '16px',
  borderRadius: '16px',
  border: '1px solid #f1f3f4'
};

const infoLabelStyle = {
  display: 'block',
  fontSize: '11px',
  color: '#a4abb3',
  fontWeight: '700',
  marginBottom: '4px',
  textTransform: 'uppercase' as const
};

const infoValueStyle = {
  fontSize: '15px',
  color: '#101418',
  fontWeight: '600'
};

const inputStyle = {
  width: '100%',
  padding: '12px 16px',
  borderRadius: '12px',
  border: '1px solid #e7e9ec',
  fontSize: '14px',
  boxSizing: 'border-box' as const
};

const modalOverlayStyle: React.CSSProperties = {
  position: 'fixed',
  top: 0, left: 0, right: 0, bottom: 0,
  background: 'rgba(15, 23, 42, 0.7)',
  backdropFilter: 'blur(4px)',
  display: 'flex',
  justifyContent: 'center',
  alignItems: 'center',
  zIndex: 1000
};

export default CompaniesList;