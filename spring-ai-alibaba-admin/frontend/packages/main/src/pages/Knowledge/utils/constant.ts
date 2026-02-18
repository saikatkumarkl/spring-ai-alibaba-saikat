// @ts-nocheck
export function areIndexStatusesEqual(data) {
  const firstStatus = data[0].index_status;
  return data.every((item) => item.index_status === firstStatus);
}

export const CRON_PRESETS = [
  { label: 'Every Hour', value: '0 0 * * * ?', description: 'Runs at the start of every hour' },
  { label: 'Every 6 Hours', value: '0 0 */6 * * ?', description: 'Runs every 6 hours' },
  { label: 'Daily at Midnight', value: '0 0 0 * * ?', description: 'Runs once a day at 00:00' },
  { label: 'Daily at 6 AM', value: '0 0 6 * * ?', description: 'Runs once a day at 06:00' },
  { label: 'Weekly (Sunday)', value: '0 0 0 ? * SUN', description: 'Runs every Sunday at midnight' },
  { label: 'Monthly (1st)', value: '0 0 0 1 * ?', description: 'Runs on the 1st of every month' },
  { label: 'Custom', value: 'custom', description: 'Enter a custom cron expression' },
];
