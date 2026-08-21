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

// Show/hide password toggle, shared by login, signup, and account settings.
// Uses inline styles for positioning rather than Tailwind utility classes,
// since the compiled output.css only contains classes actually used in the
// original pages and doesn't include absolute-positioning utilities.
const PASSWORD_EYE_OPEN_SVG = '<path d="M1 12S5 4.5 12 4.5 23 12 23 12 19 19.5 12 19.5 1 12 1 12Z" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/><circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="2"/>';
const PASSWORD_EYE_OFF_SVG = '<path d="M3 3l18 18" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><path d="M10.6 5.1A11.6 11.6 0 0 1 12 5c7 0 11 7 11 7a13.7 13.7 0 0 1-3.1 3.8M6.5 6.7C3.6 8.6 1 12 1 12s4 7 11 7c1.6 0 3-.3 4.2-.9M9.9 9.9a3 3 0 0 0 4.2 4.2" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>';

function setupPasswordToggle(inputId, btnId, iconId) {
  const input = document.getElementById(inputId);
  const btn = document.getElementById(btnId);
  const icon = document.getElementById(iconId);
  if (!input || !btn || !icon) return;
  btn.addEventListener('click', () => {
    const willShow = input.type === 'password';
    input.type = willShow ? 'text' : 'password';
    icon.innerHTML = willShow ? PASSWORD_EYE_OFF_SVG : PASSWORD_EYE_OPEN_SVG;
    btn.setAttribute('aria-label', willShow ? 'Hide password' : 'Show password');
  });
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