import apiClient from './client';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface AuthResponse {
  id: string;  
  token: string;
  role: string;
  fullName: string;
  email: string;
}

export const login = async (data: LoginRequest): Promise<AuthResponse> => {
  const response = await apiClient.post('/auth/login', data);
  return response.data;
};