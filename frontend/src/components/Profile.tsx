import React, { useState } from 'react';
import { useAuthStore } from '../store/authStore';
import apiClient from '../api/client';

const Profile: React.FC = () => {
  const { user } = useAuthStore();
  const [fullName, setFullName] = useState(user?.fullName || '');
  const [email] = useState(user?.email || '');
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setMessage('');
    
    try {
      const response = await apiClient.put('/users/profile', { fullName });
      
      // Обновляем данные в store
      const updatedUser = { ...user, fullName: response.data.fullName }; 
      localStorage.setItem('auth-storage', JSON.stringify({ state: { user: updatedUser } }));
      
      setMessage(' Профиль успешно обновлён');
      setTimeout(() => window.location.reload(), 1500);
    } catch (error: any) {
      setMessage(' Ошибка обновления');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="card">
      <h2> Личный кабинет</h2>
      
      {message && (
        <div className="message" style={{ 
          background: message.includes('успешно') ? '#e2f3e8' : '#fef0f0', 
          color: message.includes('успешно') ? '#2c6e4e' : '#9c6259',
          borderLeft: `3px solid ${message.includes('успешно') ? '#5cb085' : '#d8aaa2'}`,
          marginBottom: '20px'
        }}>
          {message}
        </div>
      )}
      
      <form onSubmit={handleSubmit}>
        <div className="form-grid">
          <div className="form-group">
            <label>Email</label>
            <input
              type="email"
              value={email}
              readOnly
              aria-label="Email пользователя (только чтение)"
              placeholder="email@example.com"
              required
            />
          </div>
          
          <div className="form-group">
            <label>Полное имя</label>
            <input
              type="text"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              placeholder="Иванов Иван Иванович"
              required
            />
          </div>
          
          <div className="form-group">
            <label>Роль</label>
            <input
              type="text"
              value={user?.role?.replace('ROLE_', '') || ''}
              disabled
              style={{ background: '#f5f7fa', color: '#8792a2' }}
            />
          </div>
        </div>
        
        <button
          type="submit"
          disabled={loading}
          className="btn-primary"
        >
          {loading ? 'Сохранение...' : 'Сохранить изменения'}
        </button>
      </form>
      
    </div>
  );
};

export default Profile;