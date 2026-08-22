import React, {useState} from 'react';
import {createRoot} from 'react-dom/client';
import './styles.css';

type Payment = {id:string; merchantReference:string; amount:string; currency:string; status:string; providerReference?:string; failureReason?:string; createdAt:string; updatedAt:string};
type ChatResponse = {requestId:string; answer:string; model:string; architecture:string; timestamp:string};

function App(){
  const [id,setId]=useState('');
  const [payment,setPayment]=useState<Payment|null>(null);
  const [error,setError]=useState('');
  const [amount,setAmount]=useState('49.90');
  const [currency,setCurrency]=useState('EUR');
  const [question,setQuestion]=useState('Investigate the current payment platform health. Use live tools before drawing conclusions.');
  const [answer,setAnswer]=useState('');
  const [asking,setAsking]=useState(false);

  async function load(paymentId=id){
    setError('');
    const r=await fetch(`/api/payments/${paymentId}`);
    if(!r.ok){setError(`Lookup failed: ${r.status}`); return;}
    setPayment(await r.json());
  }

  async function createPayment(){
    setError('');
    const key=`sentinelpay-${crypto.randomUUID()}`;
    const r=await fetch('/api/payments',{method:'POST',headers:{'Content-Type':'application/json','Idempotency-Key':key},body:JSON.stringify({amount,currency})});
    if(!r.ok){setError(`Create failed: ${r.status}`);return;}
    const p:Payment=await r.json();
    setPayment(p); setId(p.id); setTimeout(()=>load(p.id),1000);
  }

  async function askAgent(){
    setAsking(true); setAnswer('');
    try {
      const r=await fetch('/agent-api/chat',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({message:question})});
      if(!r.ok){setAnswer(`Agent request failed: ${r.status}`); return;}
      const data:ChatResponse=await r.json();
      setAnswer(data.answer || 'No answer returned.');
    } finally { setAsking(false); }
  }

  return <main>
    <header>
      <div><span className="eyebrow">SENTINELPAY</span><h1>AI Payment Operations Platform</h1><p>Event-driven payments, transactional outbox, MCP-governed live data and RAG-assisted incident response.</p></div>
      <span className="badge">ENTERPRISE DEMO</span>
    </header>

    <section className="grid">
      <article>
        <h2>Create payment</h2>
        <label>Amount<input value={amount} onChange={e=>setAmount(e.target.value)}/></label>
        <label>Currency<input value={currency} onChange={e=>setCurrency(e.target.value.toUpperCase())} maxLength={3}/></label>
        <button onClick={createPayment}>Authorize payment</button>
      </article>
      <article>
        <h2>Inspect payment</h2>
        <label>Payment ID<input value={id} onChange={e=>setId(e.target.value)} placeholder="UUID"/></label>
        <button className="secondary" onClick={()=>load()}>Refresh state</button>
        {error&&<p className="error">{error}</p>}
      </article>
    </section>

    {payment&&<section className="payment">
      <div className="paymentTop"><div><span className="eyebrow">PAYMENT</span><h2>{payment.amount} {payment.currency}</h2></div><span className={`status ${payment.status.toLowerCase()}`}>{payment.status}</span></div>
      <dl><dt>ID</dt><dd>{payment.id}</dd><dt>Idempotency reference</dt><dd>{payment.merchantReference}</dd><dt>Provider reference</dt><dd>{payment.providerReference||'pending'}</dd><dt>Failure</dt><dd>{payment.failureReason||'—'}</dd><dt>Updated</dt><dd>{new Date(payment.updatedAt).toLocaleString()}</dd></dl>
    </section>}

    <section className="agentPanel">
      <div><span className="eyebrow">AI OPERATIONS</span><h2>Incident investigator</h2><p>The agent uses MCP for live payment evidence and PGVector RAG for runbooks. Its operational tool surface is read-only by design.</p></div>
      <textarea value={question} onChange={e=>setQuestion(e.target.value)} rows={4}/>
      <button onClick={askAgent} disabled={asking}>{asking?'Investigating…':'Ask SentinelPay Agent'}</button>
      {answer&&<pre className="answer">{answer}</pre>}
    </section>

    <footer>Java 21 · Spring Boot · Spring AI · PostgreSQL · PGVector · Kafka · MCP · Ollama · Prometheus · Grafana</footer>
  </main>
}

createRoot(document.getElementById('root')!).render(<React.StrictMode><App/></React.StrictMode>);
