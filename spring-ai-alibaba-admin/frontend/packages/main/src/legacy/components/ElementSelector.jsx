import React, { useState, useEffect, useRef, useCallback } from 'react';
import * as Diff2Html from 'diff2html';
import 'diff2html/bundles/css/diff2html.min.css';

const ElementSelector = ({
  children,
  onSelect = () => {},
  highlightStyle = {},
  enabled = true,
  debug = false
}) => {
  const [isSelecting, setIsSelecting] = useState(false);
  const [hoveredElement, setHoveredElement] = useState(null);
  const [mousePosition, setMousePosition] = useState({ x: 0, y: 0 });
  const [showModal, setShowModal] = useState(false);
  const [selectedElementInfo, setSelectedElementInfo] = useState(null);
  const [prompt, setPrompt] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [showDiffModal, setShowDiffModal] = useState(false);
  const [diffResult, setDiffResult] = useState(null);
  const [selectedFile, setSelectedFile] = useState(null);
  const [showErrorModal, setShowErrorModal] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const containerRef = useRef(null);
  const overlayRef = useRef(null);
  const tooltipRef = useRef(null);

  const defaultHighlightStyle = {
    position: 'fixed',
    backgroundColor: 'rgba(101, 162, 251, 0.2)',
    border: '2px solid #65a2fb',
    pointerEvents: 'none',
    zIndex: 999999,
    borderRadius: '2px',
    transition: 'all 0.05s ease-out',
    boxShadow: 'inset 0 0 0 1px rgba(101, 162, 251, 0.3), 0 0 0 1px rgba(101, 162, 251, 0.4)',
    outline: 'none'
  };

  const createTooltip = useCallback(() => {
    if (!tooltipRef.current) {
      const tooltip = document.createElement('div');
      tooltip.id = 'element-selector-tooltip';
      tooltip.style.cssText = `
        position: fixed;
        background: rgba(0, 0, 0, 0.9);
        color: white;
        padding: 8px 12px;
        border-radius: 6px;
        font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', monospace;
        font-size: 12px;
        line-height: 1.4;
        z-index: 1000000;
        pointer-events: none;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
        border: 1px solid rgba(255, 255, 255, 0.1);
        white-space: nowrap;
        display: none;
        max-width: 300px;
        word-break: break-all;
      `;
      document.body.appendChild(tooltip);
      tooltipRef.current = tooltip;
    }
    return tooltipRef.current;
  }, []);

  const removeTooltip = useCallback(() => {
    if (tooltipRef.current) {
      document.body.removeChild(tooltipRef.current);
      tooltipRef.current = null;
    }
  }, []);

  const updateTooltip = useCallback((element, x, y) => {
    const tooltip = tooltipRef.current;
    if (!tooltip || !element) return;

    const tagName = element.tagName.toLowerCase();
    const className = element.className ? `.${element.className.split(' ').filter(c => c).join('.')}` : '';
    const id = element.id ? `#${element.id}` : '';
    const inspPath = element.getAttribute('data-insp-path') || '';

    const selector = `${tagName}${id}${className}`;
    const text = element.textContent?.trim().slice(0, 50) || '';

    tooltip.innerHTML = `
      <div style="color: #66d9ff; font-weight: bold; margin-bottom: 4px;">${selector}</div>
      ${inspPath ? `<div style="color: #ffd666; margin-bottom: 2px;">Path: ${inspPath}</div>` : ''}
      ${text ? `<div style="color: #ccc; font-size: 11px;">Text: "${text}${text.length >= 50 ? '...' : ''}"</div>` : ''}
    `;

    // Calculate position to avoid going off screen
    const tooltipRect = { width: 300, height: 60 }; // Estimated size
    let left = x + 15;
    let top = y - tooltipRect.height - 10;

    // Prevent exceeding right boundary
    if (left + tooltipRect.width > window.innerWidth) {
      left = x - tooltipRect.width - 15;
    }

    // Prevent exceeding top boundary
    if (top < 0) {
      top = y + 15;
    }

    tooltip.style.left = `${left}px`;
    tooltip.style.top = `${top}px`;
    tooltip.style.display = 'block';
  }, []);

  const hideTooltip = useCallback(() => {
    if (tooltipRef.current) {
      tooltipRef.current.style.display = 'none';
    }
  }, []);

  const findSelectableElement = useCallback((element) => {
    let current = element;

    // Traverse DOM tree upward to find first element with data-insp-path attribute
    while (current && current !== document.body) {
      if (current.hasAttribute && current.hasAttribute('data-insp-path')) {
        return current;
      }
      current = current.parentElement;
    }

    return null;
  }, []);

  const createOverlay = useCallback(() => {
    if (!overlayRef.current) {
      const overlay = document.createElement('div');
      overlay.id = 'element-selector-overlay';
      Object.assign(overlay.style, defaultHighlightStyle, highlightStyle);
      overlay.style.display = 'none';
      document.body.appendChild(overlay);
      overlayRef.current = overlay;
    }
    return overlayRef.current;
  }, [highlightStyle]);

  const removeOverlay = useCallback(() => {
    if (overlayRef.current) {
      document.body.removeChild(overlayRef.current);
      overlayRef.current = null;
    }
  }, []);

  const updateOverlay = useCallback((element) => {
    const overlay = overlayRef.current;
    if (!overlay || !element) return;

    const rect = element.getBoundingClientRect();
    overlay.style.display = 'block';
    overlay.style.left = `${rect.left}px`;
    overlay.style.top = `${rect.top}px`;
    overlay.style.width = `${rect.width}px`;
    overlay.style.height = `${rect.height}px`;
  }, []);

  const hideOverlay = useCallback(() => {
    if (overlayRef.current) {
      overlayRef.current.style.display = 'none';
    }
  }, []);

  const handleKeyDown = useCallback((event) => {
    if (!enabled) return;

    if (event.shiftKey && event.key.toLowerCase() === 'e') {
      event.preventDefault();
      setIsSelecting(prev => !prev);

      if (debug) {
        console.log('Element selector toggled:', !isSelecting);
      }
    }

    if (event.key === 'Escape' && isSelecting) {
      setIsSelecting(false);
      hideOverlay();
    }
  }, [enabled, isSelecting, hideOverlay, debug]);

  const handleMouseMove = useCallback((event) => {
    if (!isSelecting) return;

    const target = event.target;
    const { clientX, clientY } = event;

    // Update mouse position
    setMousePosition({ x: clientX, y: clientY });

    // Exclude overlay and tooltip themselves
    if (target.id === 'element-selector-overlay' || target.id === 'element-selector-tooltip') return;

    // Find selectable element (has data-insp-path attribute)
    const selectableElement = findSelectableElement(target);

    if (selectableElement) {
      // If there is a container restriction, check if the selectable element is within the container
      if (containerRef.current && !containerRef.current.contains(selectableElement)) {
        hideOverlay();
        hideTooltip();
        return;
      }

      setHoveredElement(selectableElement);
      updateOverlay(selectableElement);
      updateTooltip(selectableElement, clientX, clientY);
    } else {
      // No selectable element found, hide highlight and tooltip
      hideOverlay();
      hideTooltip();
      setHoveredElement(null);
    }
  }, [isSelecting, findSelectableElement, updateOverlay, hideOverlay, updateTooltip, hideTooltip]);

  const handleClick = useCallback((event) => {
    if (!isSelecting) return;

    event.preventDefault();
    event.stopPropagation();

    const target = event.target;

    // Exclude overlay and tooltip themselves
    if (target.id === 'element-selector-overlay' || target.id === 'element-selector-tooltip') return;

    // Find selectable element (has data-insp-path attribute)
    const selectableElement = findSelectableElement(target);

    if (selectableElement) {
      // If there is a container restriction, check if the selectable element is within the container
      if (containerRef.current && containerRef.current.contains(selectableElement)) {
        const elementInfo = {
          element: selectableElement,
          tagName: selectableElement.tagName,
          className: selectableElement.className,
          id: selectableElement.id,
          textContent: selectableElement.textContent?.slice(0, 100),
          inspPath: selectableElement.getAttribute('data-insp-path'),
          attributes: Array.from(selectableElement.attributes).reduce((acc, attr) => {
            acc[attr.name] = attr.value;
            return acc;
          }, {})
        };

        setSelectedElementInfo(elementInfo);
        setShowModal(true);
        setIsSelecting(false);
        hideOverlay();
        hideTooltip();

        if (debug) {
          console.log('Element selected:', selectableElement);
          console.log('Insp path:', selectableElement.getAttribute('data-insp-path'));
        }
      }
    }
  }, [isSelecting, findSelectableElement, hideOverlay, hideTooltip, debug]);

  useEffect(() => {
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [handleKeyDown]);

  useEffect(() => {
    if (isSelecting) {
      createOverlay();
      createTooltip();
      // Add to document to ensure capturing all mouse movements
      document.addEventListener('mousemove', handleMouseMove, true);
      document.addEventListener('click', handleClick, true);
      document.body.style.cursor = 'crosshair';

      if (debug) {
        console.log('Element selection mode activated');
      }
    } else {
      hideOverlay();
      hideTooltip();
      document.removeEventListener('mousemove', handleMouseMove, true);
      document.removeEventListener('click', handleClick, true);
      document.body.style.cursor = '';
      setHoveredElement(null);

      if (debug) {
        console.log('Element selection mode deactivated');
      }
    }

    return () => {
      document.removeEventListener('mousemove', handleMouseMove, true);
      document.removeEventListener('click', handleClick, true);
      document.body.style.cursor = '';
    };
  }, [isSelecting, handleMouseMove, handleClick, createOverlay, hideOverlay, createTooltip, hideTooltip, debug]);

  useEffect(() => {
    return () => {
      removeOverlay();
      removeTooltip();
      document.body.style.cursor = '';
    };
  }, [removeOverlay, removeTooltip]);

  const handleModalConfirm = useCallback(async () => {
    if (selectedElementInfo && prompt.trim()) {
      console.log('Prompt:', prompt);
      console.log('Data-insp-path:', selectedElementInfo.inspPath);

      setIsLoading(true);

      try {
        // Call AI Coding API
        const response = await fetch('/_ai_coding', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            prompt: prompt.trim(),
            inspPath: selectedElementInfo.inspPath
          })
        });

        const result = await response.json();

        if (result.success) {
          console.log('✅ Claude execution result:', result);


        } else {
          console.error('❌ Claude execution failed:', result.error);
          setErrorMessage('Execution failed: ' + result.error);
          setShowErrorModal(true);
        }

      } catch (error) {
        console.error('❌ Failed to call AI Coding API:', error);
        setErrorMessage('Request failed: ' + error.message);
        setShowErrorModal(true);
      } finally {
        setIsLoading(false);
      }

      // Call original onSelect callback
      onSelect(selectedElementInfo.element, selectedElementInfo);
    }

    setShowModal(false);
    setPrompt('');
    setSelectedElementInfo(null);
  }, [selectedElementInfo, prompt, onSelect]);

  const handleModalCancel = useCallback(() => {
    setShowModal(false);
    setPrompt('');
    setSelectedElementInfo(null);
  }, []);

  const handleRevertFile = async (filename) => {
    if (!window.confirm(`Are you sure you want to revert all changes to "${filename}"? This cannot be undone.`)) {
      return;
    }

    setIsLoading(true); // Use existing loading state to give user feedback

    try {
      const response = await fetch('/_ai_coding/revert', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ filename })
      });

      const result = await response.json();

      if (result.success) {
        await getDiff();
      } else {
        setErrorMessage(`Failed to revert ${filename}: ${result.error}`);
        setShowErrorModal(true);
      }
    } catch (error) {
      setErrorMessage(`Failed to revert ${filename}: ${error.message}`);
      setShowErrorModal(true);
    } finally {
      setIsLoading(false);
    }
  };

  const getDiff = async () => {
    // Get git diff results
    const diffResponse = await fetch('/_ai_coding/diff', {
      method: 'GET',
    });

    const diffData = await diffResponse.json();

    if (diffData.success) {
      setDiffResult(diffData);
      setShowDiffModal(true);
    } else {
      console.error('❌ Failed to get git diff:', diffData.error);
      setErrorMessage('Failed to get file changes: ' + diffData.error);
      setShowErrorModal(true);
    }
  }

  useEffect(() => {
    getDiff();
  }, [])

  useEffect(() => {
    if (showDiffModal && diffResult?.files?.length > 0) {
      setSelectedFile(diffResult.files[0]);
    }
  }, [showDiffModal, diffResult]);

  return (
    <div ref={containerRef} style={{ position: 'relative' }}>
      <style>{`
        @keyframes spin {
          0% { transform: rotate(0deg); }
          100% { transform: rotate(360deg); }
        }
        /* Diff2html overflow and scroll fix */
        .d2h-diff-table {
          table-layout: fixed;
          width: auto;
        }
        .d2h-code-side-linenumber {
          position: static !important; /* Disable sticky line numbers */
          width: 50px !important;    /* Set a fixed width for line numbers */
          padding-right: 10px !important;
          text-align: right !important;
        }
        .d2h-code-side-line {
          width: auto !important; /* Allow code to take remaining space */
        }
        .d2h-file-header {
          word-break: break-all;
        }
        .d2h-code-line {
          word-wrap: break-word;
          white-space: pre-wrap;
        }
      `}</style>

      {children}

      {/* Element Info Popup */}
      {showModal && selectedElementInfo && (
        <div
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: 'rgba(0, 0, 0, 0.5)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000001
          }}
          onClick={handleModalCancel}
        >
          <div
            style={{
              backgroundColor: 'white',
              borderRadius: '8px',
              padding: '24px',
              minWidth: '500px',
              maxWidth: '80vw',
              maxHeight: '80vh',
              overflow: 'auto',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)'
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <h3 style={{ margin: '0 0 16px 0', fontSize: '18px', fontWeight: 'bold' }}>
              AI Prompt Input
            </h3>

            <textarea
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              placeholder="Enter prompt for AI..."
              style={{
                width: '100%',
                height: '100px',
                padding: '12px',
                border: '1px solid #d1d5db',
                borderRadius: '6px',
                fontSize: '14px',
                fontFamily: 'inherit',
                resize: 'vertical',
                outline: 'none',
                marginBottom: '16px'
              }}
              onFocus={(e) => {
                e.target.style.borderColor = '#3b82f6';
                e.target.style.boxShadow = '0 0 0 3px rgba(59, 130, 246, 0.1)';
              }}
              onBlur={(e) => {
                e.target.style.borderColor = '#d1d5db';
                e.target.style.boxShadow = 'none';
              }}
            />

            <div style={{ marginBottom: '20px' }}>
              <h4 style={{ margin: '0 0 12px 0', fontSize: '14px', fontWeight: '600', color: '#374151' }}>
                Element Information
              </h4>
              <div style={{
                backgroundColor: '#f9fafb',
                padding: '12px',
                borderRadius: '6px',
                fontFamily: 'SF Mono, Monaco, Inconsolata, Roboto Mono, monospace',
                fontSize: '12px',
                border: '1px solid #e5e7eb'
              }}>
                <div style={{ marginBottom: '8px' }}>
                  <strong style={{ color: '#374151' }}>Selector:</strong>{' '}
                  <span style={{ color: '#1f2937' }}>
                    {selectedElementInfo.tagName.toLowerCase()}
                    {selectedElementInfo.id ? `#${selectedElementInfo.id}` : ''}
                    {selectedElementInfo.className ? `.${selectedElementInfo.className.split(' ').filter(c => c).join('.')}` : ''}
                  </span>
                </div>
                <div style={{ marginBottom: '8px' }}>
                  <strong style={{ color: '#374151' }}>Path:</strong>{' '}
                  <span style={{ color: '#059669' }}>{selectedElementInfo.inspPath}</span>
                </div>
                {selectedElementInfo.textContent && (
                  <div>
                    <strong style={{ color: '#374151' }}>Text:</strong>{' '}
                    <span style={{ color: '#6b7280' }}>"{selectedElementInfo.textContent}"</span>
                  </div>
                )}
              </div>
            </div>

            <div style={{ display: 'flex', gap: '12px', justifyContent: 'flex-end' }}>
              <button
                onClick={handleModalCancel}
                style={{
                  padding: '8px 16px',
                  border: '1px solid #d1d5db',
                  borderRadius: '6px',
                  backgroundColor: 'white',
                  color: '#374151',
                  fontSize: '14px',
                  cursor: 'pointer',
                  transition: 'all 0.2s'
                }}
                onMouseEnter={(e) => {
                  e.target.style.backgroundColor = '#f9fafb';
                }}
                onMouseLeave={(e) => {
                  e.target.style.backgroundColor = 'white';
                }}
              >
                Cancel
              </button>
              <button
                onClick={handleModalConfirm}
                disabled={!prompt.trim() || isLoading}
                style={{
                  padding: '8px 16px',
                  border: 'none',
                  borderRadius: '6px',
                  backgroundColor: (!prompt.trim() || isLoading) ? '#9ca3af' : '#3b82f6',
                  color: 'white',
                  fontSize: '14px',
                  cursor: (!prompt.trim() || isLoading) ? 'not-allowed' : 'pointer',
                  transition: 'all 0.2s',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '8px'
                }}
                onMouseEnter={(e) => {
                  if (prompt.trim() && !isLoading) {
                    e.target.style.backgroundColor = '#2563eb';
                  }
                }}
                onMouseLeave={(e) => {
                  if (prompt.trim() && !isLoading) {
                    e.target.style.backgroundColor = '#3b82f6';
                  }
                }}
              >
                {isLoading && (
                  <div style={{
                    width: '14px',
                    height: '14px',
                    border: '2px solid transparent',
                    borderTop: '2px solid white',
                    borderRadius: '50%',
                    animation: 'spin 1s linear infinite'
                  }} />
                )}
                {isLoading ? 'Executing...' : 'Confirm'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Git Diff results display modal */}
      {showDiffModal && diffResult.hasChanges && diffResult.needCheckDiff !== false && (
        <div
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: 'rgba(0, 0, 0, 0.5)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000002
          }}
          onClick={() => setShowDiffModal(false)}
        >
          <div
            style={{
              backgroundColor: 'white',
              borderRadius: '8px',
              padding: '24px',
              minWidth: '800px',
              maxWidth: '90vw',
              maxHeight: '90vh',
              overflow: 'auto',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)'
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
              <h3 style={{ margin: 0, fontSize: '20px', fontWeight: 'bold' }}>
                📊 File Change Details
              </h3>
              <button
                onClick={() => setShowDiffModal(false)}
                style={{
                  border: 'none',
                  background: 'none',
                  fontSize: '24px',
                  cursor: 'pointer',
                  color: '#6b7280'
                }}
              >
                ×
              </button>
            </div>

            {diffResult.hasChanges && diffResult.needCheckDiff !== false ? (
              <>
                {/* Change summary */}
                <div style={{
                  backgroundColor: '#f0f9ff',
                  padding: '16px',
                  borderRadius: '8px',
                  marginBottom: '20px',
                  border: '1px solid #e0f2fe'
                }}>
                  <h4 style={{ margin: '0 0 12px 0', color: '#0369a1' }}>Change Summary</h4>
                  <div style={{ display: 'flex', gap: '20px', fontSize: '14px' }}>
                    <span>📁 Total Files: <strong>{diffResult.summary.totalFiles}</strong></span>
                    {diffResult.summary.modified > 0 && (
                      <span style={{ color: '#f59e0b' }}>✏️ Modified: <strong>{diffResult.summary.modified}</strong></span>
                    )}
                    {diffResult.summary.added > 0 && (
                      <span style={{ color: '#10b981' }}>➕ Added: <strong>{diffResult.summary.added}</strong></span>
                    )}
                    {diffResult.summary.deleted > 0 && (
                      <span style={{ color: '#ef4444' }}>🗑️ Deleted: <strong>{diffResult.summary.deleted}</strong></span>
                    )}
                  </div>
                </div>

                <div style={{ display: 'flex', border: '1px solid #e5e7eb', borderRadius: '8px', height: '60vh' }}>
                  {/* File list */}
                  <div style={{ width: '300px', borderRight: '1px solid #e5e7eb', overflowY: 'auto', background: '#f9fafb' }}>
                    <h4 style={{ margin: 0, padding: '12px 16px', fontSize: '16px', fontWeight: '600', borderBottom: '1px solid #e5e7eb', background: 'white', position: 'sticky', top: 0 }}>
                      Changed Files
                    </h4>
                    {diffResult.files.map((file, index) => (
                      <div
                        key={index}
                        onClick={() => setSelectedFile(file)}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          padding: '12px 16px',
                          borderBottom: '1px solid #e5e7eb',
                          fontSize: '14px',
                          cursor: 'pointer',
                          backgroundColor: selectedFile?.filename === file.filename ? '#e0f2fe' : 'transparent',
                          fontWeight: selectedFile?.filename === file.filename ? '600' : 'normal',
                        }}
                      >
                        <span style={{
                          display: 'inline-block',
                          width: '20px',
                          textAlign: 'center',
                          fontSize: '12px',
                          fontWeight: '600',
                          color: file.status === 'M' ? '#f59e0b' :
                                file.status === 'A' ? '#10b981' :
                                file.status === 'D' ? '#ef4444' : '#6b7280',
                          marginRight: '12px'
                        }}>
                          {file.status}
                        </span>
                        <span style={{
                          fontFamily: 'SF Mono, Monaco, Inconsolata, Roboto Mono, monospace',
                          flex: 1,
                          wordBreak: 'break-all'
                        }}>
                          {file.filename}
                        </span>
                      </div>
                    ))}
                  </div>

                  {/* Detailed Diff */}
                  <div style={{ flex: 1, overflow: 'auto', display: 'flex', flexDirection: 'column' }}>
                    {selectedFile && (
                      <>
                        <div style={{
                          padding: '12px 16px',
                          borderBottom: '1px solid #e5e7eb',
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                          background: '#f9fafb'
                        }}>
                          <span style={{ fontFamily: 'SF Mono, Monaco, Inconsolata, Roboto Mono, monospace', fontSize: '14px' }}>
                            {selectedFile.filename}
                          </span>
                          <button
                            onClick={() => handleRevertFile(selectedFile.filename)}
                            disabled={isLoading}
                            style={{
                              padding: '4px 10px',
                              border: '1px solid #ef4444',
                              borderRadius: '6px',
                              backgroundColor: isLoading ? '#fca5a5' : '#fee2e2',
                              color: '#dc2626',
                              fontSize: '12px',
                              fontWeight: '600',
                              cursor: isLoading ? 'not-allowed' : 'pointer',
                              transition: 'all 0.2s'
                            }}
                          >
                            {isLoading ? 'Reverting...' : 'Revert'}
                          </button>
                        </div>
                        <div
                          style={{ flex: 1, overflow: 'auto' }}
                          dangerouslySetInnerHTML={{
                            __html: selectedFile.diff && selectedFile.diff.trim()
                              ? Diff2Html.html(selectedFile.diff, {
                                  drawFileList: false,
                                  matching: 'lines',
                                  outputFormat: 'side-by-side',
                                })
                              : '<div style="padding: 20px; color: #6b7280; text-align: center;">No changes in this file or file is binary.</div>'
                          }}
                        />
                      </>
                    )}
                  </div>
                </div>
              </>
            ) : (
              <div style={{
                textAlign: 'center',
                padding: '40px 20px',
                color: '#6b7280'
              }}>
                <div style={{ fontSize: '48px', marginBottom: '16px' }}>✨</div>
                <h4 style={{ margin: '0 0 8px 0', color: '#374151' }}>No file changes detected</h4>
                <p style={{ margin: 0 }}>AI execution completed, but no files were modified</p>
              </div>
            )}

            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '20px' }}>
              <button
                onClick={() => setShowDiffModal(false)}
                style={{
                  padding: '8px 16px',
                  border: '1px solid #d1d5db',
                  borderRadius: '6px',
                  backgroundColor: 'white',
                  color: '#374151',
                  fontSize: '14px',
                  cursor: 'pointer',
                  transition: 'all 0.2s'
                }}
                onMouseEnter={(e) => {
                  e.target.style.backgroundColor = '#f9fafb';
                }}
                onMouseLeave={(e) => {
                  e.target.style.backgroundColor = 'white';
                }}
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Error prompt modal */}
      {showErrorModal && (
        <div
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: 'rgba(0, 0, 0, 0.5)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000003
          }}
          onClick={() => setShowErrorModal(false)}
        >
          <div
            style={{
              backgroundColor: 'white',
              borderRadius: '8px',
              padding: '24px',
              minWidth: '400px',
              maxWidth: '80vw',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)'
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <div style={{ display: 'flex', alignItems: 'center', marginBottom: '16px' }}>
              <div style={{
                width: '40px',
                height: '40px',
                borderRadius: '50%',
                backgroundColor: '#fee2e2',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                marginRight: '12px'
              }}>
                <span style={{ color: '#dc2626', fontSize: '20px' }}>⚠️</span>
              </div>
              <h3 style={{ margin: 0, fontSize: '18px', fontWeight: 'bold', color: '#dc2626' }}>
                Operation Failed
              </h3>
            </div>

            <p style={{
              margin: '0 0 20px 0',
              color: '#374151',
              lineHeight: '1.5'
            }}>
              {errorMessage}
            </p>

            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
              <button
                onClick={() => setShowErrorModal(false)}
                style={{
                  padding: '8px 16px',
                  border: '1px solid #d1d5db',
                  borderRadius: '6px',
                  backgroundColor: 'white',
                  color: '#374151',
                  fontSize: '14px',
                  cursor: 'pointer',
                  transition: 'all 0.2s'
                }}
                onMouseEnter={(e) => {
                  e.target.style.backgroundColor = '#f9fafb';
                }}
                onMouseLeave={(e) => {
                  e.target.style.backgroundColor = 'white';
                }}
              >
                Got it
              </button>
            </div>
          </div>
        </div>
      )}

      {debug && (
        <div
          style={{
            position: 'fixed',
            top: '10px',
            right: '10px',
            backgroundColor: 'rgba(0, 0, 0, 0.8)',
            color: 'white',
            padding: '8px 12px',
            borderRadius: '4px',
            fontSize: '12px',
            zIndex: 10001,
            fontFamily: 'monospace'
          }}
        >
          {isSelecting ? 'Selecting Mode (Shift+E to exit)' : 'Press Shift+E to select'}
        </div>
      )}
    </div>
  );
};

export default ElementSelector;
