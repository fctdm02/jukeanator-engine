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
        widget.innerHTML = `
          <div class="now-playing-idle">
            <div class="idle-title">No music playing</div>
            <div class="idle-sub">Let's play some music!</div>
          </div>`;
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
      widget.innerHTML = `
        <div class="now-playing-idle">
          <div class="idle-title">No music playing</div>
          <div class="idle-sub">Let's play some music!</div>
        </div>`;
    }
  }

  async function loadCredits(widget) {
    if (!state.token) {
      widget.textContent = 'Credits: 0';
      return;
    }
    try {
      const profile = await api('/api/users/me');
      widget.textContent = `Credits: ${profile.numCredits ?? 0}`;
    } catch (err) {
      widget.textContent = 'Credits: 0';
    }
  }

  async function renderHome() {
    contentPanel.innerHTML = `
      <div class="app-frame">
        <header class="top-bar">
          <div class="account-panel">
            <div class="account-left">
              <button class="location-btn">
                The Rock on Third <span class="location-arrow">&#8964;</span>
              </button>
              <span class="credits-value" id="creditsValue">Credits: 0</span>
            </div>
            <button class="account-logo-btn" id="accountBtn">
              <img src="/images/AccountLogo.png" alt="Account">
            </button>
          </div>
          <div class="now-playing-bar" id="nowPlayingWidget"></div>
        </header>
        <div class="search-bar-wrap">
          <div class="search-bar">
            <span class="search-icon">&#128269;</span>
            <input type="text" placeholder="Search for music" id="searchInput">
          </div>
        </div>

        <main class="home-content" id="homeContent">
          <div class="stub-placeholder">HOME — coming soon</div>
        </main>

        <nav class="bottom-tabs">
          <button class="bottom-tab active" data-tab="music">
            <span class="tab-icon">♫</span><span>Music</span>
          </button>
          <button class="bottom-tab" data-tab="addfunds" disabled>
            <span class="tab-icon">👛</span><span>Add Funds</span>
          </button>
        </nav>
      </div>
    `;

    document.getElementById('accountBtn').addEventListener('click', () => {
      if (state.token) {
        clearAuth();
        renderHome();
      } else {
        renderLogin();
      }
    });

    await Promise.all([
      loadCredits(document.getElementById('creditsValue')),
      loadNowPlaying(document.getElementById('nowPlayingWidget')),
    ]);
  }

  // ── WebSocket ───────────────────────────────────────────────────────────

  let stompClient = null;

  function connectWebSocket() {
    if (stompClient && stompClient.connected) return;
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    stompClient.debug = () => {};
    stompClient.connect({}, () => {
      stompClient.subscribe('/topic/now-playing', (frame) => {
        const widget = document.getElementById('nowPlayingWidget');
        if (!widget) return;
        const msg = JSON.parse(frame.body);
        const song = msg.song;
        if (!song) {
          widget.innerHTML = `
            <div class="now-playing-idle">
              <div class="idle-title">No music playing</div>
              <div class="idle-sub">Let's play some music!</div>
            </div>`;
        } else {
          widget.innerHTML = `
            <img src="/api/song-library/albums/${song.albumId}/coverArt" alt="${song.albumName || ''}"
                 onerror="this.remove()">
            <div class="now-playing-text">
              <div class="song-name">${song.songName}</div>
              <div class="artist-name">${song.artistName}</div>
              <div class="album-name">${song.albumName || ''}</div>
            </div>`;
        }
      });
    }, () => {
      setTimeout(connectWebSocket, 3000);
    });
  }

  // ── Init ────────────────────────────────────────────────────────────────

  if (state.token) {
    renderHome();
  } else {
    renderLogin();
  }
  connectWebSocket();
})();
