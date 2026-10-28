import axios from 'axios';

const api = axios.create({ baseURL: '/api/v1', headers: { 'Content-Type': 'application/json' }, timeout: 15000 });
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('mandateshield_token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

export const register = (payload) => api.post('/auth/register', payload).then(r => r.data);
export const login = (payload) => api.post('/auth/login', payload).then(r => r.data);
export const fetchMe = () => api.get('/auth/me').then(r => r.data);
export const fetchDashboard = () => api.get('/dashboard').then(r => r.data);
export const fetchTransactions = () => api.get('/transactions').then(r => r.data);
export const addTransaction = (payload) => api.post('/transactions', payload).then(r => r.data);
export const importTransactions = (payload) => api.post('/transactions/import', payload).then(r => r.data);
export const deleteTransaction = (id) => api.delete(`/transactions/${id}`);
export const runPipeline = () => api.post('/pipeline/run').then(r => r.data);
export const resolveDescriptor = (payload) => api.post('/resolver/resolve', payload).then(r => r.data);
export const listPatterns = () => api.get('/resolver/patterns').then(r => r.data);
export const clearPatternCache = () => api.post('/resolver/patterns/cache/clear').then(r => r.data);
export const getPlaybook = (rail, merchant) => api.get('/cancellation/playbook', { params: { rail, merchant } }).then(r => r.data);
export const generateLetter = (payload) => api.post('/cancellation/generate-letter', payload).then(r => r.data);
export const revokeSubscription = (id) => api.post(`/cancellation/${id}/revoke`).then(r => r.data);
export const requestCancellation = (id) => api.post(`/cancellation/${id}/request`).then(r => r.data);
export const confirmCancellation = (id) => api.post(`/cancellation/${id}/confirm`).then(r => r.data);
export const reportUnused = (id) => api.post(`/cancellation/${id}/report-unused`).then(r => r.data);
export const confirmSubscriptionActivity = (id) => api.post(`/cancellation/${id}/confirm-activity`).then(r => r.data);
export const seedSyntheticHistory = () => api.post('/demo/seed').then(r => r.data);
export const fetchSyntheticEvaluation = () => api.get('/demo/evaluation').then(r => r.data);
export default api;
