// Shared auth helpers, included on every page that needs a logged-in user.
const AUTH_TOKEN_KEY = 'sow_token';
const AUTH_USER_KEY = 'sow_user';

function getToken() {
  return localStorage.getItem(AUTH_TOKEN_KEY);
}

function getStoredUser() {
  try {
    return JSON.parse(localStorage.getItem(AUTH_USER_KEY));
  } catch (e) {
    return null;
  }
}

function saveSession(token, user) {
  localStorage.setItem(AUTH_TOKEN_KEY, token);
  localStorage.setItem(AUTH_USER_KEY, JSON.stringify(user));
}

function clearSession() {
  localStorage.removeItem(AUTH_TOKEN_KEY);
  localStorage.removeItem(AUTH_USER_KEY);
}

function logout() {
  clearSession();
  window.location.href = '/login';
}

function homeUrlForRole(role) {
  return role === 'teacher' ? '/teacher/dashboard' : '/';
}

// fetch() wrapper that attaches the auth token and bounces to /login on 401.
async function authFetch(url, options = {}) {
  const token = getToken();
  const headers = Object.assign({}, options.headers, token ? { Authorization: `Bearer ${token}` } : {});
  const res = await fetch(url, Object.assign({}, options, { headers }));
  if (res.status === 401) {
    clearSession();
    window.location.href = '/login';
    throw new Error('Not authenticated');
  }
  return res;
}

// Call at the top of any protected page's script.
// requiredRole: null (any logged-in user), 'pupil', or 'teacher'.
// Returns the user object, or null (and redirects) if the check fails.
async function requireAuth(requiredRole = null) {
  const token = getToken();
  if (!token) {
    window.location.href = '/login';
    return null;
  }
  try {
    const res = await authFetch('/api/auth/me');
    const user = await res.json();
    saveSession(token, user);
    if (requiredRole && user.role !== requiredRole) {
      window.location.href = homeUrlForRole(user.role);
      return null;
    }
    return user;
  } catch (e) {
    return null;
  }
}
