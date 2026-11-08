import React, { useState } from 'react';
import { useAuthStore } from '../store/authStore';

const LoginForm: React.FC = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const { login, isLoading, error } = useAuthStore();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await login(email, password);
      window.location.href = '/dashboard';
    } catch (err) {
      console.error('Login failed:', err);
    }
  };

  return (
    <div className="auth">
      <div className="auth-form-side">
        <div className="auth-card">
          <div className="auth-brand">
            <div className="brand-mark" aria-hidden="true" />
            <span className="brand-name">FinFlow</span>
          </div>
          <h1>Вход в систему</h1>
          <p className="lead">Управление корпоративными финансами и прогнозирование денежных потоков.</p>

          <form onSubmit={handleSubmit}>
            <label className="field-label">
              Email
              <input
                type="email"
                value={email}
                onChange={e => setEmail(e.target.value)}
                placeholder="name@company.com"
                autoComplete="username"
                required
              />
            </label>
            <label className="field-label">
              Пароль
              <input
                type="password"
                value={password}
                onChange={e => setPassword(e.target.value)}
                placeholder="Введите пароль"
                autoComplete="current-password"
                required
              />
            </label>
            {error && <div className="inline-error" role="alert" style={{ marginTop: 0 }}>{error}</div>}
            <button type="submit" className="btn-primary" disabled={isLoading}>
              {isLoading ? 'Вход…' : 'Войти'}
            </button>
          </form>
        </div>
      </div>

      <aside className="auth-aside" aria-hidden="true">
        <div className="brand" style={{ padding: 0, position: 'relative', zIndex: 1 }}>
          <div className="brand-mark" style={{ background: '#fff' }} />
        </div>
        <blockquote>Денежный поток виден на месяцы вперёд.</blockquote>
        <div className="auth-facts">
          <div><strong>4</strong><span>метода прогноза</span></div>
          <div><strong>80%</strong><span>доверительный интервал</span></div>
          <div><strong>JWT</strong><span>защищённый доступ</span></div>
        </div>
      </aside>
    </div>
  );
};

export default LoginForm;