import React, { useEffect, useState } from 'react';
import Sidebar from './components/Sidebar';
import ResolverPlayground from './pages/ResolverPlayground';
import CancellationStudio from './pages/CancellationStudio';
import { addTransaction, deleteTransaction, fetchDashboard, fetchMe, fetchTransactions, generateLetter, getPlaybook, login, register, requestCancellation, confirmCancellation, reportUnused, confirmSubscriptionActivity, runPipeline, importTransactions, seedSyntheticHistory, fetchSyntheticEvaluation } from './api';
import { ReceiptText, Plus, X, ArrowUpRight, ShieldCheck, LoaderCircle, Upload, Sparkles } from 'lucide-react';

const money = (amount, currency='INR') => new Intl.NumberFormat('en-IN',{style:'currency',currency,maximumFractionDigits:2}).format(Number(amount||0));
const frequencyLabel = (frequency) => ({DAILY:'Daily',WEEKLY:'Weekly',BIWEEKLY:'Every 2 weeks',MONTHLY:'Monthly',BIMONTHLY:'Every 2 months',QUARTERLY:'Every 3 months',SEMIANNUAL:'Twice a year',ANNUAL:'Yearly',IRREGULAR:'Variable schedule',UNKNOWN:'Unknown'}[frequency]||'Recurring');
function parseCsv(text){const rows=[];let row=[],cell='',quoted=false;for(let i=0;i<text.length;i++){const ch=text[i];if(ch==='"'&&quoted&&text[i+1]==='"'){cell+='"';i++;}else if(ch==='"')quoted=!quoted;else if(ch===','&&!quoted){row.push(cell.trim());cell='';}else if((ch==='\n'||ch==='\r')&&!quoted){if(ch==='\r'&&text[i+1]==='\n')i++;row.push(cell.trim());if(row.some(Boolean))rows.push(row);row=[];cell='';}else cell+=ch;}row.push(cell.trim());if(row.some(Boolean))rows.push(row);if(rows.length<2)return[];const heads=rows.shift().map(h=>h.toLowerCase().replaceAll(' ','_'));return rows.map(values=>Object.fromEntries(heads.map((h,i)=>[h,values[i]||''])));}

function AuthScreen({onAuthenticated}) {
  const [mode,setMode]=useState('login'); const [form,setForm]=useState({fullName:'',email:'',password:''}); const [busy,setBusy]=useState(false); const [error,setError]=useState('');
  const submit=async(e)=>{e.preventDefault();setBusy(true);setError('');try{const result=mode==='register'?await register(form):await login({email:form.email,password:form.password});onAuthenticated(result);}catch(err){setError(err.response?.data?.message||err.response?.data?.detail||err.message||'We could not sign you in. Please check your details and try again.');}finally{setBusy(false);}};
  return <main className="auth-page"><section className="auth-card"><div className="auth-brand"><div className="sidebar-logo-icon"><ShieldCheck size={22}/></div><div><strong>MandateShield</strong><span>Your payments, understood.</span></div></div><h1>{mode==='login'?'Welcome back':'Create your account'}</h1><p className="auth-intro">Keep your recurring payments organized and easy to review.</p><form onSubmit={submit} className="auth-form">
    {mode==='register'&&<label className="form-group"><span className="form-label">Your name</span><input className="form-control" required maxLength="80" autoComplete="name" value={form.fullName} onChange={e=>setForm({...form,fullName:e.target.value})} placeholder="Alex Morgan"/></label>}
    <label className="form-group"><span className="form-label">Email address</span><input className="form-control" required type="email" autoComplete="email" value={form.email} onChange={e=>setForm({...form,email:e.target.value})} placeholder="you@example.com"/></label>
    <label className="form-group"><span className="form-label">Password</span><input className="form-control" required minLength="8" type="password" autoComplete={mode==='login'?'current-password':'new-password'} value={form.password} onChange={e=>setForm({...form,password:e.target.value})} placeholder="At least 8 characters"/></label>
    {error&&<div className="alert alert-error">{error}</div>}<button className="btn btn-primary btn-lg" style={{width:'100%',justifyContent:'center'}} disabled={busy}>{busy?<LoaderCircle className="spinner" size={18}/>:null}{mode==='login'?'Sign in':'Create account'}</button>
  </form><p className="auth-switch">{mode==='login'?"New to MandateShield?":"Already have an account?"} <button onClick={()=>{setMode(mode==='login'?'register':'login');setError('');}}>{mode==='login'?'Create an account':'Sign in'}</button></p></section></main>;
}

