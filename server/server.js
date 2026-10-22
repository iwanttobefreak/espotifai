'use strict';

const express = require('express');
const cors = require('cors');
const crypto = require('crypto');
const fs = require('fs');
const fsp = require('fs/promises');
const path = require('path');
const { parseFile } = require('music-metadata');
const archiver = require('archiver');

const MUSIC_DIR = process.env.MUSIC_DIR || '/music';
const API_TOKEN = process.env.API_TOKEN || '';
const PORT = parseInt(process.env.PORT || '8088', 10);
const INDEX_FILE = process.env.INDEX_FILE || path.join(__dirname, 'data', 'index.json');
const EXCLUDE_DIRS = new Set(
  (process.env.EXCLUDE_DIRS || '').split(',').map((s) => s.trim()).filter(Boolean)
);

const AUDIO_EXT = new Set(['.mp3', '.m4a', '.flac', '.ogg', '.wav', '.opus', '.aac']);
const COVER_NAMES = ['cover.jpg', 'cover.jpeg', 'cover.png', 'folder.jpg', 'folder.jpeg', 'front.jpg', 'front.jpeg', 'album.jpg', 'albumart.jpg', 'thumb.jpg'];

const app = express();
app.use(cors());
app.use(express.json());

// ---------- utilidades ----------
function b64urlEncode(str) {
  return Buffer.from(str, 'utf8').toString('base64url');
}

function b64urlDecode(str) {
  return Buffer.from(str, 'base64url').toString('utf8');
}

function isSafeRel(rel) {
  const abs = path.resolve(MUSIC_DIR, rel);
  const root = path.resolve(MUSIC_DIR);
  return abs === root || abs.startsWith(root + path.sep);
}

function cleanTitle(filename) {
  let name = filename.replace(/\.[^.]+$/, '');
  name = name.replace(/^\d+\s*[-_.]\s*/, '');
  name = name.replace(/\s*\(\d+\s*kbps\)\s*$/i, '');
  return name.trim() || filename;
}

// Entero en rango. Evita que un MP3 corrupto meta valores como
// bitrate=46044718001, que no caben en un Int de 32 bits y rompen el parseo
// de toda la biblioteca en la app (NumberFormatException/Gson).
function safeInt(v, min, max, d = null) {
  if (v === null || v === undefined || v === '') return d;
  const n = Math.round(Number(v));
  if (!Number.isFinite(n) || n < min || n > max) return d;
  return n;
}

// ---------- autenticacion y usuarios ----------
// API_TOKEN = token "admin": sirve para la app y ademas para gestionar usuarios.
// Los usuarios (token propio, para familia/dispositivos) viven en data/users.json.
// Asi se da/revoca acceso a cada uno sin tocar el token de los demas.
const USERS_FILE = process.env.USERS_FILE || path.join(__dirname, 'data', 'users.json');
let users = Object.create(null); // nombre -> { token, created, updated? }

