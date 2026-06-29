(() => {
  const state = {
    token: localStorage.getItem('jwt'),
    role: localStorage.getItem('role'),
    emailAddress: localStorage.getItem('emailAddress'),
  };

  function isAdmin() {
    return state.role === 'ADMIN';
  }

  function authHeaders() {
    return state.token ? { Authorization: `Bearer ${state.token}` } : {};
  }

  async function api(path, options = {}) {
    const res = await fetch(path, {
      ...options,
      headers: { 'Content-Type': 'application/json', ...authHeaders(), ...(options.headers || {}) },
    });
    if (!res.ok) {
      throw new Error(`${options.method || 'GET'} ${path} failed: ${res.status}`);
    }
    const text = await res.text();
    return text ? JSON.parse(text) : null;
  }

  // ── Auth ────────────────────────────────────────────────────────────────

  function refreshAuthUi() {
    document.getElementById('authStatus').textContent = state.token
      ? `${state.emailAddress} (${state.role})`
      : 'Not logged in';
    document.getElementById('loginBtn').hidden = !!state.token;
    document.getElementById('logoutBtn').hidden = !state.token;
    document.getElementById('adminPlayerControls').hidden = !isAdmin();
    document.getElementById('adminQueueControls').hidden = !isAdmin();
  }

  document.getElementById('loginBtn').addEventListener('click', () => {
    document.getElementById('loginDialog').showModal();
  });

  document.getElementById('loginCancelBtn').addEventListener('click', () => {
    document.getElementById('loginDialog').close();
  });

  document.getElementById('showRegisterBtn').addEventListener('click', () => {
    document.getElementById('loginDialog').close();
    document.getElementById('registerDialog').showModal();
  });

  document.getElementById('registerCancelBtn').addEventListener('click', () => {
    document.getElementById('registerDialog').close();
  });

  document.getElementById('registerForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const firstName = document.getElementById('registerFirstName').value;
    const lastName = document.getElementById('registerLastName').value;
    const emailAddress = document.getElementById('registerEmail').value;
    const password = document.getElementById('registerPassword').value;
    try {
      const auth = await api('/api/users/register', {
        method: 'POST',
        body: JSON.stringify({ firstName, lastName, emailAddress, password }),
      });
      state.token = auth.token;
      state.role = auth.role;
      state.emailAddress = auth.emailAddress;
      localStorage.setItem('jwt', auth.token);
      localStorage.setItem('role', auth.role);
      localStorage.setItem('emailAddress', auth.emailAddress);
      document.getElementById('registerDialog').close();
      refreshAuthUi();
    } catch (err) {
      alert('Registration failed');
    }
  });

  document.getElementById('logoutBtn').addEventListener('click', () => {
    state.token = null;
    state.role = null;
    state.emailAddress = null;
    localStorage.removeItem('jwt');
    localStorage.removeItem('role');
    localStorage.removeItem('emailAddress');
    refreshAuthUi();
  });

  document.getElementById('loginForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const emailAddress = document.getElementById('loginEmail').value;
    const password = document.getElementById('loginPassword').value;
    try {
      const auth = await api('/api/users/login', {
        method: 'POST',
        body: JSON.stringify({ emailAddress, password }),
      });
      state.token = auth.token;
      state.role = auth.role;
      state.emailAddress = auth.emailAddress;
      localStorage.setItem('jwt', auth.token);
      localStorage.setItem('role', auth.role);
      localStorage.setItem('emailAddress', auth.emailAddress);
      document.getElementById('loginDialog').close();
      refreshAuthUi();
    } catch (err) {
      alert('Login failed');
    }
  });

  // ── Tabs ────────────────────────────────────────────────────────────────

  document.querySelectorAll('.tab-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.tab-btn').forEach((b) => b.classList.remove('active'));
      document.querySelectorAll('.tab').forEach((t) => (t.hidden = true));
      btn.classList.add('active');
      document.getElementById(btn.dataset.tab).hidden = false;
    });
  });
  document.querySelector('.tab-btn').classList.add('active');

  // ── Rendering helpers ───────────────────────────────────────────────────

  function albumCard(album) {
    const div = document.createElement('div');
    div.className = 'album-card';
    const img = document.createElement('img');
    img.src = `/api/song-library/albums/${album.albumId}/coverArt`;
    img.alt = album.albumName;
    img.onerror = () => img.remove();
    div.appendChild(img);
    div.insertAdjacentHTML('beforeend', `
      <strong>${album.albumName}</strong>
      <span>${album.artistName}</span>
    `);
    return div;
  }

  function songRow(song, onAdd) {
    const div = document.createElement('div');
    div.className = 'song-row';
    div.innerHTML = `<span>${song.trackNumber ?? ''} ${song.songName} — ${song.artistName}</span>`;
    if (onAdd) {
      const btn = document.createElement('button');
      btn.textContent = 'Queue';
      btn.addEventListener('click', () => onAdd(song));
      div.appendChild(btn);
    }
    return div;
  }

  async function addSongToQueue(song) {
    if (!state.token) {
      alert('Log in to add songs to the queue');
      return;
    }
    try {
      const priority = await api('/api/song-queue/highestPriority');
      await api('/api/song-queue/addSong', {
        method: 'POST',
        body: JSON.stringify({
          username: state.emailAddress,
          albumId: song.albumId,
          songId: song.songId,
          priority,
        }),
      });
    } catch (err) {
      alert('Could not add song to queue');
    }
  }

  // ── Popular tab ─────────────────────────────────────────────────────────

  async function loadPopular() {
    const result = await api('/api/song-library/popular');
    renderPopular(result);
  }

  function renderPopular(result) {
    const container = document.getElementById('popularTab');
    container.innerHTML = '';
    const grid = document.createElement('div');
    grid.className = 'album-grid';
    (result.albums || []).forEach((album) => grid.appendChild(albumCard(album)));
    container.appendChild(grid);
  }

  // ── Search tab ──────────────────────────────────────────────────────────

  document.getElementById('searchBtn').addEventListener('click', async () => {
    const query = document.getElementById('searchInput').value.trim();
    if (!query) return;
    const result = await api(`/api/song-library/search?searchFor=${encodeURIComponent(query)}`);
    const container = document.getElementById('searchResults');
    container.innerHTML = '';
    (result.songs || []).forEach((song) => container.appendChild(songRow(song, addSongToQueue)));
    const grid = document.createElement('div');
    grid.className = 'album-grid';
    (result.albums || []).forEach((album) => grid.appendChild(albumCard(album)));
    container.appendChild(grid);
  });

  // ── Genres tab ──────────────────────────────────────────────────────────

  async function loadGenres() {
    const genres = await api('/api/song-library/genres');
    renderGenres(genres);
  }

  function renderGenres(genres) {
    const list = document.getElementById('genreList');
    list.innerHTML = '';
    genres.forEach((genre) => {
      const btn = document.createElement('button');
      btn.textContent = `${genre.genreName} (${genre.numPlays})`;
      btn.addEventListener('click', async () => {
        const albums = await api(`/api/song-library/genres/${genre.genreId}/albums`);
        const albumsContainer = document.getElementById('genreAlbums');
        albumsContainer.innerHTML = '';
        const grid = document.createElement('div');
        grid.className = 'album-grid';
        albums.forEach((album) => grid.appendChild(albumCard(album)));
        albumsContainer.appendChild(grid);
      });
      list.appendChild(btn);
    });
  }

  // ── Queue tab ───────────────────────────────────────────────────────────

  function renderQueue(queue) {
    const list = document.getElementById('queueList');
    list.innerHTML = '';
    queue.forEach((entry) => {
      const li = document.createElement('li');
      li.className = 'queue-item';
      const song = entry.song;
      li.innerHTML = `<span>#${entry.priority} ${song.songName} — ${song.artistName}</span>`;
      if (isAdmin()) {
        const removeBtn = document.createElement('button');
        removeBtn.textContent = 'Remove';
        removeBtn.addEventListener('click', () =>
          api('/api/song-queue/removeSongDownFromQueue', {
            method: 'POST',
            body: JSON.stringify({ albumId: song.albumId, songId: song.songId }),
          }),
        );
        li.appendChild(removeBtn);
      }
      list.appendChild(li);
    });
  }

  document.getElementById('randomizeBtn').addEventListener('click', () =>
    api('/api/song-queue/randomizeQueue', { method: 'POST' }),
  );
  document.getElementById('flushBtn').addEventListener('click', () =>
    api('/api/song-queue/flushQueue', { method: 'POST' }),
  );

  // ── Now playing / player controls ──────────────────────────────────────

  function renderNowPlaying(song) {
    const content = document.getElementById('nowPlayingContent');
    content.textContent = song ? `${song.songName} — ${song.artistName}` : 'Nothing playing';
  }

  function renderPlaybackStatus(status) {
    document.getElementById('playbackStatus').textContent = status
      ? `${status.status} (${status.elapsedSeconds ?? 0}s / ${status.totalSeconds ?? 0}s)`
      : '';
  }

  document.getElementById('pauseBtn').addEventListener('click', () =>
    api('/api/song-player/pause', { method: 'POST' }),
  );
  document.getElementById('nextBtn').addEventListener('click', () =>
    api('/api/song-player/next', { method: 'POST' }),
  );
  document.getElementById('stopBtn').addEventListener('click', () =>
    api('/api/song-player/stop', { method: 'POST' }),
  );

  // ── WebSocket live updates ──────────────────────────────────────────────

  function connectWebSocket() {
    const socket = new SockJS('/ws');
    const client = Stomp.over(socket);
    client.debug = null;
    client.connect({}, () => {
      client.subscribe('/topic/queue', (msg) => renderQueue(JSON.parse(msg.body)));
      client.subscribe('/topic/now-playing', (msg) => renderNowPlaying(JSON.parse(msg.body).song));
      client.subscribe('/topic/playback-status', (msg) => renderPlaybackStatus(JSON.parse(msg.body)));
      client.subscribe('/topic/genres', (msg) => renderGenres(JSON.parse(msg.body)));
      client.subscribe('/topic/popularity', (msg) => renderPopular(JSON.parse(msg.body)));
    });
  }

  // ── Init ────────────────────────────────────────────────────────────────

  async function init() {
    refreshAuthUi();
    connectWebSocket();
    await loadPopular();
    await loadGenres();
    try {
      renderQueue(await api('/api/song-queue/queuedSongs'));
    } catch (err) {
      // queue not available yet
    }
    try {
      renderNowPlaying(await api('/api/song-player/nowPlayingSong'));
      renderPlaybackStatus(await api('/api/song-player/playbackStatus'));
    } catch (err) {
      // nothing playing yet
    }
  }

  init();
})();
