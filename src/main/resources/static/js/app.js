(() => {
  const state = {
    token: localStorage.getItem('jwt'),
    role: localStorage.getItem('role'),
    emailAddress: localStorage.getItem('emailAddress'),
  };

  const contentPanel = document.getElementById('contentPanel');

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

  function setAuth(auth) {
    state.token = auth.token;
    state.role = auth.role;
    state.emailAddress = auth.emailAddress;
    localStorage.setItem('jwt', auth.token);
    localStorage.setItem('role', auth.role);
    localStorage.setItem('emailAddress', auth.emailAddress);
  }

  function clearAuth() {
    state.token = null;
    state.role = null;
    state.emailAddress = null;
    localStorage.removeItem('jwt');
    localStorage.removeItem('role');
    localStorage.removeItem('emailAddress');
  }

  // ── Album grid rendering ────────────────────────────────────────────────

  const homeGrid = {
    albums: [],
    sort: 'title',
    letter: null,
  };

  function albumCard(album) {
    const div = document.createElement('div');
    div.className = 'album-card';
    const img = document.createElement('img');
    img.src = `/api/song-library/albums/${album.albumId}/coverArt`;
    img.alt = album.albumName;
    img.onerror = () => img.remove();
    div.appendChild(img);
    const titleLine = album.genreName ? `${album.albumName} (${album.genreName})` : album.albumName;
    div.insertAdjacentHTML('beforeend', `
      <strong>${titleLine}</strong>
      <span>${album.artistName}</span>
    `);
    return div;
  }

  function sortKey(value) {
    if (!value || !value.trim()) return '￿';
    const first = value.trim()[0].toUpperCase();
    return /[A-Z]/.test(first) ? `~${value.toUpperCase()}` : value.toUpperCase();
  }

  function letterFor(value) {
    if (!value || !value.trim()) return '#';
    const first = value.trim()[0].toUpperCase();
    return /[A-Z]/.test(first) ? first : '#';
  }

  function sortedAlbums() {
    const field = homeGrid.sort === 'title' ? 'albumName' : 'artistName';
    return [...homeGrid.albums].sort((a, b) =>
      sortKey(a[field]).localeCompare(sortKey(b[field])),
    );
  }

  function availableLetters(albums) {
    const field = homeGrid.sort === 'title' ? 'albumName' : 'artistName';
    const letters = new Set(albums.map((a) => letterFor(a[field])));
    const ordered = ['#', ...'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('')];
    return ordered.filter((l) => letters.has(l));
  }

  function renderAlbumGridHeader(container, albums) {
    const header = document.createElement('div');
    header.className = 'album-grid-header';
    header.innerHTML = `
      <div class="album-grid-title">
        <span class="icon">♫</span>
        <div>
          <div class="title">All Albums</div>
          <div class="subtitle">${albums.length} albums</div>
        </div>
      </div>
      <div class="sort-toggle">
        <span class="sort-toggle-label">Order By:</span>
        <button class="sort-btn" data-sort="title">Title</button>
        <button class="sort-btn" data-sort="artist">Artist</button>
      </div>
    `;
    header.querySelectorAll('.sort-btn').forEach((btn) => {
      btn.classList.toggle('active', btn.dataset.sort === homeGrid.sort);
      btn.addEventListener('click', () => {
        if (homeGrid.sort === btn.dataset.sort) return;
        homeGrid.sort = btn.dataset.sort;
        homeGrid.letter = null;
        renderHomeGrid(container);
      });
    });
    container.appendChild(header);
  }

  function renderLetterNav(container, letters) {
    const nav = document.createElement('div');
    nav.className = 'letter-nav';

    const prevBtn = document.createElement('button');
    prevBtn.className = 'letter-nav-arrow';
    prevBtn.textContent = '‹';
    nav.appendChild(prevBtn);

    const buttons = document.createElement('div');
    buttons.className = 'letter-buttons';
    letters.forEach((letter) => {
      const btn = document.createElement('button');
      btn.className = 'letter-btn';
      btn.textContent = letter;
      btn.classList.toggle('active', letter === homeGrid.letter);
      btn.addEventListener('click', () => {
        homeGrid.letter = letter;
        renderHomeGrid(container);
      });
      buttons.appendChild(btn);
    });
    nav.appendChild(buttons);

    const nextBtn = document.createElement('button');
    nextBtn.className = 'letter-nav-arrow';
    nextBtn.textContent = '›';
    nav.appendChild(nextBtn);

    function stepLetter(delta) {
      const idx = letters.indexOf(homeGrid.letter);
      const nextIdx = idx === -1 ? 0 : (idx + delta + letters.length) % letters.length;
      homeGrid.letter = letters[nextIdx];
      renderHomeGrid(container);
    }
    prevBtn.addEventListener('click', () => stepLetter(-1));
    nextBtn.addEventListener('click', () => stepLetter(1));

    container.appendChild(nav);
  }

  function renderHomeGrid(homeContent) {
    homeContent.innerHTML = '';

    const albums = sortedAlbums();
    renderAlbumGridHeader(homeContent, albums);

    const letters = availableLetters(albums);
    if (!homeGrid.letter || !letters.includes(homeGrid.letter)) {
      homeGrid.letter = letters[0] || null;
    }

    const field = homeGrid.sort === 'title' ? 'albumName' : 'artistName';
    const visibleAlbums = homeGrid.letter
      ? albums.filter((a) => letterFor(a[field]) === homeGrid.letter)
      : albums;

    const grid = document.createElement('div');
    grid.className = 'album-grid';
    visibleAlbums.forEach((album) => grid.appendChild(albumCard(album)));
    homeContent.appendChild(grid);

    if (letters.length) {
      renderLetterNav(homeContent, letters);
    }
  }

  async function loadAlbumGrid(homeContent) {
    homeGrid.albums = await api('/api/song-library/albums');
    renderHomeGrid(homeContent);
  }

  // ── Views ───────────────────────────────────────────────────────────────

  function renderLogin(errorMessage) {
    contentPanel.innerHTML = `
      <div class="centered-view">
        <div class="auth-box">
          <h1>JukeANator</h1>
          <form id="loginForm">
            ${errorMessage ? `<div class="error-msg">${errorMessage}</div>` : ''}
            <label>Email <input type="email" id="loginEmail" required></label>
            <label>Password <input type="password" id="loginPassword" required></label>
            <button type="submit">Log in</button>
            <div class="secondary-actions">
              <button type="button" id="loginCancelBtn" class="link-btn">Cancel</button>
              <button type="button" id="showRegisterBtn" class="link-btn">Create Account</button>
            </div>
          </form>
        </div>
      </div>
    `;

    document.getElementById('loginCancelBtn').addEventListener('click', () => renderLogin());
    document.getElementById('showRegisterBtn').addEventListener('click', () => renderRegister());

    document.getElementById('loginForm').addEventListener('submit', async (e) => {
      e.preventDefault();
      const emailAddress = document.getElementById('loginEmail').value;
      const password = document.getElementById('loginPassword').value;
      try {
        const auth = await api('/api/users/login', {
          method: 'POST',
          body: JSON.stringify({ emailAddress, password }),
        });
        setAuth(auth);
        renderHome();
      } catch (err) {
        renderLogin('Login failed. Check your email and password.');
      }
    });
  }

  function renderRegister(errorMessage) {
    contentPanel.innerHTML = `
      <div class="centered-view">
        <div class="auth-box">
          <h1>Create Account</h1>
          <form id="registerForm">
            ${errorMessage ? `<div class="error-msg">${errorMessage}</div>` : ''}
            <label>First name <input type="text" id="registerFirstName" required></label>
            <label>Last name <input type="text" id="registerLastName" required></label>
            <label>Email <input type="email" id="registerEmail" required></label>
            <label>Password <input type="password" id="registerPassword" required></label>
            <button type="submit">Register</button>
            <div class="secondary-actions">
              <button type="button" id="registerCancelBtn" class="link-btn">Cancel</button>
            </div>
          </form>
        </div>
      </div>
    `;

    document.getElementById('registerCancelBtn').addEventListener('click', () => renderLogin());

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
        setAuth(auth);
        renderHome();
      } catch (err) {
        renderLogin('Could not create account. That email may already be registered.');
      }
    });
  }

  async function loadNowPlaying(widget) {
    try {
      const song = await api('/api/song-player/nowPlayingSong');
      if (!song) {
        widget.innerHTML = '<div class="now-playing-text"><span class="artist-name">Nothing playing</span></div>';
        return;
      }
      widget.innerHTML = `
        <img src="/api/song-library/albums/${song.albumId}/coverArt" alt="${song.albumName || ''}"
             onerror="this.remove()">
        <div class="now-playing-text">
          <div class="song-name">${song.songName}</div>
          <div class="artist-name">${song.artistName}</div>
          <div class="album-name">${song.albumName || ''}</div>
        </div>
      `;
    } catch (err) {
      widget.innerHTML = '<div class="now-playing-text"><span class="artist-name">Nothing playing</span></div>';
    }
  }

  async function loadCredits(widget) {
    try {
      const profile = await api('/api/users/me');
      widget.textContent = profile.numCredits ?? 0;
    } catch (err) {
      widget.textContent = '0';
    }
  }

  async function renderHome() {
    contentPanel.innerHTML = `
      <div class="app-frame">
        <header class="top-bar">
          <div class="credits-widget">
            <span class="credits-label">CREDITS</span>
            <span class="credits-value" id="creditsValue">0</span>
          </div>
          <h1 class="app-banner">JukeANator</h1>
          <div class="now-playing-widget" id="nowPlayingWidget"></div>
          <button id="logoutBtn" class="link-btn">Log out</button>
        </header>

        <main class="home-content" id="homeContent"></main>

        <nav class="bottom-tabs">
          <button class="bottom-tab active" data-tab="home">
            <span class="tab-icon">⌂</span><span>HOME</span>
          </button>
          <button class="bottom-tab" data-tab="search" disabled>
            <span class="tab-icon">⌕</span><span>SEARCH</span>
          </button>
          <button class="bottom-tab" data-tab="hot" disabled>
            <span class="tab-icon">♨</span><span>HOT HERE</span>
          </button>
          <button class="bottom-tab" data-tab="genres" disabled>
            <span class="tab-icon">▦</span><span>GENRES</span>
          </button>
          <button class="bottom-tab" data-tab="queue" disabled>
            <span class="tab-icon">♫</span><span>QUEUE</span>
          </button>
        </nav>
      </div>
    `;

    document.getElementById('logoutBtn').addEventListener('click', () => {
      clearAuth();
      renderLogin();
    });

    await Promise.all([
      loadCredits(document.getElementById('creditsValue')),
      loadNowPlaying(document.getElementById('nowPlayingWidget')),
      loadAlbumGrid(document.getElementById('homeContent')),
    ]);
  }

  // ── Init ────────────────────────────────────────────────────────────────

  if (state.token) {
    renderHome();
  } else {
    renderLogin();
  }
})();
