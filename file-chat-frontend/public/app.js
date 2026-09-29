// File Chat UI. All requests go to /api/* -> auth gateway -> services (see ../server.mjs).
const $ = (sel, root = document) => root.querySelector(sel);
const el = (tag, cls, text) => { const n = document.createElement(tag); if (cls) n.className = cls; if (text != null) n.textContent = text; return n; };

const state = { conversationId: null, streaming: false, page: 1, pages: 1, pageSize: 6 };
const STORE_KEY = 'filechat.docs';
const PAGE_SIZE = 6;

// ---------- small helpers ----------
const userId = () => $('#userId').value.trim() || '1001';
const loadDocs = () => { try { return JSON.parse(localStorage.getItem(STORE_KEY) || '{}'); } catch { return {}; } };
const saveDoc = (id, meta) => { try { const d = loadDocs(); d[id] = { ...d[id], ...meta }; localStorage.setItem(STORE_KEY, JSON.stringify(d)); } catch { /* storage unavailable */ } };

function toast(msg, bad = false) {
  const t = $('#toast');
  t.textContent = msg; t.className = 'toast' + (bad ? ' bad' : ''); t.hidden = false;
  clearTimeout(toast.timer); toast.timer = setTimeout(() => { t.hidden = true; }, 4000);
}

async function api(path, opts) {
  const res = await fetch('/api' + path, opts);
  if (!res.ok) throw Object.assign(new Error(`${res.status} ${res.statusText}`), { status: res.status });
  return res;
}

// "Author: X - Book: Y" lines from the Neo4j node -> [{author, books[]}]
function groupBooks(lines) {
  const groups = new Map();
  for (const line of lines) {
    const m = /^Author: (.+?) - Book: (.+)$/.exec(line);
    if (!m) continue;
    if (!groups.has(m[1])) groups.set(m[1], []);
    groups.get(m[1]).push(m[2]);
  }
  return [...groups].map(([author, books]) => ({ author, books }));
}

const trunc = (s, n) => (s.length > n ? s.slice(0, n - 1) + '…' : s);

// ---------- graph rendering (authors -> WROTE -> ebooks) ----------
function renderGraph(groups) {
  const NS = 'http://www.w3.org/2000/svg';
  const mk = (tag, attrs = {}, text) => { const n = document.createElementNS(NS, tag); for (const k in attrs) n.setAttribute(k, attrs[k]); if (text != null) n.textContent = text; return n; };
  const W = 380, ROW = 34, GAP = 14;
  const height = groups.reduce((h, g) => h + Math.max(g.books.length, 1) * ROW + GAP, 0) || ROW;
  const svg = mk('svg', { viewBox: `0 0 ${W} ${height}`, role: 'img', 'aria-label': 'Authors and the ebooks they wrote' });
  const defs = mk('defs');
  const marker = mk('marker', { id: 'arrow', viewBox: '0 0 10 10', refX: 9, refY: 5, markerWidth: 6, markerHeight: 6, orient: 'auto' });
  marker.appendChild(mk('path', { d: 'M0 0L10 5L0 10z', fill: 'currentColor', style: 'color:var(--muted)' }));
  defs.appendChild(marker); svg.appendChild(defs);
  let y = 0;
  for (const g of groups) {
    const rows = Math.max(g.books.length, 1);
    const cy = y + (rows * ROW) / 2;
    g.books.forEach((title, i) => {
      const by = y + i * ROW + ROW / 2;
      svg.appendChild(mk('path', { class: 'g-edge', d: `M138 ${cy} C 172 ${cy}, 182 ${by}, 214 ${by}` }));
      const b = mk('g', { class: 'g-book' });
      b.appendChild(mk('title', {}, title));
      b.appendChild(mk('rect', { x: 214, y: by - 13, width: 162, height: 26, rx: 7 }));
      b.appendChild(mk('text', { x: 222, y: by + 4 }, trunc(title, 24)));
      svg.appendChild(b);
    });
    const a = mk('g', { class: 'g-author' });
    a.appendChild(mk('title', {}, g.author));
    a.appendChild(mk('rect', { x: 4, y: cy - 17, width: 134, height: 34, rx: 9 }));
    a.appendChild(mk('text', { x: 12, y: cy + 4 }, trunc(g.author, 20)));
    svg.appendChild(a);
    y += rows * ROW + GAP;
  }
  return svg;
}

