import React from 'react';
import { LayoutDashboard, ReceiptText, Shield, LogOut, Compass, Ban } from 'lucide-react';

export default function Sidebar({ activeTab, setActiveTab, user, onLogout }) {
  return (
    <aside className="sidebar">
      <div className="sidebar-logo">
        <div className="sidebar-logo-icon"><Shield size={20} color="#fff" /></div>
        <div className="sidebar-logo-text">
          <span className="brand">MandateShield</span>
          <span className="tagline">Payment intelligence</span>
        </div>
      </div>
      <nav className="sidebar-nav">
        <div className="nav-section-label">Your account</div>
        {[
          ['dashboard', 'Overview', LayoutDashboard],
          ['transactions', 'Transactions', ReceiptText],
          ['resolver', 'Descriptor Studio', Compass],
          ['playbooks', 'Cancellation Studio', Ban]
        ].map(([id, label, Icon]) => (
          <button
            key={id}
            onClick={() => setActiveTab(id)}
            className={`nav-item ${activeTab === id ? 'active' : ''}`}
          >
            <Icon className="nav-icon" size={17} />
            <span>{label}</span>
          </button>
        ))}
      </nav>
      <div className="sidebar-footer">
        <div className="user-pill">
          <div className="user-avatar">{user.name?.split(' ').map(n=>n[0]).join('').slice(0,2)}</div>
          <div className="user-info">
            <div className="name">{user.name}</div>
            <div className="id">{user.email}</div>
          </div>
        </div>
        <button className="btn btn-secondary btn-sm" style={{width:'100%',justifyContent:'center',marginTop:12}} onClick={onLogout}>
          <LogOut size={15}/>Sign out
        </button>
      </div>
    </aside>
  );
}
