// Mock of the FileChat gateway (file-chat-gateway-openapi, port 8100) so the UI can run
// without MySQL / Elasticsearch / Neo4j / a DashScope key.
//
// It mirrors the real routes, auth headers, history DTOs and the SSE chunk format
// (`data: {"<node>": "<chunk>"}`). Answers are canned and only cover the bundled sample
// report; uploaded PDFs are accepted but never read.
import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const PORT = Number(process.env.PORT ?? 8100);
const STREAM_DELAY_MS = Number(process.env.STREAM_DELAY_MS ?? 35);
const DOC = 'q3-research-report.pdf';
const DEMO_USER = '12345678';

const MIME = { '.html': 'text/html', '.css': 'text/css', '.js': 'text/javascript',
  '.svg': 'image/svg+xml', '.pdf': 'application/pdf', '.png': 'image/png' };

// ---- canned knowledge about the sample report --------------------------------------
const cite = `[Doc: ${DOC}]`;
const KB = [
  { keys: ['summar', 'overview', 'about', 'main points', 'tl;dr', 'report'],
    answer: `Happy to help! The report covers Northwind Analytics' third quarter of 2026. The headline points are:\n\n- **Revenue** reached $4.2M, up 18% quarter over quarter ${cite}\n- **Churn** fell from 6.1% to 4.7% after the onboarding redesign ${cite}\n- **Next up:** the EU region launches in Q4 ${cite}\n\nWant me to dig into any of these?` },
  { keys: ['who', 'author', 'wrote', 'written'],
    answer: `The sources list **Dr. Maya Chen** as lead author, with **Luis Ortega** as co-author ${cite}.\n\nYou may also like other pieces by the same authors:\n\n- *Q2 2026 Retention Study* — Dr. Maya Chen\n- *Pricing Experiments, Year One* — Dr. Maya Chen & Luis Ortega` },
  { keys: ['revenue', 'sales', 'income', 'growth', 'money'],
    answer: `Revenue for the quarter was **$4.2M**, an increase of 18% over Q2. Roughly two thirds of the growth came from existing customers expanding their plans ${cite}.` },
  { keys: ['churn', 'retention', 'cancel'],
    answer: `Monthly churn dropped from **6.1% to 4.7%**. The sources credit the redesigned onboarding flow and proactive check-ins for accounts in their first 60 days ${cite}.` },
  { keys: ['risk', 'threat', 'concern', 'problem', 'challenge'],
    answer: `The documents flag three main risks:\n\n- **Supply chain delays** for hardware add-ons ${cite}\n- **Currency exposure** ahead of the EU launch ${cite}\n- **Key-person dependency** on the analytics team ${cite}` },
  { keys: ['eu', 'europe', 'roadmap', 'launch', 'next', 'plan', 'q4'],
    answer: `The roadmap puts the **EU region launch in Q4 2026**, followed by SSO and audit logging for enterprise plans in early 2027 ${cite}.` },
];
const FALLBACK = `Based on the available documents, I cannot find specific information about that. Try asking about revenue, churn, risks, the roadmap or the authors.`;

function answerFor(message) {
  const q = message.toLowerCase();
  let best = null, bestScore = 0;
  for (const entry of KB) {
    const score = entry.keys.filter((k) => q.includes(k)).length;
    if (score > bestScore) { best = entry; bestScore = score; }
  }
  return best ? best.answer : FALLBACK;
}

// ---- in-memory history (seeded so the sidebar is not empty) ------------------------
const fmt = (d) => d.toISOString().replace('T', ' ').slice(0, 19);
const ago = (h) => fmt(new Date(Date.now() - h * 3600_000));
const history = []; // ChatHistoryDTO rows
let nextId = 1;
function addRow(conversationId, type, content, createdDate = fmt(new Date())) {
  history.push({ id: nextId++, userName: 'Andrew', userId: DEMO_USER, conversationId,
    traceId: randomUUID().replaceAll('-', ''), content, type, createdDate });
}
const seed = (suffix, hoursAgo, q, a) => {
  const id = `${DEMO_USER}_${suffix}`;
  addRow(id, 'USER', q, ago(hoursAgo));
  addRow(id, 'ASSISTANT', a, ago(hoursAgo - 0.01));
};
seed('seed0000000000000000000000000001', 26, 'What was the revenue growth this quarter?', answerFor('revenue'));
seed('seed0000000000000000000000000002', 50, 'What are the main risks?', answerFor('risk'));