// ---------- pipeline panel ----------
const STAGES = ['author_extraction_node', 'neo4j_search_node', 'vector_search_stream', 'prompt_template_stream', 'rag_chat_stream'];
const stageEl = (id) => $(`.stages li[data-stage="${id}"]`);
function setStage(id, status, detail) {
  const li = stageEl(id);
  li.className = status;
  const d = $('.detail', li);
  if (detail === undefined) return;
  d.replaceChildren(...(Array.isArray(detail) ? detail : detail == null ? [] : [detail]));
}
function resetPipeline() {
  STAGES.forEach((s) => setStage(s, s === 'rag_chat_stream' ? '' : 'running', null));
  $('#graphCard').hidden = true; $('#graphSvg').replaceChildren();
}
function chips(names, cls = '') { return names.map((n) => el('span', 'chip ' + cls, n)); }

// ---------- messages ----------
const messages = $('#messages');
function addMsg(role, text = '') {
  $('#empty')?.remove();
  const m = el('div', `msg ${role}`);
  if (text) m.textContent = text;
  messages.appendChild(m); m.scrollIntoView({ block: 'end' });
  return m;
}
function renderAnswer(node, text) {
  node.replaceChildren();
  for (const part of text.split(/(\[Doc:[^\]]*\])/)) {
    if (/^\[Doc:/.test(part)) node.appendChild(el('span', 'cite', part)); else node.appendChild(document.createTextNode(part));
  }
}
function addRelated(groups) {
  if (!groups.length) return;
  const box = el('div', 'related');
  box.appendChild(el('h4', '', 'More by the same authors · from Neo4j'));
  for (const g of groups) {
    const row = el('div', 'grp');
    row.append(...chips([g.author], 'author'), ...chips(g.books));
    box.appendChild(row);
  }
  messages.appendChild(box); box.scrollIntoView({ block: 'end' });
}

// ---------- chat (SSE over fetch) ----------
async function ask(question) {
  state.streaming = true; setComposer();
  addMsg('user', question);
  const answer = addMsg('assistant streaming');
  resetPipeline();
  let text = '', neo4jGroups = [], gotChunk = false;
  const url = `/graph/rag/stream?message=${encodeURIComponent(question)}&conversationId=${encodeURIComponent(state.conversationId)}`;
  try {
    const res = await api('/chat' + url, { headers: { Accept: 'text/event-stream' } });
    const reader = res.body.getReader(); const dec = new TextDecoder(); let buf = '';
    const handle = (ev) => {
      const [key, value] = Object.entries(ev)[0] || [];
      switch (key) {
        case 'author_extraction_node':
          setStage(key, 'done', value.length ? chips(value, 'author') : el('span', 'mono', 'no authors found'));
          break;
        case 'neo4j_search_node': {
          neo4jGroups = groupBooks(value);
          const n = neo4jGroups.reduce((s, g) => s + g.books.length, 0);
          setStage(key, 'done', el('span', '', n ? `${n} book${n > 1 ? 's' : ''} by ${neo4jGroups.length} author${neo4jGroups.length > 1 ? 's' : ''} in the graph` : 'authors not in the graph — skipped'));
          $('#graphSvg').replaceChildren(n ? renderGraph(neo4jGroups) : el('div', 'notfound', 'No matching authors in Neo4j.'));
          $('#graphCard').hidden = false;
          break;
        }
        case 'vector_search_stream':
          setStage(key, 'done', el('span', 'mono', `${value.length.toLocaleString()} chars of context · ` + trunc(value.replace(/\s+/g, ' ').replace(/^\[/, ''), 70)));
          break;
        case 'prompt_template_stream':
          setStage(key, 'done', el('span', 'mono', `${value.length.toLocaleString()} chars`));
          break;
        case 'rag_chat_stream':
          if (!gotChunk) { gotChunk = true; setStage(key, 'running', el('span', 'mono', 'streaming…')); }
          text += value; renderAnswer(answer, text); answer.scrollIntoView({ block: 'end' });
          break;
        default: break;
      }
    };
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      buf += dec.decode(value, { stream: true });
      let i;
      while ((i = buf.indexOf('\n\n')) >= 0) {
        const raw = buf.slice(0, i); buf = buf.slice(i + 2);
        for (const line of raw.split('\n')) {
          if (!line.startsWith('data:')) continue;
          try { handle(JSON.parse(line.slice(5).replace(/^ /, ''))); } catch { /* ignore malformed event */ }
        }
      }
    }
    setStage('rag_chat_stream', 'done', el('span', 'mono', `${text.length} chars`));
    STAGES.forEach((s) => { if (stageEl(s).className === 'running') setStage(s, 'skipped', el('span', 'mono', 'no event received')); });
    addRelated(neo4jGroups);
    loadConversations();
  } catch (e) {
    answer.classList.add('error'); answer.textContent = 'Request failed: ' + e.message;
    STAGES.forEach((s) => { if (stageEl(s).className === 'running') setStage(s, 'skipped'); });
  } finally {
    answer.classList.remove('streaming');
    state.streaming = false; setComposer();
  }
}

