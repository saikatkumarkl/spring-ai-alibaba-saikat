import Keycloak from 'keycloak-js';
import { session } from './session';

/** Keycloak adapter singleton */
let _keycloak: Keycloak | null = null;

export interface KeycloakConfig {
  enabled: boolean;
  url: string;
  realm: string;
}

/**
 * Initialise keycloak-js with config fetched from the backend.
 * Returns null if Keycloak is disabled.
 */
export function initKeycloak(config: KeycloakConfig, clientId: string): Keycloak | null {
  if (!config.enabled) return null;

  _keycloak = new Keycloak({
    url: config.url,
    realm: config.realm,
    clientId,
  });

  return _keycloak;
}

/**
 * Trigger the Keycloak OIDC login redirect.
 * On success, stores the Keycloak access token in the existing session manager
 * so the rest of the app continues to work unchanged.
 *
 * @param kc        Keycloak instance
 * @param idpHint   If provided, Keycloak will skip its login page and redirect
 *                  directly to the specified external Identity Provider
 *                  (e.g. "azure-ad", "google").
 */
export async function keycloakLogin(kc: Keycloak, idpHint?: string): Promise<boolean> {
  try {
    const initOptions: any = {
      onLoad: 'login-required',
      checkLoginIframe: false,
      pkceMethod: 'S256',
    };

    // If idpHint is specified, configure Keycloak to go directly to the IdP
    if (idpHint) {
      initOptions.onLoad = 'check-sso';
    }

    const authenticated = await kc.init(initOptions);

    // If not authenticated and we have an idpHint, redirect directly to that IdP
    if (!authenticated && idpHint) {
      kc.login({ idpHint });
      return false; // will redirect
    }

    if (authenticated && kc.token && kc.refreshToken) {
      // Map Keycloak tokens into the existing session shape
      session.set({
        access_token: kc.token,
        // Keycloak returns tokenParsed.exp as Unix timestamp; convert to ms
        expires_in: kc.tokenParsed?.exp
          ? kc.tokenParsed.exp - Math.floor(Date.now() / 1000)
          : 3600,
        refresh_token: kc.refreshToken,
      });

      // Set up automatic token refresh
      setupTokenRefresh(kc);

      return true;
    }

    return false;
  } catch (err) {
    console.error('[Keycloak] Init failed:', err);
    return false;
  }
}

/**
 * Periodically refresh the Keycloak token before it expires.
 * Updates the session store so that API requests always have a fresh token.
 */
function setupTokenRefresh(kc: Keycloak) {
  // Refresh 30 seconds before expiry
  const refreshInterval = setInterval(async () => {
    try {
      const refreshed = await kc.updateToken(30);
      if (refreshed && kc.token && kc.refreshToken) {
        session.set({
          access_token: kc.token,
          expires_in: kc.tokenParsed?.exp
            ? kc.tokenParsed.exp - Math.floor(Date.now() / 1000)
            : 3600,
          refresh_token: kc.refreshToken,
        });
      }
    } catch {
      console.warn('[Keycloak] Token refresh failed — logging out');
      clearInterval(refreshInterval);
      kc.logout();
    }
  }, 60_000); // check every 60 seconds
}

/**
 * Logout from Keycloak and clear local session.
 */
export function keycloakLogout(redirectUri?: string) {
  session.clear();
  if (_keycloak) {
    _keycloak.logout({ redirectUri: redirectUri || window.location.origin });
  } else {
    window.location.reload();
  }
}

export function getKeycloak(): Keycloak | null {
  return _keycloak;
}
