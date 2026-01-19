import { Router } from 'express';

export function healthRouter({ fanout }) {
  const r = Router();
  r.get('/healthz', (_req, res) => res.status(200).send('ok'));
  r.get('/readyz', (_req, res) => {
    if (!fanout.isHealthy()) return res.status(503).send('redis not ready');
    res.status(200).send('ready');
  });
  return r;
}