function setComposer() {
  const ready = !!state.conversationId && !state.streaming;
  $('#q').disabled = !ready; $('#send').disabled = !ready;
  if (ready) $('#q').focus();
}

$('#composer').addEventListener('submit', (e) => {
  e.preventDefault();
  const q = $('#q').value.trim();
  if (!q || state.streaming || !state.conversationId) return;
  $('#q').value = ''; ask(q);
});

// ---------- conversations ----------
function selectConversation(id, title) {
  state.conversationId = id;
  $('#chatTitle').textContent = title || 'Conversation';
  $('#convId').textContent = id;
  document.querySelectorAll('.convo-list li').forEach((li) => li.classList.toggle('active', li.dataset.id === id));
  setComposer();
}

async function openConversation(id, title) {
  selectConversation(id, title);
  messages.replaceChildren();
  resetPipeline(); STAGES.forEach((s) => setStage(s, '', null));
  try {
    const rows = await (await api(`/chat/history/getByConversationId?conversationId=${encodeURIComponent(id)}`)).json();
    if (!rows.length) { messages.appendChild(el('div', 'empty', 'No messages yet — ask a question below.')); return; }
    for (const r of rows) { const m = addMsg(r.type === 'USER' ? 'user' : 'assistant'); r.type === 'USER' ? (m.textContent = r.content) : renderAnswer(m, r.content); }
    messages.scrollTop = messages.scrollHeight;
  } catch (e) { toast('Could not load history: ' + e.message, true); }
}

async function loadConversations() {
  const list = $('#convoList');
  let server = [];
  try {
    const data = await (await api(`/chat/history/pages?pageNum=${state.page}&pageSize=${PAGE_SIZE}&userId=${encodeURIComponent(userId())}`)).json();
    server = data.data; state.pages = Math.max(data.pages, 1);
    setConn(true);
  } catch (e) { setConn(false); }
  const docs = loadDocs();
  const known = new Set(server.map((s) => s.conversationId));
  const localOnly = state.page === 1
    ? Object.entries(docs).filter(([id, d]) => !known.has(id) && d.userId === userId()).map(([id, d]) => ({ conversationId: id, content: null, createdDate: d.createdAt, fileName: d.fileName }))
    : [];
  const items = [...localOnly, ...server.map((s) => ({ ...s, fileName: docs[s.conversationId]?.fileName }))];
  list.replaceChildren();
  if (!items.length) list.appendChild(el('li', 'convo-empty', 'No conversations yet.'));
  for (const it of items) {
    const li = el('li'); li.dataset.id = it.conversationId; if (it.conversationId === state.conversationId) li.className = 'active';
    const title = it.fileName || (it.content ? trunc(it.content, 34) : 'Untitled document');
    li.appendChild(el('div', 't', title));
    // with a known file name the first question is the subtitle; otherwise the title already is the first question
    const sub = it.fileName ? (it.content || 'no messages yet') : 'conversation';
    const meta = el('div', 'm'); meta.append(el('span', '', sub), el('span', '', (it.createdDate || '').slice(5, 16)));
    li.appendChild(meta);
    li.addEventListener('click', () => openConversation(it.conversationId, title));
    list.appendChild(li);
  }
  $('#pageInfo').textContent = `${state.page} / ${state.pages}`;
  $('#prevPage').disabled = state.page <= 1; $('#nextPage').disabled = state.page >= state.pages;
}
$('#prevPage').addEventListener('click', () => { state.page--; loadConversations(); });
$('#nextPage').addEventListener('click', () => { state.page++; loadConversations(); });
$('#userId').addEventListener('change', () => { state.page = 1; loadConversations(); });