function loadUsers() {
  try {
    const obj = JSON.parse(fs.readFileSync(USERS_FILE, 'utf8'));
    if (obj && typeof obj === 'object' && !Array.isArray(obj)) users = obj;
  } catch (e) {
    if (e.code !== 'ENOENT') console.error('users.json:', e.message);
    users = Object.create(null);
  }
}
function saveUsers() {
  fs.mkdirSync(path.dirname(USERS_FILE), { recursive: true });
  const tmp = `${USERS_FILE}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(users, null, 2));
  fs.renameSync(tmp, USERS_FILE);
}
function newToken() {
  return crypto.randomBytes(24).toString('base64url');
}
// ¿el token ya está en uso? -> por otro usuario, o es el token admin (reservado)
function tokenInUse(token, exceptName = null) {
  if (!token) return false;
  if (API_TOKEN && token === API_TOKEN) return true;
  for (const [name, u] of Object.entries(users)) {
    if (name !== exceptName && u.token === token) return true;
  }
  return false;
}
function uniqueToken() {
  let t;
  do { t = newToken(); } while (tokenInUse(t));
  return t;
}
function findUserByToken(tok) {
  if (!tok) return null;
  for (const [name, u] of Object.entries(users)) {
    if (u.token === tok) return name;
  }
  return null;
}
function tokenFromReq(req) {
  const header = req.headers['authorization'] || '';
  return header.startsWith('Bearer ') ? header.slice(7) : (req.query.token || '');
}
loadUsers();

function auth(req, res, next) {
  const tok = tokenFromReq(req);
  // Sin ningun token configurado -> abierto (comportamiento antiguo)
  if (!API_TOKEN && Object.keys(users).length === 0) return next();
  if (API_TOKEN && tok === API_TOKEN) { req.user = 'admin'; req.isAdmin = true; return next(); }
  const name = findUserByToken(tok);
  if (name) { req.user = name; req.isAdmin = false; return next(); }
  return res.status(401).json({ error: 'Unauthorized' });
}

function requireAdmin(req, res, next) {
  if (!API_TOKEN) return res.status(500).json({ error: 'API_TOKEN (admin) no configurado' });
  if (tokenFromReq(req) !== API_TOKEN) return res.status(403).json({ error: 'requiere token admin' });
  return next();
}

// ---------- escaneo de la biblioteca ----------
async function findCover(dir) {
  for (const name of COVER_NAMES) {
    const p = path.join(dir, name);
    try {
      await fsp.access(p);
      return name;
    } catch (_) { /* no existe */ }
  }
  const entries = await fsp.readdir(dir, { withFileTypes: true }).catch(() => []);
  for (const e of entries) {
    if (e.isFile() && /\.(jpe?g|png)$/i.test(e.name)) return e.name;
  }
  return null;
}

async function readMeta(filePath, rel) {
  try {
    const meta = await parseFile(filePath, { duration: true });
    const common = meta.common || {};
    const format = meta.format || {};
    return {
      title: common.title || cleanTitle(path.basename(rel)),
      artist: common.artist || null,
      album: common.album || null,
      track: safeInt(common.track && common.track.no, 0, 999, null),
      duration: safeInt(format.duration, 0, 12 * 3600, 0),
      bitrate: safeInt(format.bitrate, 1, 10000000, null),
    };
  } catch (_) {
    return { title: cleanTitle(path.basename(rel)), artist: null, album: null, track: null, duration: 0, bitrate: null };
  }
}

async function walkDir(dir, relBase) {
  const out = [];
  let entries = [];
  try {
    entries = await fsp.readdir(dir, { withFileTypes: true });
  } catch (_) {
    return out;
  }
  entries.sort((a, b) => a.name.localeCompare(b.name, 'es', { numeric: true }));
  for (const e of entries) {
    const abs = path.join(dir, e.name);
    const rel = path.posix.join(relBase, e.name);
    if (e.isDirectory()) {
      if (EXCLUDE_DIRS.has(e.name)) continue;
      out.push(...(await walkDir(abs, rel)));
    } else if (e.isFile() && AUDIO_EXT.has(path.extname(e.name).toLowerCase())) {
      out.push({ abs, rel });
    }
  }
  return out;
}

async function buildLibrary() {
  const files = await walkDir(MUSIC_DIR, '');
  const artists = new Map();

  for (const f of files) {
    const parts = f.rel.split('/').filter(Boolean);
    const artistName = parts[0] || 'Desconocido';
    const albumDir = parts.length > 1 ? parts.slice(0, -1).join('/') : artistName;
    const albumName = parts.length > 1 ? parts[parts.length - 2] : 'Sin álbum';
    const meta = await readMeta(f.abs, f.rel);

    if (!artists.has(artistName)) {
      artists.set(artistName, { id: b64urlEncode(artistName), name: artistName, albums: new Map() });
    }
    const artist = artists.get(artistName);

    if (!artist.albums.has(albumDir)) {
      const coverFile = await findCover(path.join(MUSIC_DIR, albumDir));
      artist.albums.set(albumDir, {
        id: b64urlEncode(albumDir),
        name: meta.album || albumName,
        cover: coverFile ? b64urlEncode(path.posix.join(albumDir, coverFile)) : null,
        tracks: [],
      });
    }
    artist.albums.get(albumDir).tracks.push({
      id: b64urlEncode(f.rel),
      title: meta.title,
      artist: meta.artist || artistName,
      album: meta.album || albumName,
      track: meta.track,
      duration: meta.duration,
      bitrate: meta.bitrate,
    });
  }

  const result = [];
  for (const a of artists.values()) {
    const albums = [];
    for (const al of a.albums.values()) {
      al.tracks.sort((x, y) => (x.track || 0) - (y.track || 0) || x.title.localeCompare(y.title, 'es'));
      albums.push(al);
    }
    albums.sort((x, y) => x.name.localeCompare(y.name, 'es', { numeric: true }));
    result.push({ id: a.id, name: a.name, albums });
  }
  result.sort((x, y) => x.name.localeCompare(y.name, 'es'));
  return { library: result, totalTracks: files.length };
}

let libraryCache = null;
let scanPromise = null;

async function rescan() {
  if (scanPromise) return scanPromise;
  scanPromise = (async () => {
    const lib = await buildLibrary();
    libraryCache = lib;
    try {
      await fsp.mkdir(path.dirname(INDEX_FILE), { recursive: true });
      await fsp.writeFile(INDEX_FILE, JSON.stringify({ updated: Date.now(), ...lib }, null, 2));
    } catch (e) {
      console.error('No se pudo guardar el indice:', e.message);
    }
    return lib;
  })().finally(() => { scanPromise = null; });
  return scanPromise;
}

async function initLibrary() {
  try {
    const cached = JSON.parse(await fsp.readFile(INDEX_FILE, 'utf8'));
    if (cached && Array.isArray(cached.library)) {
      libraryCache = { library: cached.library, totalTracks: cached.totalTracks || 0 };
      console.log(`Indice cargado de cache (${libraryCache.totalTracks} pistas).`);
      return;
    }
  } catch (_) { /* no cache */ }
  await rescan();
}

// ---------- rutas ----------
app.get('/api/health', (req, res) => {
  res.json({ ok: true, name: 'espotifai-server', version: '1.0.0', totalTracks: libraryCache ? libraryCache.totalTracks : 0 });
});

// ---------- gestion de usuarios (solo token admin) ----------
// Crear / listar / cambiar token / borrar. El token de cada usuario se devuelve
// al crearlo o al cambiarlo (no se lista entero para no filtrarlo).
app.get('/api/users', requireAdmin, (req, res) => {
  res.json({
    count: Object.keys(users).length,
    users: Object.entries(users).map(([name, u]) => ({
      name,
      created: u.created || null,
      updated: u.updated || null,
      token: u.token ? `${u.token.slice(0, 4)}…${u.token.slice(-4)}` : null,
    })),
  });
});

app.post('/api/users', requireAdmin, (req, res) => {
  const name = String((req.body && req.body.name) || '').trim();
  if (!/^[A-Za-z0-9._-]{1,40}$/.test(name)) {
    return res.status(400).json({ error: 'nombre invalido (A-Z a-z 0-9 . _ -, 1-40)' });
  }
  if (users[name]) return res.status(409).json({ error: 'ya existe', name });
  let token;
  if (req.body && req.body.token) {
    token = String(req.body.token);
    if (tokenInUse(token)) return res.status(409).json({ error: 'ese token ya está en uso (elígelo distinto o no lo pases para generar uno)' });
  } else {
    token = uniqueToken();
  }
  users[name] = { token, created: new Date().toISOString() };
  saveUsers();
  console.log(`usuario creado: ${name}`);
  res.status(201).json({ ok: true, name, token });
});

app.put('/api/users/:name', requireAdmin, (req, res) => {
  const name = req.params.name;
  if (!users[name]) return res.status(404).json({ error: 'no existe', name });
  let token;
  if (req.body && req.body.token) {
    token = String(req.body.token);
    if (tokenInUse(token, name)) return res.status(409).json({ error: 'ese token ya está en uso (elígelo distinto o no lo pases para generar uno)' });
  } else {
    token = uniqueToken();
  }
  users[name].token = token;
  users[name].updated = new Date().toISOString();
  saveUsers();
  console.log(`token cambiado: ${name}`);
  res.json({ ok: true, name, token });
});

app.delete('/api/users/:name', requireAdmin, (req, res) => {
  const name = req.params.name;
  if (!users[name]) return res.status(404).json({ error: 'no existe', name });
  delete users[name];
  saveUsers();
  console.log(`usuario borrado: ${name}`);
  res.json({ ok: true, deleted: name });
});

app.get('/api/library', auth, async (req, res) => {
  try {
    if (!libraryCache) await initLibrary();
    res.json(libraryCache);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.post('/api/rescan', auth, async (req, res) => {
  try {
    const lib = await rescan();
    res.json({ ok: true, totalTracks: lib.totalTracks });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});


app.get('/api/artists', auth, async (req, res) => {
  try {
    if (!libraryCache) await initLibrary();
    const artists = (libraryCache.library || []).map(a => ({ id: a.id, name: a.name, albumsCount: (a.albums || []).length }));
    res.json(artists);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});
app.get('/artists', auth, async (req, res) => {
  try {
    if (!libraryCache) await initLibrary();
    const artists = (libraryCache.library || []).map(a => ({ id: a.id, name: a.name, albumsCount: (a.albums || []).length }));
    res.json(artists);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});


app.get('/api/stream/:id', auth, (req, res) => {
  let rel;
  try { rel = b64urlDecode(req.params.id); } catch (_) { return res.status(400).json({ error: 'id invalido' }); }
  if (!isSafeRel(rel)) return res.status(403).json({ error: 'forbidden' });
  const abs = path.join(MUSIC_DIR, rel);
  fs.stat(abs, (err, stat) => {
    if (err) return res.status(404).json({ error: 'not found' });
    const mime = { '.mp3': 'audio/mpeg', '.m4a': 'audio/mp4', '.flac': 'audio/flac', '.ogg': 'audio/ogg', '.opus': 'audio/ogg', '.wav': 'audio/wav', '.aac': 'audio/aac' }[path.extname(abs).toLowerCase()] || 'application/octet-stream';
    const range = req.headers.range;
    res.setHeader('Accept-Ranges', 'bytes');
    res.setHeader('Content-Type', mime);
    if (range) {
      const m = /bytes=(\d*)-(\d*)/.exec(range);
      let start = m && m[1] ? parseInt(m[1], 10) : 0;
      let end = m && m[2] ? parseInt(m[2], 10) : stat.size - 1;
      if (isNaN(start) || start < 0) start = 0;
      if (isNaN(end) || end >= stat.size) end = stat.size - 1;
      if (start > end || start >= stat.size) {
        res.status(416).setHeader('Content-Range', `bytes */${stat.size}`);
        return res.end();
      }
      res.status(206);
      res.setHeader('Content-Range', `bytes ${start}-${end}/${stat.size}`);
      res.setHeader('Content-Length', end - start + 1);
      fs.createReadStream(abs, { start, end }).pipe(res);
    } else {
      res.setHeader('Content-Length', stat.size);
      fs.createReadStream(abs).pipe(res);
    }
  });
});

app.get('/api/cover/:id', auth, (req, res) => {
  let rel;
  try { rel = b64urlDecode(req.params.id); } catch (_) { return res.status(400).json({ error: 'id invalido' }); }
  if (!isSafeRel(rel)) return res.status(403).json({ error: 'forbidden' });
  const abs = path.join(MUSIC_DIR, rel);
  const ext = path.extname(abs).toLowerCase();
  const mime = { '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.png': 'image/png' }[ext] || 'image/jpeg';
  res.setHeader('Content-Type', mime);
  res.setHeader('Cache-Control', 'public, max-age=86400');
  fs.createReadStream(abs).on('error', () => res.status(404).end()).pipe(res);
});

// ---------- log de fallos de la app ----------
// La app sube aqui la traza si se cierra por un error (ver CrashReporter.kt).
// Fichero: data/clientlog.txt (se recorta a ~512 KB).
const CLIENTLOG_FILE = path.join(__dirname, 'data', 'clientlog.txt');
const CLIENTLOG_MAX = 512 * 1024;

// ---------- descarga de un album en ZIP ----------
// GET /api/album/:id/zip
// El id es el de la carpeta del album (base64url, igual que en /api/library).
app.get('/api/album/:id/zip', auth, async (req, res) => {
  try {
    if (!libraryCache) await initLibrary();

    let rel;
    try { rel = b64urlDecode(req.params.id); } catch (_) {
      return res.status(400).json({ error: 'id invalido' });
    }
    if (!isSafeRel(rel)) return res.status(403).json({ error: 'forbidden' });

    const abs = path.join(MUSIC_DIR, rel);
    let st;
    try { st = await fsp.stat(abs); } catch (_) {
      return res.status(404).json({ error: 'album no encontrado' });
    }
    if (!st.isDirectory()) return res.status(400).json({ error: 'no es un album' });

    const album = (libraryCache.library || [])
      .flatMap((a) => a.albums || [])
      .find((al) => al.id === req.params.id);
    if (!album) return res.status(404).json({ error: 'album no indexado' });

    const ficheros = [];
    for (const t of album.tracks || []) {
      let trel;
      try { trel = b64urlDecode(t.id); } catch (_) { continue; }
      if (trel !== rel && !trel.startsWith(rel + '/')) continue;
      if (!isSafeRel(trel)) continue;
      const tabs = path.join(MUSIC_DIR, trel);
      try {
        if ((await fsp.stat(tabs)).isFile()) {
          ficheros.push({ abs: tabs, name: path.basename(trel) });
        }
      } catch (_) { /* pista desaparecida */ }
    }
    if (album.cover) {
      let crel;
      try { crel = b64urlDecode(album.cover); } catch (_) { crel = null; }
      if (crel && (crel === rel || crel.startsWith(rel + '/')) && isSafeRel(crel)) {
        const cabs = path.join(MUSIC_DIR, crel);
        try {
          if ((await fsp.stat(cabs)).isFile()) {
            ficheros.push({ abs: cabs, name: path.basename(crel) });
          }
        } catch (_) { /* sin portada */ }
      }
    }
    if (!ficheros.length) return res.status(404).json({ error: 'album sin ficheros' });

    const artista = (album.tracks && album.tracks[0] && album.tracks[0].artist) || rel.split('/')[0] || '';
    const nombre = [artista, album.name].filter(Boolean).join(' - ')
      .replace(/[\\/:*?"<>|\x00-\x1f]/g, '_')
      .replace(/\s+/g, ' ')
      .trim() || 'album';
    const zipName = `${nombre}.zip`;
    const ascii = zipName.replace(/[^\x20-\x7E]/g, '_');

    res.setHeader('Content-Type', 'application/zip');
    res.setHeader(
      'Content-Disposition',
      `attachment; filename="${ascii}"; filename*=UTF-8''${encodeURIComponent(zipName)}`
    );

    const archive = archiver('zip', { zlib: { level: 6 } });
    archive.on('error', (err) => {
      console.error('zip:', err.message);
      try { res.destroy(err); } catch (_) { /* conexion cerrada */ }
    });
    archive.pipe(res);
    for (const f of ficheros) archive.file(f.abs, { name: f.name });
    await archive.finalize();
  } catch (e) {
    try { res.status(500).json({ error: e.message }); } catch (_) { /* ya enviado */ }
  }
});

app.post('/api/clientlog', auth, express.text({ type: '*/*', limit: '256kb' }), async (req, res) => {
  try {
    const texto = typeof req.body === 'string' ? req.body : JSON.stringify(req.body);
    const linea = `\n===== ${new Date().toISOString()} =====\n${texto}\n`;
    await fsp.mkdir(path.dirname(CLIENTLOG_FILE), { recursive: true });
    try {
      const st = await fsp.stat(CLIENTLOG_FILE);
      if (st.size > CLIENTLOG_MAX) await fsp.writeFile(CLIENTLOG_FILE, '(recortado)\n');
    } catch (_) { /* no existe aun */ }
    await fsp.appendFile(CLIENTLOG_FILE, linea);
    console.log(`clientlog: ${texto.split('\n')[0].slice(0, 160)}`);
    res.json({ ok: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.get('/api/clientlog', auth, async (req, res) => {
  try {
    res.type('text/plain').send(await fsp.readFile(CLIENTLOG_FILE, 'utf8'));
  } catch (_) {
    res.type('text/plain').send('');
  }
});

// ---------- arranque ----------
(async () => {
  await initLibrary();
  app.listen(PORT, '0.0.0.0', () => {
    console.log(`espotifai-server escuchando en http://0.0.0.0:${PORT}`);
    console.log(`Directorio: ${MUSIC_DIR}`);
    console.log(`Token admin: ${API_TOKEN ? 'activado' : 'desactivado'} · usuarios: ${Object.keys(users).length}`);
  });
})();

