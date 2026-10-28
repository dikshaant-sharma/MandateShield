import React from 'react';
import { Database, RefreshCw } from 'lucide-react';

export default function Header({ title, subtitle, isDemoMode, setIsDemoMode, onRefresh, isRefreshing }) {
  return (
    <header className="page-header">
      <div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <h1 className="page-title">{title}</h1>
          {isDemoMode && (
            <span className="badge badge-warning" style={{ fontSize: '11px', textTransform: 'none' }}>
              Sample Data Active
            </span>
          )}
        </div>
        <p className="page-subtitle">{subtitle}</p>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
        <button
          onClick={() => setIsDemoMode(!isDemoMode)}
          className="btn btn-secondary btn-sm"
          title="Switch between live API and realistic sample mock dataset"
        >
          <Database size={14} />
          <span>{isDemoMode ? 'Switch to Live API' : 'Switch to Sample Data'}</span>
        </button>

        {onRefresh && (
          <button
            onClick={onRefresh}
            disabled={isRefreshing}
            className="btn btn-secondary btn-sm"
            title="Reload data"
          >
            <RefreshCw size={14} className={isRefreshing ? 'spinner' : ''} />
            <span>{isRefreshing ? 'Refreshing...' : 'Refresh'}</span>
          </button>
        )}
      </div>
    </header>
  );
}