function TransactionForm({onSave,onClose}) {
  const [today]=useState(()=>new Date().toISOString().slice(0,10));
  const [saving,setSaving]=useState(false);
  const [error,setError]=useState('');
  const [form,setForm]=useState({merchant:'',merchantName:'',shareMerchant:false,trialTransaction:false,trialEndDate:'',trialConversionAmount:'',amount:'',currency:'INR',category:'',paymentRail:'UPI',date:today});
  const submit=async(e)=>{
    e.preventDefault();
    setSaving(true);setError('');
    try{if(await onSave({...form,amount:Number(form.amount)}))onClose();else setError('Could not save this transaction. Please try again.');}
    catch{setError('Could not save this transaction. Please try again.');}
    finally{setSaving(false);}
  };
  return <div className="modal-backdrop" onMouseDown={e=>e.target===e.currentTarget&&onClose()}><section className="transaction-modal"><div className="modal-heading"><div><span className="eyebrow">YOUR ACTIVITY</span><h2>Add a transaction</h2><p>Enter a payment to keep your spending history up to date.</p></div><button className="btn btn-secondary btn-icon" onClick={onClose} aria-label="Close"><X size={18}/></button></div><form onSubmit={submit} className="transaction-form">
    <label className="form-group"><span className="form-label">Statement description</span><input autoFocus required maxLength="250" className="form-control" placeholder="Paste the bank line, e.g. ACH_INB_NFLX_IN_9812 or S_P_O_T_I_T_Y" value={form.merchant} onChange={e=>setForm({...form,merchant:e.target.value})}/><small className="field-hint">We keep this original text with your transaction and identify the merchant.</small></label>
    <label className="form-group"><span className="form-label">Recognized merchant (optional)</span><input maxLength="80" className="form-control" placeholder="Enter a name if you recognize it" value={form.merchantName} onChange={e=>setForm({...form,merchantName:e.target.value})}/></label>
    {form.merchantName.trim()&&<label className="check-row"><input type="checkbox" checked={form.shareMerchant} onChange={e=>setForm({...form,shareMerchant:e.target.checked})}/><span>Share this merchant match to help identify the same descriptor for other people. Only a one-way descriptor fingerprint and merchant name are shared; this transaction and account remain private.</span></label>}
    <div className="form-grid"><label className="form-group"><span className="form-label">Amount</span><input required min={form.trialTransaction?'0':'0.01'} step="0.01" type="number" className="form-control" placeholder="0.00" value={form.amount} onChange={e=>setForm({...form,amount:e.target.value})}/></label><label className="form-group"><span className="form-label">Currency</span><select className="form-control" value={form.currency} onChange={e=>setForm({...form,currency:e.target.value})}><option value="INR">INR — Rupee</option><option value="USD">USD — US Dollar</option><option value="EUR">EUR — Euro</option><option value="GBP">GBP — Pound</option></select></label></div>
    <div className="form-grid"><label className="form-group"><span className="form-label">Category (optional)</span><input maxLength="60" className="form-control" list="transaction-categories" placeholder="Choose or leave blank" value={form.category} onChange={e=>setForm({...form,category:e.target.value})}/><datalist id="transaction-categories"><option>Entertainment</option><option>Shopping</option><option>Utilities</option><option>Food & dining</option><option>Travel</option><option>Cloud services</option><option>Other</option></datalist></label><label className="form-group"><span className="form-label">Paid with</span><select className="form-control" value={form.paymentRail} onChange={e=>setForm({...form,paymentRail:e.target.value})}><option value="UPI">UPI</option><option value="CREDIT_CARD">Credit card</option><option value="DEBIT_CARD">Debit card</option><option value="NACH">Bank mandate</option><option value="NET_BANKING">Net banking</option><option value="CASH">Cash</option><option value="OTHER">Other</option></select></label></div>
    <label className="form-group"><span className="form-label">Date</span><input required type="date" className="form-control" value={form.date} onChange={e=>setForm({...form,date:e.target.value})}/></label><label className="check-row"><input type="checkbox" checked={form.trialTransaction} onChange={e=>setForm({...form,trialTransaction:e.target.checked})}/><span>This payment was a free trial or introductory charge</span></label>{form.trialTransaction&&<div className="form-grid"><label className="form-group"><span className="form-label">Trial ends</span><input type="date" className="form-control" value={form.trialEndDate} onChange={e=>setForm({...form,trialEndDate:e.target.value})}/></label><label className="form-group"><span className="form-label">Expected first charge</span><input min="0.01" step="0.01" type="number" className="form-control" value={form.trialConversionAmount} onChange={e=>setForm({...form,trialConversionAmount:e.target.value})}/></label></div>}{error&&<div className="alert alert-error" role="alert">{error}</div>}<div className="modal-actions"><button type="button" onClick={onClose} className="btn btn-secondary" disabled={saving}>Cancel</button><button className="btn btn-primary" disabled={saving}>{saving?'Saving…':'Save transaction'}</button></div>
  </form></section></div>;
}

