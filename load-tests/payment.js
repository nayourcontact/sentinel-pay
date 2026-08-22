import http from 'k6/http';
import { check, sleep } from 'k6';
export const options = { stages: [{duration:'20s',target:25},{duration:'40s',target:100},{duration:'20s',target:0}], thresholds: { http_req_failed:['rate<0.01'], http_req_duration:['p(95)<500'] } };
export default function(){
  const id = `${__VU}-${__ITER}-${Date.now()}`;
  const r=http.post('http://localhost:8080/api/payments', JSON.stringify({amount:'49.90',currency:'EUR'}), {headers:{'Content-Type':'application/json','Idempotency-Key':id}});
  check(r,{'created or replayed':x=>x.status===201}); sleep(0.1);
}