// ---- helpers -----------------------------------------------------------------------
const json = (res, status, body) => {
  res.writeHead(status, { 'content-type': 'application/json', 'access-control-allow-origin': '*' });
  res.end(JSON.stringify(body, null, 2));
};
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// Same rules as AuthGatewayFilter: missing headers -> 401, rejected token -> 403.
function authorize(req, res) {
  const token = req.headers['authorization'];
  const authId = req.headers['authid'];
  if (!token || !authId) { res.writeHead(401).end(); return false; }
  if (token.replace(/^Bearer /i, '') === 'invalid') { res.writeHead(403).end(); return false; }
  return true;
}

async function streamAnswer(req, res, url) {
  const message = url.searchParams.get('message') ?? '';
  const conversationId = url.searchParams.get('conversationId') ?? '';
  const answer = answerFor(message);
  const traceId = randomUUID().replaceAll('-', '');
  addRow(conversationId, 'USER', message);

  res.writeHead(200, { 'content-type': 'text/event-stream', 'cache-control': 'no-cache',
    connection: 'keep-alive', 'access-control-allow-origin': '*' });
  let closed = false;
  res.on('close', () => { closed = true; });

  await sleep(500); // "retrieval" pause before the first token
  for (const chunk of answer.match(/\S+\s*|\s+/g)) {
    if (closed) return;
    // Real payload: JSON.toJSONString(Map.of(nodeName, chunk)) from GraphProcess.
    res.write(`data:${JSON.stringify({ rag_chat: chunk })}\n\n`);
    await sleep(STREAM_DELAY_MS);
  }
  addRow(conversationId, 'ASSISTANT', answer);
  res.end();
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`);
  const p = url.pathname;

  if (req.method === 'OPTIONS') {
    res.writeHead(204, { 'access-control-allow-origin': '*', 'access-control-allow-headers': '*',
      'access-control-allow-methods': 'GET,POST,OPTIONS' }).end();
    return;
  }

  // API (paths as seen *through* the gateway: /chat/** and /upload/**)
  if (p.startsWith('/chat/') || p.startsWith('/upload/')) {
    if (!authorize(req, res)) return;

    if (req.method === 'GET' && p === '/chat/graph/rag/stream') return streamAnswer(req, res, url);

    if (req.method === 'GET' && p === '/chat/history/getByConversationId') {
      const id = url.searchParams.get('conversationId');
      return json(res, 200, history.filter((r) => r.conversationId === id));
    }

    if (req.method === 'GET' && p === '/chat/history/pages') {
      const pageNum = Number(url.searchParams.get('pageNum') ?? 1);
      const pageSize = Number(url.searchParams.get('pageSize') ?? 20);
      const userId = url.searchParams.get('userId');
      // One row per conversation: its first message, newest conversation first.
      const firsts = new Map();
      for (const r of history) if (r.userId === userId && !firsts.has(r.conversationId)) firsts.set(r.conversationId, r);
      const rows = [...firsts.values()].sort((a, b) => b.createdDate.localeCompare(a.createdDate)).map(({ userName, userId, conversationId, content, createdDate }) =>
        ({ userName, userId, conversationId, content, createdDate }));
      const total = rows.length;
      return json(res, 200, { data: rows.slice((pageNum - 1) * pageSize, pageNum * pageSize),
        pageNo: pageNum, pageSize, total, pages: Math.ceil(total / pageSize) });
    }

    if (req.method === 'POST' && p === '/upload/pdf') {
      let bytes = 0;
      for await (const c of req) bytes += c.length;
      await sleep(900); // pretend to chunk + embed
      res.writeHead(200, { 'content-type': 'text/plain', 'access-control-allow-origin': '*' });
      return res.end(`File uploaded and processed successfully. (${bytes} bytes)`);
    }
    return json(res, 404, { error: 'not found' });
  }

  // Static frontend
  const rel = p === '/' ? 'index.html' : p.slice(1);
  const file = path.join(ROOT, rel);
  if (!file.startsWith(ROOT + path.sep) || rel.startsWith('scripts') || rel.endsWith('.mjs')) { res.writeHead(404).end(); return; }
  try {
    const data = await readFile(file);
    res.writeHead(200, { 'content-type': MIME[path.extname(file)] ?? 'application/octet-stream' });
    res.end(data);
  } catch { res.writeHead(404).end('Not found'); }
});

server.listen(PORT, '127.0.0.1', () => console.log(`FileChat demo (mock API) on http://localhost:${PORT}`));
