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
    <div style={{ 
      minHeight: '100vh', 
      display: 'flex',
      background: '#f5efe5'
    }}>
      
      <div style={{
        flex: 1,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '28px 24px'
      }}>
        <div style={{
          maxWidth: '440px',
          width: '100%'
        }}>
          
          <div style={{ marginBottom: '30px' }}>
            <h1 style={{
              fontSize: 'clamp(28px, 5vw, 36px)',
              fontWeight: '600',
              background: 'linear-gradient(135deg, #5b1e32 0%, #35101f 100%)',
              WebkitBackgroundClip: 'text',
              WebkitTextFillColor: 'transparent',
              backgroundClip: 'text',
              marginBottom: '12px'
            }}>
              FinFlow
            </h1>
            <p style={{ color: '#8b7779', fontSize: 'clamp(14px, 3vw, 16px)' }}>
              Управление корпоративными финансами
            </p>
          </div>

          
          <div style={{ marginBottom: '22px' }}>
            <h2 style={{ fontSize: 'clamp(24px, 4vw, 28px)', fontWeight: '500', color: '#351a25', marginBottom: '8px' }}>
              Добро пожаловать!
            </h2>
            <p style={{ color: '#8c7e79', fontSize: '14px' }}>
              Войдите в систему, чтобы продолжить работу
            </p>
          </div>

          
          <form onSubmit={handleSubmit}>
            <div style={{ marginBottom: '24px' }}>
              <label style={{ display: 'block', marginBottom: '8px', fontSize: '14px', fontWeight: '500', color: '#59424a' }}>
                Email
              </label>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="user@finance.com"
                style={{
                  width: '100%',
                  padding: '14px 16px',
                  border: '1px solid #e2d7cb',
                  borderRadius: '14px',
                  fontSize: '15px',
                  background: 'white',
                  transition: 'all 0.2s',
                  outline: 'none',
                  boxSizing: 'border-box'
                }}
                onFocus={(e) => { e.target.style.borderColor = '#8d3a55'; e.target.style.boxShadow = '0 0 0 3px rgba(141, 58, 85, 0.12)'; }}
                onBlur={(e) => { e.target.style.borderColor = '#e2d7cb'; e.target.style.boxShadow = 'none'; }}
                required
              />
            </div>

            <div style={{ marginBottom: '28px' }}>
              <label style={{ display: 'block', marginBottom: '8px', fontSize: '14px', fontWeight: '500', color: '#59424a' }}>
                Пароль
              </label>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                style={{
                  width: '100%',
                  padding: '14px 16px',
                  border: '1px solid #e2d7cb',
                  borderRadius: '14px',
                  fontSize: '15px',
                  background: 'white',
                  transition: 'all 0.2s',
                  outline: 'none',
                  boxSizing: 'border-box'
                }}
                onFocus={(e) => { e.target.style.borderColor = '#8d3a55'; e.target.style.boxShadow = '0 0 0 3px rgba(141, 58, 85, 0.12)'; }}
                onBlur={(e) => { e.target.style.borderColor = '#e2d7cb'; e.target.style.boxShadow = 'none'; }}
                required
              />
            </div>

            {error && (
              <div style={{
                padding: '12px 16px',
                background: '#fef0f0',
                borderLeft: '3px solid #d8aaa2',
                borderRadius: '12px',
                marginBottom: '20px',
                color: '#9c6259',
                fontSize: '14px'
              }}>
                {error}
              </div>
            )}

            <button
              type="submit"
              disabled={isLoading}
              style={{
                width: '100%',
                padding: '14px',
                background: 'linear-gradient(135deg, #7a2945 0%, #431526 100%)',
                color: 'white',
                border: 'none',
                borderRadius: '40px',
                fontSize: '16px',
                fontWeight: '500',
                cursor: 'pointer',
                transition: 'all 0.2s',
                boxShadow: '0 2px 8px rgba(91, 30, 50, 0.18)'
              }}
              onMouseEnter={(e) => { e.currentTarget.style.transform = 'translateY(-1px)'; e.currentTarget.style.boxShadow = '0 4px 12px rgba(91, 30, 50, 0.28)'; }}
              onMouseLeave={(e) => { e.currentTarget.style.transform = 'translateY(0)'; e.currentTarget.style.boxShadow = '0 2px 8px rgba(91, 30, 50, 0.18)'; }}
            >
              {isLoading ? 'Вход...' : 'Войти →'}
            </button>
          </form>

        </div>
      </div>

      
      <div style={{
        flex: 1,
        background: 'linear-gradient(135deg, #7a2945 0%, #431526 100%)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        position: 'relative',
        overflow: 'hidden'
      }}>
        
        <div style={{
          position: 'absolute',
          top: '50%',
          left: '50%',
          transform: 'translate(-50%, -50%)',
          width: 'clamp(280px, 45vw, 380px)',
          height: 'clamp(280px, 45vw, 380px)',
          background: 'rgba(255, 238, 220, 0.09)',
          borderRadius: '50%',
          backdropFilter: 'blur(2px)'
        }} />
        
        
        <div style={{
          position: 'absolute',
          top: '15%',
          right: '15%',
          width: 'clamp(50px, 8vw, 80px)',
          height: 'clamp(50px, 8vw, 80px)',
          background: 'rgba(255, 238, 220, 0.12)',
          borderRadius: '50%'
        }} />
        <div style={{
          position: 'absolute',
          bottom: '20%',
          left: '10%',
          width: 'clamp(70px, 12vw, 100px)',
          height: 'clamp(70px, 12vw, 100px)',
          background: 'rgba(255, 238, 220, 0.12)',
          borderRadius: '50%'
        }} />
        
        
        <div style={{ 
          textAlign: 'center', 
          color: 'white', 
          zIndex: 1, 
          width: '80%', 
          maxWidth: '400px',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: '1px'
        }}>
          
          <svg
            viewBox="0 0 100 100"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
            style={{ width: 'clamp(80px, 15vw, 110px)', height: 'auto' }}
          >
            <path 
              d="M15 65 Q30 45 45 60 Q55 70 70 55 Q85 40 85 40" 
              stroke="white" 
              strokeWidth="3" 
              strokeLinecap="round" 
              fill="none" 
            />
            <circle cx="45" cy="60" r="3.5" fill="white" />
            <circle cx="70" cy="55" r="3.5" fill="white" />
            <circle cx="85" cy="40" r="3.5" fill="white" />
            <circle cx="30" cy="50" r="3" fill="white" fillOpacity="0.6" />
            <circle cx="55" cy="70" r="3" fill="white" fillOpacity="0.6" />
            <circle cx="20" cy="62" r="2.5" fill="white" fillOpacity="0.4" />
            <circle cx="78" cy="48" r="2.5" fill="white" fillOpacity="0.4" />
          </svg>
          
          
          <div>
            <h3 style={{ 
              fontSize: 'clamp(18px, 3.5vw, 22px)', 
              fontWeight: '500', 
              marginBottom: '6px',
              letterSpacing: '-0.3px'
            }}>
              Финансовый дашборд
            </h3>
            <p style={{ 
              fontSize: 'clamp(11px, 2.5vw, 13px)', 
              opacity: 0.85,
              lineHeight: '1.5',
              margin: 0
            }}>
              Прогнозирование денежных потоков<br />
              Платежный календарь<br />
              Аналитика и отчетность
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default LoginForm;