export default function App(){
  const [user,setUser]=useState(null);const [checking,setChecking]=useState(Boolean(localStorage.getItem('mandateshield_token')));const [activeTab,setActiveTab]=useState('dashboard');const [transactions,setTransactions]=useState([]);const [dashboard,setDashboard]=useState(null);const [showForm,setShowForm]=useState(false);const [loading,setLoading]=useState(false);const [notice,setNotice]=useState('');const [cancelTarget,setCancelTarget]=useState(null);const [playbook,setPlaybook]=useState(null);const [letter,setLetter]=useState('');const [cancelForm,setCancelForm]=useState({bankName:'',mandateRef:''});const [cancelBusy,setCancelBusy]=useState(false);const [cancelRequested,setCancelRequested]=useState(false);const [demo,setDemo]=useState(null);const [demoBusy,setDemoBusy]=useState(false);
  const [savingSkeleton, setSavingSkeleton]=useState(null);

  function signOut(){localStorage.removeItem('mandateshield_token');setUser(null);setTransactions([]);setDashboard(null);}
  const load=async()=>{setLoading(true);try{const [tx,dash]=await Promise.all([fetchTransactions(),fetchDashboard()]);setTransactions(tx);setDashboard(dash);try{setDemo(await fetchSyntheticEvaluation());}catch{setDemo(null);}}catch(e){if(e.response?.status===401)signOut();else setNotice('We could not load your account data. Please try again.');}finally{setLoading(false);}};
  useEffect(()=>{if(!checking)return;fetchMe().then(setUser).catch(()=>{localStorage.removeItem('mandateshield_token');}).finally(()=>setChecking(false));},[checking]);
  useEffect(()=>{if(user){runPipeline().catch(()=>{}).finally(()=>load());}},[user]);
  const authenticate=(session)=>{localStorage.setItem('mandateshield_token',session.token);setUser(session.user);};
  
  const saveTransaction=async(payload)=>{
    // 1. Immediately display skeleton in the transaction list
    setSavingSkeleton({
      id: 'skeleton-pending',
      merchant: payload.merchantName || payload.merchant || 'Resolving merchant...',
      rawDescriptor: payload.merchant,
      amount: payload.amount,
      currency: payload.currency || 'INR',
      category: payload.category || 'Resolving...',
      paymentRail: payload.paymentRail || 'UPI',
      date: payload.date
    });
    try{
      const saved = await addTransaction(payload);
      setTransactions(prev=>[saved,...prev.filter(t=>t.id!==saved.id)]);
      setNotice(saved.merchant==='UNKNOWN'?'Transaction added. Checking merchant details in background.':`${saved.merchant} recorded successfully.`);
      // Run pipeline and update dashboard in background
      runPipeline().then(()=>fetchDashboard().then(setDashboard)).catch(()=>{});
      return true;
    }catch(err){
      setNotice(err.response?.data?.message||'Could not save this transaction.');
      return false;
    }finally{
      setSavingSkeleton(null);
    }
  };

  const removeTransaction=async(id)=>{try{await deleteTransaction(id);setTransactions(prev=>prev.filter(tx=>tx.id!==id));setNotice('Transaction deleted.');}catch{setNotice('Could not delete this transaction.');}};
  const openCancellation=async(sub)=>{setCancelTarget(sub);setLetter('');setCancelRequested(false);setCancelForm({bankName:'',mandateRef:''});try{setPlaybook(await getPlaybook(sub.paymentRail,sub.merchantName));}catch{setPlaybook(null);}};
  const createLetter=async(e)=>{e.preventDefault();setCancelBusy(true);try{const result=await generateLetter({subscriptionId:cancelTarget.id,...cancelForm});setLetter(result.letter);}catch(err){setNotice(err.response?.data?.message||'Could not prepare the notice.');}finally{setCancelBusy(false);}};
  const markCancelled=async()=>{setCancelBusy(true);try{if(!cancelRequested){await requestCancellation(cancelTarget.id);setCancelRequested(true);setNotice('Cancellation request recorded. Keep the confirmation and verify the next statement.');}else{await confirmCancellation(cancelTarget.id);setNotice('Cancellation confirmed and removed from your active commitments.');setCancelTarget(null);}await load();}catch(err){setNotice(err.response?.data?.message||'Could not update this recurring payment.');}finally{setCancelBusy(false);}};
  const markUnused=async(sub)=>{try{await reportUnused(sub.id);await load();setNotice(`Saved your report that you stopped using ${sub.merchantName}.`);}catch{setNotice('Could not save your usage report.');}};
  const markUsed=async(sub)=>{try{await confirmSubscriptionActivity(sub.id);await load();setNotice(`Usage confirmed for ${sub.merchantName}.`);}catch{setNotice('Could not save your usage confirmation.');}};
  const loadDemo=async()=>{setDemoBusy(true);try{setDemo(await seedSyntheticHistory());await load();setNotice('Synthetic sample history added to your account and reviewed.');}catch{setNotice('Could not load the synthetic sample history.');}finally{setDemoBusy(false);}};
  const importCsv=async(event)=>{const file=event.target.files?.[0];if(!file)return;try{const parsed=parseCsv(await file.text());const rows=parsed.map(r=>({rawDescriptor:r.raw_descriptor||r.descriptor||r.description||r.merchant,amount:r.amount,currency:r.currency||'INR',transactionDate:r.transaction_date||r.date,paymentRail:r.payment_rail||r.rail||'OTHER',merchantName:r.merchant_name||'',category:r.category||'',trialTransaction:/^(true|yes|1)$/i.test(r.trial_transaction||''),trialEndDate:r.trial_end_date||'',trialConversionAmount:r.trial_conversion_amount||''})).filter(r=>r.rawDescriptor&&r.amount&&r.transactionDate);if(!rows.length)throw new Error('Use the CSV template with description, amount, date, currency, and payment_rail columns.');await importTransactions(rows);await runPipeline();await load();setNotice(`${rows.length} transaction${rows.length===1?'':'s'} imported and reviewed.`);}catch(err){setNotice(err.response?.data?.message||err.message||'The CSV could not be imported.');}finally{event.target.value='';}};

  if(checking)return <div className="auth-page"><div className="spinner"/></div>;
  if(!user)return <AuthScreen onAuthenticated={authenticate}/>;

  const pageTitle=activeTab==='dashboard'?'Your overview':activeTab==='transactions'?'Transactions':activeTab==='resolver'?'Descriptor Studio':'Cancellation Studio';
  const total=transactions.reduce((sum,tx)=>sum+Number(tx.amount||0),0);
  const recent=transactions.slice(0,5);

  return <div className="app-shell">
    <Sidebar activeTab={activeTab} setActiveTab={setActiveTab} user={user} onLogout={signOut}/>
    <main className="main-content">
      <header className="page-header">
        <div>
          <h1 className="page-title">{pageTitle}</h1>
          <p className="page-subtitle">
            {activeTab==='dashboard'?`Good to see you, ${user.name}. Here’s your recurring payment intelligence.`
              :activeTab==='transactions'?'Your complete personal statement and payment history.'
              :activeTab==='resolver'?'Test raw descriptor resolution across Tier 1 (Regex), Tier 2 (Vector/Heuristic), and Tier 3 (LLM).'
              :'Cancellation request drafts and step-by-step payment guidance.'}
          </p>
        </div>
        <div className="header-actions">
          <label className="btn btn-secondary"><Upload size={16}/>Import CSV<input type="file" accept=".csv,text/csv" hidden onChange={importCsv}/></label>
          <a className="text-button" download="mandateshield-transactions-template.csv" href={`data:text/csv;charset=utf-8,${encodeURIComponent('description,amount,date,currency,payment_rail,merchant_name,category,trial_transaction\nNETFLIX SUBSCRIPTION,499.00,2026-09-01,INR,CREDIT_CARD,Netflix,Entertainment,false')}`}>CSV template</a>
          <button className="btn btn-primary" onClick={()=>setShowForm(true)}><Plus size={17}/>Add transaction</button>
        </div>
      </header>

      <div className="page-content animate-fade-up">
        {notice&&<div className="alert alert-info" style={{marginBottom:18}}>{notice}<button onClick={()=>setNotice('')} style={{marginLeft:'auto',color:'inherit',background:'none',border:0,cursor:'pointer'}}>Dismiss</button></div>}
        {activeTab==='dashboard'&&(dashboard?.trialConversionAlerts||[]).map((alert,index)=><div className="alert alert-error" style={{marginBottom:12}} key={`${alert.merchantName}-${index}`}><strong>Trial converts within 48 hours:</strong> {alert.merchantName||'A trial'} ends {new Date(alert.trialEndDate).toLocaleString()} {alert.expectedAmount?`and may charge ${money(alert.expectedAmount)}.`:'— review the renewal amount and cancel with the merchant if you do not want to continue.'}</div>)}

        {activeTab==='resolver' ? (
          <ResolverPlayground activeUser={user} isDemoMode={false} />
        ) : activeTab==='playbooks' ? (
          <CancellationStudio activeUser={user} isDemoMode={false} />
        ) : activeTab==='dashboard' ? (
          <>
            <div className="welcome-banner">
              <div>
                <span className="eyebrow">YOUR RECURRING COMMITMENTS</span>
                <h2>The yearly cost of staying subscribed</h2>
                <p>Based on recurring patterns in your statement history. Review the evidence before acting.</p>
              </div>
              <ReceiptText size={46} strokeWidth={1.3}/>
            </div>
            <div className="kpi-grid">
              <div className="kpi-card"><span className="kpi-label">Estimated annual recurring spend</span><span className="kpi-value">{money(dashboard?.estimatedAnnualRecurringSpend)}</span><span className="kpi-sub">Projected from detected payment patterns</span></div>
              <div className="kpi-card"><span className="kpi-label">Potential annual savings</span><span className="kpi-value">{money(dashboard?.potentialAnnualSavings)}</span><span className="kpi-sub">Estimate from flagged payments, not guaranteed savings</span></div>
              <div className="kpi-card"><span className="kpi-label">Recurring commitments</span><span className="kpi-value">{(dashboard?.activeSubscriptions||[]).length+(dashboard?.tentativeSubscriptions||[]).length}</span><span className="kpi-sub">{dashboard?.tentativeSubscriptions?.length||0} need your review</span></div>
              <div className="kpi-card"><span className="kpi-label">Transactions reviewed</span><span className="kpi-value">{transactions.length}</span><span className="kpi-sub">{money(total)} recorded in your history</span></div>
            </div>
            <section className="transaction-panel synthetic-panel">
              <div>
                <span className="eyebrow">JUDGING DATASET</span>
                <h2>See what MandateShield catches</h2>
                <p>Load a labeled, synthetic payment history with annual renewal, variable billing, a price increase, a trial conversion, and a paused-and-resumed payment. It stays in your account.</p>
                {demo&&demo.transactionsEvaluated>0&&<div className="demo-results">
                  <strong>Merchant labels {demo.merchantLabelsCorrect}/{demo.merchantLabelsTotal}</strong>
                  <span>Recurring groups {demo.recurringGroupsDetected}/{demo.recurringGroupsTotal}</span>
                  <span>Price increase · {demo.priceIncreaseDetected?'found':'missed'}</span>
                  <span>Trial conversion · {demo.trialConversionDetected?'found':'missed'}</span>
                  <span>Annual renewal · {demo.annualRenewalDetected?'found':'missed'}</span>
                  <span>Resumed billing · {demo.resumedBillingDetected?'found':'missed'}</span>
                </div>}
              </div>
              <button className="btn btn-primary" disabled={demoBusy} onClick={loadDemo}><Sparkles size={16}/>{demoBusy?'Loading sample…':demo?.transactionsEvaluated?'Run sample review':'Load synthetic sample'}</button>
            </section>
            <section className="transaction-panel">
              <div className="panel-heading"><div><h2>Recent transactions</h2><p>Your latest recorded activity</p></div><button className="text-button" onClick={()=>setActiveTab('transactions')}>View all <ArrowUpRight size={15}/></button></div>
              <TransactionList items={recent} loading={loading} onDelete={removeTransaction} savingSkeleton={savingSkeleton}/>
            </section>
            <section className="transaction-panel">
              <div className="panel-heading"><div><h2>Recurring payments</h2><p>Review repeated charges, price changes and cancellation options.</p></div><button className="btn btn-secondary btn-sm" onClick={async()=>{setLoading(true);try{await runPipeline();await load();setNotice('Payment review is up to date.');}catch{setNotice('Could not review payments right now.');}finally{setLoading(false);}}}>Review payments</button></div>
              {(dashboard?.detectedAnomalies||[]).length>0&&<div className="anomaly-list">{dashboard.detectedAnomalies.map(a=><div className="anomaly-row" key={a.id}><span className={`severity-dot severity-${a.severity}`}/><div><strong>{a.type.replaceAll('_',' ')}</strong><span>{a.description}</span></div><strong>{a.financialImpact?money(a.financialImpact):''}</strong></div>)}</div>}
              {((dashboard?.activeSubscriptions||[]).length+(dashboard?.tentativeSubscriptions||[]).length)===0?<div className="empty-state"><div className="empty-icon"><ReceiptText size={24}/></div><strong>No recurring payments spotted yet</strong><span>Add a few months of charges from the same service, then review your payments.</span></div>:<div className="recurring-list">{dashboard.activeSubscriptions.map(sub=><div className="recurring-row" key={sub.id}><div className="merchant-icon">{sub.merchantName?.slice(0,1)}</div><div className="transaction-primary"><strong>{sub.merchantName}</strong><span>{frequencyLabel(sub.frequency)} · {sub.paymentRail?.replaceAll('_',' ')} · {sub.detectionConfidence==null?'Confidence pending':`${Math.round(sub.detectionConfidence*100)}% confidence`}</span><small>{sub.detectionEvidence}</small></div><div className="commitment-cost"><strong className="transaction-amount">{money(sub.currentAmount)}</strong><small>~{money(sub.estimatedAnnualAmount)}/year</small></div><button className="btn btn-secondary btn-sm" onClick={()=>markUsed(sub)}>Confirm used recently</button><button className="btn btn-secondary btn-sm" onClick={()=>markUnused(sub)}>I stopped using it</button><button className="btn btn-secondary btn-sm" onClick={()=>openCancellation(sub)}>Cancellation steps</button></div>)}</div>}
              {(dashboard?.tentativeSubscriptions||[]).length>0&&<div className="tentative-list"><h3>Possible recurring payments · check these</h3>{dashboard.tentativeSubscriptions.map(sub=><article className="tentative-row" key={sub.id}><div><strong>{sub.merchantName}</strong><span>{sub.detectionEvidence}</span><small>{sub.detectionConfidence==null?'Confidence pending':`${Math.round(sub.detectionConfidence*100)}% pattern confidence`} · {sub.paymentRail?.replaceAll('_',' ')}</small></div><strong>~{money(sub.estimatedAnnualAmount)}/year</strong></article>)}</div>}
            </section>
          </>
        ) : (
          <section className="transaction-panel">
            <div className="panel-heading"><div><h2>All transactions</h2><p>{transactions.length?`${transactions.length} transaction${transactions.length===1?'':'s'} recorded`:'Add payments manually to start building your history.'}</p></div></div>
            <TransactionList items={transactions} loading={loading} onDelete={removeTransaction} savingSkeleton={savingSkeleton}/>
          </section>
        )}
      </div>
    </main>

    {showForm&&<TransactionForm onSave={saveTransaction} onClose={()=>setShowForm(false)}/>}
    {cancelTarget&&<div className="modal-backdrop"><section className="transaction-modal"><div className="modal-heading"><div><span className="eyebrow">{cancelTarget.merchantName}</span><h2>Cancellation options</h2></div><button className="btn btn-secondary btn-icon" onClick={()=>setCancelTarget(null)}><X size={18}/></button></div><div className="playbook-box"><strong>Steps through {cancelTarget.paymentRail?.replaceAll('_',' ')}</strong>{playbook?<><h3>{playbook.route}</h3><strong>Payment provider</strong><ol>{playbook.paymentSteps?.map((step,i)=><li key={`p${i}`}>{step}</li>)}</ol><strong>Merchant</strong><ol>{playbook.merchantSteps?.map((step,i)=><li key={`m${i}`}>{step}</li>)}</ol><strong>If charges continue</strong><ol>{playbook.ifItContinues?.map((step,i)=><li key={`e${i}`}>{step}</li>)}</ol><a href={playbook.sourceUrl} target="_blank" rel="noreferrer">Official guidance · checked {playbook.reviewedOn}</a></>:<p>Check your bank or provider’s current instructions before proceeding.</p>}<small>MandateShield cannot cancel a payment for you. Keep the provider’s confirmation and check the next statement.</small></div>{!letter?<form onSubmit={createLetter} className="transaction-form"><label className="form-group"><span className="form-label">Bank or payment provider</span><input required className="form-control" value={cancelForm.bankName} onChange={e=>setCancelForm({...cancelForm,bankName:e.target.value})} placeholder="e.g. HDFC Bank"/></label><label className="form-group"><span className="form-label">Mandate reference or descriptor</span><input required className="form-control" value={cancelForm.mandateRef} onChange={e=>setCancelForm({...cancelForm,mandateRef:e.target.value})} placeholder="Reference shown by your bank"/></label><div className="modal-actions"><button type="button" className="btn btn-secondary" onClick={()=>setCancelTarget(null)}>Close</button><button className="btn btn-primary" disabled={cancelBusy}>{cancelBusy?'Preparing…':'Prepare cancellation notice'}</button></div></form>:<><pre className="letter-preview">{letter}</pre><div className="modal-actions"><button className="btn btn-secondary" onClick={()=>navigator.clipboard.writeText(letter)}>Copy notice</button><button className="btn btn-primary" disabled={cancelBusy} onClick={markCancelled}>{cancelBusy?'Saving…':cancelRequested?'I have confirmation it stopped':'I submitted the cancellation request'}</button></div></>}</section></div>}
  </div>;
}

