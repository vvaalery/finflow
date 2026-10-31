import React, { useState, useEffect } from 'react';
import { useAuthStore } from '../store/authStore';
import apiClient from '../api/client';

interface User {
  id: string;
  email: string;
  fullName: string;
  role: { id: number; name: string };
  isActive: boolean;
}

interface Role {
  id: number;
  name: string;
}

const UserManagement: React.FC = () => {
  const { user } = useAuthStore();
  const [users, setUsers] = useState<User[]>([]);
  const [roles, setRoles] = useState<Role[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [message, setMessage] = useState('');
  const [formData, setFormData] = useState({
    email: '',
    password: '',
    fullName: '',
    roleId: 3
  });

  const normalizedUserRole = (user?.role || '').replace('ROLE_', '');
  const isAdmin = normalizedUserRole === 'ADMIN';

  const inputStyle = {
    width: '100%',
    padding: '12px 14px',
    border: '1px solid #d4e2ef',
    borderRadius: '14px',
    fontSize: '14px',
    boxSizing: 'border-box' as const,
    height: '48px',
    background: 'white'
  };

  useEffect(() => {
    if (isAdmin) {
      loadUsers();
      loadRoles();
    }
  }, [isAdmin]);

  const loadUsers = async () => {
    try {
      const response = await apiClient.get('/admin/users');
      setUsers(response.data);
    } catch (error) {
      console.error('Error loading users:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadRoles = async () => {
    try {
      const response = await apiClient.get('/admin/roles');
      setRoles(response.data);
    } catch (error) {
      console.error('Error loading roles:', error);
    }
  };

  const createUser = async (e: React.FormEvent) => {
    e.preventDefault();
    setMessage('');
    try {
      await apiClient.post('/admin/users', formData);
      setShowForm(false);
      setFormData({ email: '', password: '', fullName: '', roleId: 3 });
      loadUsers();
      setMessage(' Пользователь создан');
      setTimeout(() => setMessage(''), 3000);
    } catch (error) {
      setMessage(' Ошибка создания');
      setTimeout(() => setMessage(''), 3000);
    }
  };

  const toggleUserStatus = async (id: string, currentStatus: boolean) => {
    try {
      await apiClient.patch(`/admin/users/${id}/status`, { isActive: !currentStatus });
      loadUsers();
      setMessage(` Пользователь ${!currentStatus ? 'активирован' : 'заблокирован'}`);
      setTimeout(() => setMessage(''), 3000);
    } catch (error) {
      setMessage(' Ошибка изменения статуса');
      setTimeout(() => setMessage(''), 3000);
    }
  };

  const getRoleName = (roleName: string) => {
    switch (roleName.replace('ROLE_', '')) {
      case 'ADMIN':
        return { name: 'Администратор', color: '#1d796c', bg: 'rgba(90, 155, 192, 0.1)' };
      case 'FINANCE_MANAGER':
      case 'FINANCIAL_MANAGER':
        return { name: 'Финансовый менеджер', color: '#1d796c', bg: 'rgba(29, 121, 108, 0.1)' };
      case 'MANAGER':
      case 'DIRECTOR':
        return { name: 'Руководитель', color: '#cc8b2c', bg: 'rgba(204, 139, 44, 0.1)' };
      default:
        return { name: 'Просмотр', color: '#8792a2', bg: 'rgba(122, 154, 190, 0.1)' };
    }
  };

  if (!isAdmin) {
    return (
      <div className="card">
        <div style={{ textAlign: 'center', padding: '40px', color: '#9c6259' }}>
           Доступ запрещён. Только для администраторов.
        </div>
      </div>
    );
  }

  return (
    <div className="card">
      <div className="flex-between" style={{ marginBottom: '24px' }}>
        <h2> Управление пользователями</h2>
        <button
          onClick={() => setShowForm(!showForm)}
          className="btn-primary"
          style={{ padding: '8px 20px', height: '48px', background: showForm ? '#963f50' : 'linear-gradient(135deg, #7b2b46 0%, #5b1d33 100%)' }}
        >
          {showForm ? ' Отмена' : '+ Добавить пользователя'}
        </button>
      </div>

      {message && (
        <div style={{
          padding: '12px 16px',
          borderRadius: '12px',
          marginBottom: '20px',
          background: /создан|активирован/.test(message) ? '#e8f2e9' : '#fbebeb',
          color: /создан|активирован/.test(message) ? '#386b4c' : '#963f50',
          fontSize: '14px'
        }}>
          {message}
        </div>
      )}

      {showForm && (
        <form onSubmit={createUser} style={{
          marginBottom: '28px',
          padding: '20px',
          background: '#fbf5ef',
          borderRadius: '18px',
          border: '1px solid #e9ded3'
        }}>
          <div className="form-grid">
            <div className="form-group">
              <label>Email</label>
              <input
                type="email"
                placeholder="user@example.com"
                value={formData.email}
                onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                required
                style={inputStyle}
              />
            </div>
            <div className="form-group">
              <label>Пароль</label>
              <input
                type="password"
                placeholder="••••••••"
                value={formData.password}
                onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                required
                style={inputStyle}
              />
            </div>
            <div className="form-group">
              <label>Полное имя</label>
              <input
                type="text"
                placeholder="Иванов Иван Иванович"
                value={formData.fullName}
                onChange={(e) => setFormData({ ...formData, fullName: e.target.value })}
                required
                style={inputStyle}
              />
            </div>
            <div className="form-group">
              <label>Роль</label>
              <select
                value={formData.roleId}
                onChange={(e) => setFormData({ ...formData, roleId: parseInt(e.target.value) })}
                style={inputStyle}
              >
                {roles.map((role) => {
                  const roleInfo = getRoleName(role.name);
                  return (
                    <option key={role.id} value={role.id}>
                      {roleInfo.name}
                    </option>
                  );
                })}
              </select>
            </div>
          </div>
          <div style={{ display: 'flex', gap: '12px', marginTop: '8px' }}>
            <button type="submit" className="btn-primary" style={{ height: '48px' }}>Создать пользователя</button>
            <button type="button" onClick={() => setShowForm(false)} className="btn-secondary" style={{ height: '48px' }}>Отмена</button>
          </div>
        </form>
      )}

      {loading ? (
        <div className="loading">Загрузка...</div>
      ) : users.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '40px', color: '#8aaec9' }}> Нет пользователей</div>
      ) : (
        <div className="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>Email</th>
                <th>Имя</th>
                <th>Роль</th>
                <th>Статус</th>
                <th>Действия</th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => {
                const roleInfo = getRoleName(u.role?.name || '');
                return (
                  <tr key={u.id}>
                    <td>{u.email}</td>
                    <td><strong>{u.fullName}</strong></td>
                    <td>
                      <span style={{
                        display: 'inline-block',
                        padding: '4px 12px',
                        borderRadius: '20px',
                        fontSize: '12px',
                        background: roleInfo.bg,
                        color: roleInfo.color
                      }}>
                        {roleInfo.name}
                      </span>
                    </td>
                    <td>
                      <span style={{
                        display: 'inline-block',
                        padding: '4px 12px',
                        borderRadius: '20px',
                        fontSize: '12px',
                        background: u.isActive ? '#e2f3e8' : '#fef0f0',
                        color: u.isActive ? '#2c6e4e' : '#9c6259'
                      }}>
                        {u.isActive ? 'Активен' : 'Заблокирован'}
                      </span>
                    </td>
                    <td>
                      <button
                        onClick={() => toggleUserStatus(u.id, u.isActive)}
                        className="btn-secondary"
                        style={{ 
                          background: u.isActive ? '#fef0f0' : '#e2f3e8',
                          color: u.isActive ? '#9c6259' : '#2c6e4e',
                          borderColor: u.isActive ? '#d8aaa2' : '#5cb085',
                          height: '36px',
                          padding: '0 16px'
                        }}
                      >
                        {u.isActive ? 'Заблокировать' : 'Разблокировать'}
                      </button>
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

export default UserManagement;