import { IconFont, Input } from '@spark-ai/design';
import classNames from 'classnames';
import React, { useCallback, useEffect, useRef } from 'react';
import styles from './index.module.less';

interface SearchProps {
  /**
   * Search callback
   */
  onSearch?: (value: string) => void;
  /**
   * Custom className to override default styles
   */
  className?: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  /**
   * Debounce delay in ms for search-as-you-type (default: 300)
   */
  debounceMs?: number;
}

/**
 * Knowledge base search component
 * Contains search input with search-as-you-type (debounced)
 */
const Search: React.FC<SearchProps> = ({
  onSearch,
  className,
  onChange,
  value,
  placeholder = 'Type here...',
  debounceMs = 300,
}) => {
  const debounceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const latestValueRef = useRef(value);
  latestValueRef.current = value;

  // Cleanup timer on unmount
  useEffect(() => {
    return () => {
      if (debounceTimerRef.current) clearTimeout(debounceTimerRef.current);
    };
  }, []);

  const debouncedSearch = useCallback(
    (val: string) => {
      if (debounceTimerRef.current) clearTimeout(debounceTimerRef.current);
      debounceTimerRef.current = setTimeout(() => {
        onSearch?.(val);
      }, debounceMs);
    },
    [onSearch, debounceMs],
  );

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newValue = e.target.value;
    onChange(newValue);
    debouncedSearch(newValue);
  };

  const handleSearch = () => {
    if (debounceTimerRef.current) clearTimeout(debounceTimerRef.current);
    onSearch?.(value);
  };

  return (
    <Input
      className={classNames(styles['input'], className)}
      prefix={<IconFont type="spark-search-line" />}
      placeholder={placeholder}
      value={value}
      onChange={handleChange}
      onPressEnter={handleSearch}
      allowClear
    />
  );
};

export default Search;
