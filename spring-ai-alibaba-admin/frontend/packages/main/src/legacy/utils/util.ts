export const extractParametersFromDoubleBrace = (content: string) => {
  const regex = /\{\{(\w+)\}\}/g;
  const parameters: string[] = [];
  let match;
  while ((match = regex.exec(content)) !== null) {
    if (!parameters.includes(match[1])) {
      parameters.push(match[1]);
    }
  }
  return parameters;
};

export const safeJSONStringify = <T>(obj: any, fallback: () => T = () => "" as T, replacer?: (this: any, key: string, value: any) => any, space?: number) => {
  try {
    return JSON.stringify(obj, replacer, space);
  } catch (error) {
    return fallback();
  }
};

export const safeJSONParse = <T>(jsonString: string, fallback: () => T = () => ({} as T), reviver?: (this: any, key: string, value: any) => any) => {
  try {
    return JSON.parse(jsonString, reviver);
  } catch (error) {
    return fallback();
  }
};

export function copyToClipboard(text: string) {
  // Return a Promise object
  return new Promise((resolve, reject) => {
    if (navigator.clipboard && window.isSecureContext) {
      // Use Clipboard API to write to clipboard
      navigator.clipboard.writeText(text).then(resolve, reject);
    } else {
      // Fallback method for non-secure contexts or browsers that don't support Clipboard API
      const textArea = document.createElement('textarea');
      textArea.value = text;

      // Avoid scrollbars
      textArea.style.position = 'fixed';
      textArea.style.top = '0';
      textArea.style.left = '0';
      textArea.style.width = '2em';
      textArea.style.height = '2em';
      textArea.style.padding = '0';
      textArea.style.border = 'none';
      textArea.style.outline = 'none';
      textArea.style.boxShadow = 'none';
      textArea.style.background = 'transparent';

      document.body.appendChild(textArea);
      textArea.focus();
      textArea.select();

      try {
        const successful = document.execCommand('copy');
        successful ? resolve(void 0) : reject();
      } catch (err) {
        reject(err); // If execution fails, call reject
      }
      document.body.removeChild(textArea);
    }
  });
}
