/**
 * Legacy path utilities
 * Normalize legacy page paths by auto-appending the /admin prefix
 */

/**
 * Build a full legacy path (auto-adds /admin prefix)
 * @param path Raw path, e.g. '/prompts', '/prompt-detail'
 * @returns Full path, e.g. '/admin/prompts', '/admin/prompt-detail'
 */
export const getLegacyPath = (path: string): string => {
  // If the path already starts with /admin, return as-is
  if (path.startsWith('/admin')) {
    return path;
  }

  // If the path starts with /, prepend /admin
  if (path.startsWith('/')) {
    return `/admin${path}`;
  }

  // If the path does not start with /, add /admin/ prefix
  return `/admin/${path}`;
};

/**
 * Build a full path with query parameters
 * @param path Base path
 * @param params Query parameter object
 * @returns Full path, e.g. '/admin/prompt-detail?promptKey=xxx'
 */
export const buildLegacyPath = (path: string, params?: Record<string, string | number | null | undefined>): string => {
  const fullPath = getLegacyPath(path);

  if (!params || Object.keys(params).length === 0) {
    return fullPath;
  }

  const searchParams = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== null && value !== undefined) {
      searchParams.append(key, String(value));
    }
  });

  const queryString = searchParams.toString();
  return queryString ? `${fullPath}?${queryString}` : fullPath;
};

