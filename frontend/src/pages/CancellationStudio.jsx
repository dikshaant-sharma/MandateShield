import React, { useCallback, useState, useEffect } from 'react';
import { 
  FileText, 
  Copy, 
  Check, 
  Download, 
  Printer, 
  BookOpen
} from 'lucide-react';
import { getPlaybook, generateLetter } from '../api';
import { SAMPLE_REVOCATION_PRESETS } from '../data/sampleData';

export default function CancellationStudio({ activeUser, isDemoMode, initialData }) {
  // Playbook state
  const [selectedRail, setSelectedRail] = useState(initialData?.rail || 'UPI_AUTOPAY');
  const [playbookContent, setPlaybookContent] = useState('');

  // Notice generator form state
  const [userName, setUserName] = useState(initialData?.userName || activeUser.name);
  const [bankName, setBankName] = useState(initialData?.bankName || 'HDFC Bank');
  const [merchantName, setMerchantName] = useState(initialData?.merchantName || 'Adobe Creative Cloud');
  const [mandateRef, setMandateRef] = useState(initialData?.mandateRef || 'MND-HDFC-99281-ADOBE');
  
  const [generatedLetter, setGeneratedLetter] = useState('');
  const [letterError, setLetterError] = useState('');
  const [generating, setGenerating] = useState(false);
  const [copied, setCopied] = useState(false);

  const handleGenerateLetter = useCallback(async () => {
    setGenerating(true);
    setLetterError('');
    try {
      if (!isDemoMode) {
        const res = await generateLetter({
          bankName: bankName || 'Bank',
          merchantName: merchantName || 'Merchant',
          mandateRef: mandateRef || 'REF-DEFAULT'
        });
        setGeneratedLetter(res.letter);
      } else {
        await new Promise(r => setTimeout(r, 150));
        const today = new Date().toISOString().split('T')[0];
        setGeneratedLetter(`To,
The Branch Manager, ${bankName || 'Bank'}

SUBJECT: NOTICE OF REVOCATION AND CANCELLATION OF MANDATE FOR ${merchantName.toUpperCase()}

Dear Sir/Madam,

I, ${userName}, request revocation of the recurring payment mandate
registered under Reference/Descriptor: ${mandateRef} in favor of ${merchantName}.

Please confirm the steps required to revoke this mandate and the effective date. This draft
is a request for review and does not itself cancel the mandate or guarantee a legal outcome.

Date: ${today}
Sincerely,
${userName}`);
      }
    } catch (err) {
      setLetterError(err.response?.data?.message || 'Could not generate the notice. Check the details and try again.');
    } finally {
      setGenerating(false);
    }
  }, [bankName, isDemoMode, mandateRef, merchantName, userName]);

  useEffect(() => {
    if (isDemoMode) return;
    getPlaybook(selectedRail, merchantName).then(res => setPlaybookContent([
      `${res.route} cancellation steps:`,
      ...res.paymentSteps.map((step, index) => `${index + 1}. ${step}`),
      ...(res.merchantSteps?.length ? ['', 'Merchant steps:', ...res.merchantSteps.map(step => `• ${step}`)] : []),
      ...(res.ifItContinues?.length ? ['', 'If billing continues:', ...res.ifItContinues.map(step => `• ${step}`)] : []),
    ].join('\n'))).catch(() => setPlaybookContent("1. Open your payment application.\n2. Go to recurring payments.\n3. Choose revoke consent."));
  }, [isDemoMode, merchantName, selectedRail]);

  const copyToClipboard = () => {
    if (!generatedLetter) return;
    navigator.clipboard.writeText(generatedLetter);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const downloadText = () => {
    if (!generatedLetter) return;
    const element = document.createElement("a");
    const file = new Blob([generatedLetter], { type: 'text/plain' });
    element.href = URL.createObjectURL(file);
    element.download = `MandateRevocation_${merchantName.replace(/\s+/g, '_')}.txt`;
    document.body.appendChild(element);
    element.click();
    document.body.removeChild(element);
  };

  const printNotice = () => {
    const printWindow = window.open('', '_blank');
    printWindow.document.write(`
      <html>
        <head>
          <title>Cancellation Request Draft</title>
          <style>
            body { font-family: monospace; white-space: pre-wrap; padding: 40px; line-height: 1.8; }
          </style>
        </head>
        <body>${generatedLetter}</body>
      </html>
    `);
    printWindow.document.close();
    printWindow.print();
  };

  const applyPreset = (preset) => {
    setUserName(preset.userName);
    setBankName(preset.bankName);
    setMerchantName(preset.merchantName);
    setMandateRef(preset.mandateRef);
    setSelectedRail(preset.rail);
  };

  return (
    <div className="page-content animate-fade-up">
      {/* Banner */}
      <div className="hero-banner">
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
          <span className="badge badge-primary">Payment rail guidance</span>
          <span style={{ fontSize: '12px', color: 'var(--color-text-muted)' }}>
            Check the latest steps with your bank or payment provider
          </span>
        </div>
        <h2 style={{ fontSize: '22px', fontWeight: 700, color: '#ffffff' }}>
          Cancellation & Revocation Studio
        </h2>
        <p style={{ color: 'var(--color-text-secondary)', fontSize: '13px', maxWidth: '650px', marginTop: '4px' }}>
          Draft a cancellation request and review payment rail guidance. Confirm requirements and completion with your bank or provider.
        </p>
      </div>

      {/* Payment Rail Playbook Section */}
      <div className="card">
        <div className="card-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <BookOpen size={18} color="var(--color-primary-light)" />
            <span className="card-title">Payment Rail Cancellation Protocol</span>
          </div>

          <div className="tabs">
            {[
              { id: 'UPI_AUTOPAY', label: 'UPI AutoPay' },
              { id: 'NACH', label: 'NACH e-Mandate' },
              { id: 'CREDIT_CARD', label: 'Credit Card SI' },
            ].map(rail => (
              <button
                key={rail.id}
                onClick={() => setSelectedRail(rail.id)}
                className={`tab-btn ${selectedRail === rail.id ? 'active' : ''}`}
              >
                {rail.label}
              </button>
            ))}
          </div>
        </div>

        <div className="card-body">
          {!playbookContent ? (
            <div className="loading-center">
              <div className="spinner"></div>
              <p>Loading rail protocol...</p>
            </div>
          ) : (
            <div style={{
              background: 'var(--color-bg-input)',
              border: '1px solid var(--color-border)',
              borderRadius: 'var(--radius-md)',
              padding: '16px 20px',
              whiteSpace: 'pre-line',
              lineHeight: '1.8',
              color: 'var(--color-text-primary)',
              fontSize: '13px'
            }}>
              {playbookContent}
            </div>
          )}
        </div>
      </div>

      {/* Revocation Notice Generator Form & Output */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(420px, 1fr))', gap: '24px' }}>
        {/* Form */}
        <div className="card">
          <div className="card-header">
            <span className="card-title">Cancellation Request Details</span>
            <span className="badge badge-primary">Editable draft</span>
          </div>
          <div className="card-body" style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            <div className="form-group">
              <label className="form-label">Full Account Holder Name</label>
              <input
                type="text"
                className="form-control"
                value={userName}
                onChange={(e) => setUserName(e.target.value)}
                placeholder="e.g. Dikshaant Sharma"
              />
            </div>

            <div className="form-group">
              <label className="form-label">Bank or Financial Institution</label>
              <input
                type="text"
                className="form-control"
                value={bankName}
                onChange={(e) => setBankName(e.target.value)}
                placeholder="e.g. HDFC Bank, ICICI Bank, SBI"
              />
            </div>

            <div className="form-group">
              <label className="form-label">Merchant / Billing Entity</label>
              <input
                type="text"
                className="form-control"
                value={merchantName}
                onChange={(e) => setMerchantName(e.target.value)}
                placeholder="e.g. Netflix India, Adobe Creative Cloud"
              />
            </div>

            <div className="form-group">
              <label className="form-label">Mandate / URN Reference Number</label>
              <input
                type="text"
                className="form-control form-control-mono"
                value={mandateRef}
                onChange={(e) => setMandateRef(e.target.value)}
                placeholder="e.g. URN-2024-99812-NACH"
              />
            </div>

            <button
              onClick={handleGenerateLetter}
              disabled={generating}
              className="btn btn-primary"
              style={{ width: '100%', justifyContent: 'center' }}
            >
              <FileText size={16} className={generating ? 'spinner' : ''} />
              <span>{generating ? 'Drafting request...' : 'Generate cancellation request'}</span>
            </button>

            {/* Presets */}
            <div style={{ marginTop: '8px' }}>
              <label className="form-label" style={{ marginBottom: '8px', display: 'block' }}>
                Quick Test Scenarios (Click to Fill)
              </label>
              <div className="chip-group">
                {SAMPLE_REVOCATION_PRESETS.map((p, idx) => (
                  <button
                    key={idx}
                    onClick={() => applyPreset(p)}
                    className="chip"
                  >
                    <span>{p.merchantName} ({p.rail})</span>
                  </button>
                ))}
              </div>
            </div>
          </div>
        </div>

        {/* Legal Notice Preview & Actions */}
        <div className="card">
          <div className="card-header">
            <span className="card-title">Request Draft Preview</span>
            <div style={{ display: 'flex', gap: '8px' }}>
              <button
                onClick={copyToClipboard}
                className="btn btn-secondary btn-sm"
                title="Copy to clipboard"
              >
                {copied ? <Check size={14} color="var(--color-success)" /> : <Copy size={14} />}
                <span>{copied ? 'Copied!' : 'Copy'}</span>
              </button>

              <button
                onClick={downloadText}
                className="btn btn-secondary btn-sm"
                title="Download as .txt"
              >
                <Download size={14} />
                <span>Download</span>
              </button>

              <button
                onClick={printNotice}
                className="btn btn-secondary btn-sm"
                title="Print or Save as PDF"
              >
                <Printer size={14} />
                <span>Print</span>
              </button>
            </div>
          </div>

          <div className="card-body">
            {letterError && <div className="alert alert-error" role="alert">{letterError}</div>}
            {generatedLetter ? (
              <div className="letter-box">
                {generatedLetter}
              </div>
            ) : (
              <div className="empty-state">
                <FileText size={32} color="var(--color-text-muted)" />
                <p>Fill in parameters on the left to generate the revocation notice.</p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
