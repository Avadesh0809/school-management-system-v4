/* Global configuration
 * API_BASE is relative by default so the same build works when the
 * frontend is served by Nginx (which proxies /api to the backend) or
 * directly from the Spring Boot host. Override to an absolute URL
 * (e.g. http://localhost:8080/api) when opening the HTML files via
 * file:// or a different origin during local development.
 */
window.SMS_CONFIG = {
  // Use relative '/api' when the frontend is served from the same origin
  // (e.g. behind nginx or by Spring Boot). Fall back to the dev backend
  // URL when opening the HTML files via file:// or a different host.
  API_BASE: (location.protocol === 'file:' || location.port === '63342')
    ? 'http://localhost:8080/api'
    : '/api',
  TOKEN_KEY: 'sms_token',
  REFRESH_KEY: 'sms_refresh',
  USER_KEY: 'sms_user'
};