function setConn(ok) {
  const c = $('#conn'); c.textContent = ok ? 'gateway · auth ok' : 'gateway unreachable'; c.className = 'pill ' + (ok ? 'ok' : 'bad');
}

// ---------- upload ----------
async function upload(file) {
  if (!file) return;
  if (file.type !== 'application/pdf' && !file.name.toLowerCase().endsWith('.pdf')) { toast('Please choose a PDF file.', true); return; }
  const drop = $('#drop'); drop.classList.add('busy'); $('#dropHint').textContent = `Uploading ${file.name}…`;
  try {
    const res = await api(`/chat/graph/create-chat?userId=${encodeURIComponent(userId())}`, { method: 'POST', headers: { 'Content-Type': 'application/octet-stream' }, body: file });
    const id = (await res.text()).split('conversation_id:').pop().trim();
    saveDoc(id, { fileName: file.name, userId: userId(), createdAt: new Date().toISOString().replace('T', ' ').slice(0, 19) });
    messages.replaceChildren();
    addMsg('assistant', `“${file.name}” is indexed. Ask me anything about it.`);
    resetPipeline(); STAGES.forEach((s) => setStage(s, '', null));
    state.page = 1; await loadConversations();
    selectConversation(id, file.name);
    toast('Document uploaded and indexed');
  } catch (e) { toast('Upload failed: ' + e.message, true); }
  finally { drop.classList.remove('busy'); $('#dropHint').textContent = 'Starts a new conversation about it'; $('#file').value = ''; }
}
$('#file').addEventListener('change', (e) => upload(e.target.files[0]));
const dropEl = $('#drop');
['dragenter', 'dragover'].forEach((ev) => dropEl.addEventListener(ev, (e) => { e.preventDefault(); dropEl.classList.add('over'); }));
['dragleave', 'drop'].forEach((ev) => dropEl.addEventListener(ev, (e) => { e.preventDefault(); dropEl.classList.remove('over'); }));
dropEl.addEventListener('drop', (e) => upload(e.dataTransfer.files[0]));

// ---------- tabs + author explorer ----------
document.querySelectorAll('.tabs button').forEach((b) => b.addEventListener('click', () => {
  document.querySelectorAll('.tabs button').forEach((x) => x.classList.toggle('active', x === b));
  $('#tab-pipeline').hidden = b.dataset.tab !== 'pipeline'; $('#tab-explorer').hidden = b.dataset.tab !== 'explorer';
}));

$('#authorForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const name = $('#authorName').value.trim(); if (!name) return;
  const out = $('#explorerResult'); out.replaceChildren(el('p', 'hint', 'Querying Neo4j…'));
  const q = encodeURIComponent(name);
  const [books, entity] = await Promise.allSettled([
    api(`/chat/test/books/byAuthor?name=${q}`).then((r) => r.json()),
    api(`/chat/test/getAuthor?name=${q}`).then((r) => r.json()),
  ]);
  out.replaceChildren();
  if (books.status === 'rejected') { out.appendChild(el('div', 'notfound', 'Lookup failed: ' + books.reason.message)); return; }
  const b1 = el('div', 'result-block');
  const h = el('h3', '', 'Ebooks written'); h.appendChild(el('small', '', 'findEbookTitlesByAuthorName'));
  b1.appendChild(h);
  if (books.value.length) b1.appendChild(renderGraph([{ author: name, books: books.value }]));
  else b1.appendChild(el('div', 'notfound', `“${name}” has no ebooks in the graph — the chat skips the Neo4j recommendation for such authors.`));
  out.appendChild(b1);
  const b2 = el('div', 'result-block');
  const h2 = el('h3', '', 'Author node'); h2.appendChild(el('small', '', 'findByName'));
  b2.appendChild(h2);
  if (entity.status === 'fulfilled') {
    const d = el('details'); d.appendChild(el('summary', '', `Found · ${entity.value.ebook?.length ?? 0} linked ebooks`));
    d.appendChild(el('pre', '', JSON.stringify(entity.value, null, 2))); d.open = true; b2.appendChild(d);
  } else b2.appendChild(el('div', 'notfound', entity.reason.status === 404 ? 'No Author node with that name (404).' : 'Lookup failed: ' + entity.reason.message));
  out.appendChild(b2);
});

// ---------- init ----------
resetPipeline(); STAGES.forEach((s) => setStage(s, '', null));
loadConversations();
