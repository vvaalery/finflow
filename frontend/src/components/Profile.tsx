
import React, { useEffect, useState } from 'react';
import { useAuthStore } from '../store/authStore';
import apiClient from '../api/client';

const Profile: React.FC = () => {
  const { user, setUser } = useAuthStore();

  const [fullName, setFullName] = useState(user?.fullName || '');
  const [email, setEmail] = useState(user?.email || '');
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');

  useEffect(() => {
    setFullName(user?.fullName || '');
    setEmail(user?.email || '');
  }, [user]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!user) {
      setMessage('Необходимо авторизоваться');
      return;
    }

    setLoading(true);
    setMessage('');

    try {
      await apiClient.put('/users/profile', {
        fullName,
        email
      });

      setUser({
        ...user,
        fullName,
        email
      });

      setMessage('Профиль успешно обновлён');
    } catch (error: any) {
      setMessage('Ошибка обновления');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="card">
      <h2>Личный кабинет</h2>

      {message && (
        <div
          className="message"
          style={{
            background: message.includes('успешно') ? '#e9f3ee' : '#faf0ee',
            color: message.includes('успешно') ? '#1f7a5a' : '#a4473d',
            borderLeft: `3px solid ${message.includes('успешно') ? '#1f7a5a' : '#e6cdc9'}`,
            marginBottom: '20px'
          }}
        >
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
              onChange={(e) => setEmail(e.target.value)}
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
              style={{
                background: '#f6f7f8',
                color: '#7a838d'
              }}
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