app.get('/api/v1/artists', auth, async (req, res) => {
  try {
    if (!libraryCache) await initLibrary();
    res.json((libraryCache.library || []).map(a => ({ id: a.id, name: a.name, albumsCount: (a.albums || []).length })));
  } catch (e) { res.status(500).json({ error: e.message }); }
});
app.get('/v1/artists', auth, async (req, res) => {
  try {
    if (!libraryCache) await initLibrary();
    res.json((libraryCache.library || []).map(a => ({ id: a.id, name: a.name, albumsCount: (a.albums || []).length })));
  } catch (e) { res.status(500).json({ error: e.message }); }
});

app.get('/library', auth, async (req, res) => {
  try { if (!libraryCache) await initLibrary(); res.json(libraryCache); } catch (e) { res.status(500).json({ error: e.message }); }
});
app.get('/api', (req,res)=>{ res.json({ ok:true, name:'espotifai-server', version:'1.0.0', totalTracks: libraryCache?libraryCache.totalTracks:0 }); });

app.get('/api/v1/library', auth, async (req, res) => {
  try { if (!libraryCache) await initLibrary(); res.json(libraryCache); } catch (e) { res.status(500).json({ error: e.message }); }
});
app.get('/v1/library', auth, async (req, res) => {
  try { if (!libraryCache) await initLibrary(); res.json(libraryCache); } catch (e) { res.status(500).json({ error: e.message }); }
});
app.get('/api/artists/list', auth, async (req, res) => {
  try { if (!libraryCache) await initLibrary(); res.json((libraryCache.library||[]).map(a=>({id:a.id,name:a.name,albumsCount:(a.albums||[]).length}))); } catch (e) { res.status(500).json({ error: e.message }); }
});

app.get('/api/all', auth, async (req,res)=>{
  try{ if(!libraryCache) await initLibrary(); res.json(libraryCache); }catch(e){res.status(500).json({error:e.message});}
});
