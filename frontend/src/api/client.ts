import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';
const apiClient = axios.create({ baseURL: API_BASE_URL, headers: { 'Content-Type': 'application/json' } });

apiClient.interceptors.request.use((config) => {
  const auth = localStorage.getItem('auth-storage');
  if (auth) {
    try {
      const parsed = JSON.parse(auth);
      const token = parsed?.state?.user?.token;
      if (token) config.headers.Authorization = `Bearer ${token}`;
    } catch {}
  }
  return config;
});
export default apiClient;