function TransactionList({items,loading,onDelete,savingSkeleton}){
  if(loading)return <div className="loading-center"><div className="spinner"/><p>Loading your transactions…</p></div>;
  if(!items.length&&!savingSkeleton)return <div className="empty-state"><div className="empty-icon"><ReceiptText size={24}/></div><strong>No transactions yet</strong><span>Add your first payment to get started.</span></div>;
  return (
    <div className="transaction-list">
      {savingSkeleton&&(
        <article className="transaction-row skeleton-row animate-shimmer" key="skeleton-pending">
          <div className="merchant-icon skeleton-icon-placeholder">
            <LoaderCircle className="spinner" size={16} />
          </div>
          <div className="transaction-primary">
            <div className="skeleton-bar skeleton-title skeleton-pulse" />
            <div className="skeleton-bar skeleton-subtitle skeleton-pulse" />
          </div>
          <div className="skeleton-bar skeleton-date skeleton-pulse" />
          <div className="skeleton-bar skeleton-amount skeleton-pulse" />
        </article>
      )}
      {items.map(tx=>(
        <article className="transaction-row" key={tx.id}>
          <div className="merchant-icon">{(tx.merchant||'?').slice(0,1).toUpperCase()}</div>
          <div className="transaction-primary">
            <strong>{tx.merchant}</strong>
            <span>{tx.rawDescriptor&&tx.rawDescriptor!==tx.merchant?`${tx.rawDescriptor} · `:''}{tx.category||'Other'} · {tx.paymentRail?.replaceAll('_',' ')||'Payment'}{tx.confidenceScore!=null?` · ${Math.round(tx.confidenceScore*100)}% match`:''}{tx.resolutionTier?` · ${tx.resolutionTier}`:''}</span>
          </div>
          <div className="transaction-date">{tx.date?new Date(`${tx.date}T12:00:00`).toLocaleDateString('en-IN',{day:'numeric',month:'short',year:'numeric'}):''}</div>
          <strong className="transaction-amount">{money(tx.amount,tx.currency)}</strong>
          <button className="delete-transaction" onClick={()=>onDelete(tx.id)} aria-label={`Delete ${tx.merchant}`} title="Delete transaction"><X size={16}/></button>
        </article>
      ))}
    </div>
  );
}
