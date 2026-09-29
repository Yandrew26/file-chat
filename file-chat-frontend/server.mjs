// Dev server for the File Chat UI. No dependencies (Node 18+).
//
//   AUTH_ID=... AUTH_SECRET=... node server.mjs
//
// - serves ./public
// - proxies /api/<path> to <GATEWAY_URL>/<path>, adding the gateway's auth headers
//   (authID + authorization = md5("authId=<id>&secretKey=<secret>")), so the secret
//   never reaches the browser. Streaming (SSE) responses are passed through unbuffered.
import http from 'node:http';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const PORT = Number(process.env.PORT || 5173);
// Loopback only: this proxy adds the gateway credentials to every request it forwards.
const HOST = process.env.HOST || '127.0.0.1';
const GATEWAY = new URL(process.env.GATEWAY_URL || 'http://localhost:8100');
const AUTH_ID = process.env.AUTH_ID || '';
const AUTH_SECRET = process.env.AUTH_SECRET || '';
const PUBLIC_DIR = path.join(path.dirname(fileURLToPath(import.meta.url)), 'public');

if (!AUTH_ID || !AUTH_SECRET) {
  console.warn('AUTH_ID / AUTH_SECRET not set: gateway requests will be rejected (401/403).');
}

const authorization = crypto
  .createHash('md5')
  .update(`authId=${AUTH_ID}&secretKey=${AUTH_SECRET}`)
  .digest('hex');

const TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.svg': 'image/svg+xml',
};

function serveStatic(req, res) {
  const urlPath = new URL(req.url, 'http://x').pathname;
  const file = path.normalize(path.join(PUBLIC_DIR, urlPath === '/' ? 'index.html' : urlPath));
  if (file !== PUBLIC_DIR && !file.startsWith(PUBLIC_DIR + path.sep)) {
    res.writeHead(403).end();
    return;
  }
  fs.readFile(file, (err, data) => {
    if (err) {
      res.writeHead(404, { 'content-type': 'text/plain' }).end('Not found');
      return;
    }
    res.writeHead(200, { 'content-type': TYPES[path.extname(file)] || 'application/octet-stream' }).end(data);
  });
}

function proxy(req, res) {
  const target = new URL(req.url.slice('/api'.length), GATEWAY);
  const headers = { ...req.headers, host: GATEWAY.host, authid: AUTH_ID, authorization };
  const upstream = http.request(
    { hostname: GATEWAY.hostname, port: GATEWAY.port, path: target.pathname + target.search, method: req.method, headers },
    (upRes) => {
      const outHeaders = { ...upRes.headers };
      if ((outHeaders['content-type'] || '').includes('text/event-stream')) {
        outHeaders['cache-control'] = 'no-cache';
        outHeaders['x-accel-buffering'] = 'no';
      }
      res.writeHead(upRes.statusCode, outHeaders);
      upRes.pipe(res);
    },
  );
  upstream.on('error', (e) => {
    if (!res.headersSent) res.writeHead(502, { 'content-type': 'text/plain' });
    res.end(`Gateway unreachable: ${e.message}`);
  });
  res.on('close', () => upstream.destroy());
  req.pipe(upstream);
}

http
  .createServer((req, res) => (req.url.startsWith('/api/') ? proxy(req, res) : serveStatic(req, res)))
  .listen(PORT, HOST, () => console.log(`File Chat UI on http://${HOST}:${PORT}  ->  ${GATEWAY.origin}`));
