(() => {
  // ── State ───────────────────────────────────────────────────────────────
  const state = {
    token: localStorage.getItem('jwt'),
    role: localStorage.getItem('role'),
    emailAddress: localStorage.getItem('emailAddress'),
    pendingScreen: null,   // { screen, params } to navigate to after login
    navStack: [],          // { screen, params } entries for back navigation
    currentMainTab: 'music',
    searchHistory: null,   // cached from GET /api/users/home; null = not yet loaded
    recentPlays: [],       // cached from GET /api/users/home; updated live via STOMP
    hotHereArtists: [],    // cached from home page API; refreshed on loadHomePage
    hotHereSongs: [],      // cached from home page API; refreshed on loadHomePage
    numCredits: 0,         // cached credit count; refreshed on loadCredits()
    myPlaylists: [],       // [{name, songCount, firstSongAlbumId}] from GET /api/users/playlists
    favoriteSongIds: new Set(), // Set of 'albumId_songId' strings
  };

  const COST_PLAY = 2; // Web UI normal play cost (double the JFC 1-credit cost)

  const contentPanel = document.getElementById('contentPanel');

  // ── Auth helpers ────────────────────────────────────────────────────────
  function authHeaders() {
    return state.token ? { Authorization: `Bearer ${state.token}` } : {};
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

  // ── API helper ──────────────────────────────────────────────────────────
  async function api(path, options = {}) {
    const res = await fetch(path, {
      ...options,
      headers: { 'Content-Type': 'application/json', ...authHeaders(), ...(options.headers || {}) },
    });
    if (res.status === 401 && state.token) {
      // Stale/invalid token (e.g. left over from before the user store was reset) -
      // treat this the same as "not logged in" instead of surfacing a raw failure.
      clearAuth();
      renderLogin();
    }
    if (!res.ok) throw new Error(`${options.method || 'GET'} ${path} failed: ${res.status}`);
    const text = await res.text();
    return text ? JSON.parse(text) : null;
  }

  // ── Navigation ──────────────────────────────────────────────────────────
  function navigateSub(screen, params = {}) {
    state.navStack.push({ screen, params });
    renderSubScreen(screen, params);
  }

  function goBack() {
    state.navStack.pop(); // current screen
    const prev = state.navStack[state.navStack.length - 1];
    if (!prev) {
      renderMain(state.currentMainTab);
    } else {
      renderSubScreen(prev.screen, prev.params);
    }
  }

  // ── Sub-screen shell ────────────────────────────────────────────────────
  function subScreenShell(title, bodyHtml) {
    return `
      <div class="sub-screen">
        <header class="sub-header">
          <button class="back-btn" id="backBtn">&#8592;</button>
          <h1 class="sub-title">${title}</h1>
        </header>
        <div class="sub-content">${bodyHtml}</div>
      </div>`;
  }

  function renderSubScreen(screen, params = {}) {
    switch (screen) {
      case 'my-account':        renderMyAccount(params);        break;
      case 'manage-account':    renderManageAccount(params);    break;
      case 'user-profile':      renderUserProfile(params);      break;
      case 'change-password':   renderChangePassword(params);   break;
      case 'delete-account':    renderDeleteAccount(params);    break;
      case 'transaction-history': renderStub('Transaction History'); break;
      case 'help':              renderStub('Help');              break;
      case 'settings':          renderStub('Settings');          break;
      case 'terms':             renderStub('Terms and Conditions'); break;
      case 'privacy':           renderStub('Privacy Policy');    break;
      case 'recent-plays-all':       renderRecentPlaysAll();          break;
      case 'artists-hot-here-all':   renderArtistsHotHereAll();       break;
      case 'songs-hot-here-all':     renderSongsHotHereAll();         break;
      case 'my-playlists-all':       renderMyPlaylistsAll();          break;
      case 'playlist-detail':        renderPlaylistDetail(params);    break;
      case 'search-entry':           renderSearchEntry();             break;
      case 'search-results':    renderSearchResults(params.query, params.result); break;
      case 'artist-detail':     renderArtistDetail(params);      break;
      case 'album-detail':      renderAlbumDetail(params);       break;
      default:                  renderMain(state.currentMainTab);
    }
  }

  function wireBackBtn() {
    const btn = document.getElementById('backBtn');
    if (btn) btn.addEventListener('click', goBack);
  }

  // ── Main frame ──────────────────────────────────────────────────────────
  async function renderMain(tab = 'music') {
    state.currentMainTab = tab;
    state.navStack = [];

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

        <main class="home-content" id="homeContent"></main>

        <nav class="bottom-tabs">
          <button class="bottom-tab ${tab === 'music' ? 'active' : ''}" id="tabMusic">
            <span class="tab-icon">&#9835;</span><span>Music</span>
          </button>
          <button class="bottom-tab ${tab === 'addfunds' ? 'active' : ''}" id="tabAddFunds">
            <span class="tab-icon">&#128176;</span><span>Add Funds</span>
          </button>
        </nav>
      </div>`;

    document.getElementById('tabMusic').addEventListener('click', () => renderMain('music'));
    document.getElementById('tabAddFunds').addEventListener('click', () => {
      if (!state.token) {
        state.pendingScreen = { screen: 'addfunds' };
        renderLogin();
      } else {
        renderMain('addfunds');
      }
    });

    document.getElementById('accountBtn').addEventListener('click', () => {
      if (state.token) {
        navigateSub('my-account');
      } else {
        state.pendingScreen = { screen: 'my-account' };
        renderLogin();
      }
    });

    document.getElementById('searchInput').addEventListener('focus', (e) => {
      e.target.blur();
      navigateSub('search-entry');
    });

    await Promise.all([
      loadCredits(document.getElementById('creditsValue')),
      loadNowPlaying(document.getElementById('nowPlayingWidget')),
    ]);

    const homeContent = document.getElementById('homeContent');
    if (tab === 'music') {
      loadHomePage(homeContent);
    } else if (tab === 'addfunds') {
      loadAddFunds(homeContent);
    }
  }

  async function loadHomePage(container) {
    if (!container) return;

    try {
      if (state.token) {
        const [homePage, playlists, favIds] = await Promise.all([
          api('/api/users/home'),
          api('/api/users/playlists').catch(() => []),
          api('/api/users/playlists/favorites/songs').catch(() => []),
        ]);
        state.searchHistory    = homePage.searchHistory    || [];
        state.recentPlays      = homePage.myRecentPlays    || [];
        state.hotHereArtists   = homePage.artistsHotHere   || [];
        state.hotHereSongs     = homePage.songsHotHere     || [];
        state.myPlaylists      = playlists || [];
        state.favoriteSongIds  = new Set((favIds || []).map(si => `${si.albumId}_${si.songId}`));
      } else {
        const publicPage = await api('/api/users/home-public');
        state.hotHereArtists   = publicPage.artistsHotHere || [];
        state.hotHereSongs     = publicPage.songsHotHere   || [];
        state.myPlaylists      = [];
        state.favoriteSongIds  = new Set();
      }
    } catch {
      container.innerHTML = '<div class="stub-placeholder">Could not load home page.</div>';
      return;
    }

    if (state.token) {
      container.innerHTML = `
        <div class="home-sections">
          <section class="home-section">
            <div class="home-section-header">
              <h2 class="home-section-title">My Recent Plays</h2>
              <button class="home-section-view-all" id="recentPlaysViewAll">View All</button>
            </div>
            <div class="home-section-body" id="recentPlaysBody">${renderSwipeableSongThumbs(state.recentPlays, 'rp')}</div>
          </section>
          <section class="home-section">
            <div class="home-section-header">
              <h2 class="home-section-title">My Playlists</h2>
              <button class="home-section-view-all" id="myPlaylistsViewAll">View All</button>
            </div>
            <div class="home-section-body" id="myPlaylistsBody">${renderPlaylistTileRow(state.myPlaylists)}</div>
          </section>
          ${hotHereSectionsHtml()}
        </div>`;

      document.getElementById('recentPlaysViewAll')
        ?.addEventListener('click', () => navigateSub('recent-plays-all'));
      document.getElementById('myPlaylistsViewAll')
        ?.addEventListener('click', () => navigateSub('my-playlists-all'));
      wireSwipeableClicks('recentPlaysBody', state.recentPlays, s => showSongPopup(s));
      wirePlaylistTileClicks('myPlaylistsBody', state.myPlaylists);
    } else {
      container.innerHTML = `<div class="home-sections">${hotHereSectionsHtml()}</div>`;
    }

    wireHotHereButtons();
  }

  function hotHereSectionsHtml() {
    return `
      <section class="home-section">
        <div class="home-section-header">
          <h2 class="home-section-title">Artists Hot Here</h2>
          <button class="home-section-view-all" id="artistsHotHereViewAll">View All</button>
        </div>
        <div class="home-section-body" id="artistsHotHereBody">${renderSwipeableArtistThumbs(state.hotHereArtists)}</div>
      </section>
      <section class="home-section">
        <div class="home-section-header">
          <h2 class="home-section-title">Songs Hot Here</h2>
          <button class="home-section-view-all" id="songsHotHereViewAll">View All</button>
        </div>
        <div class="home-section-body" id="songsHotHereBody">${renderSwipeableSongThumbs(state.hotHereSongs, 'hot')}</div>
      </section>`;
  }

  function wireHotHereButtons() {
    document.getElementById('artistsHotHereViewAll')
      ?.addEventListener('click', () => navigateSub('artists-hot-here-all'));
    document.getElementById('songsHotHereViewAll')
      ?.addEventListener('click', () => navigateSub('songs-hot-here-all'));
    wireSwipeableClicks('artistsHotHereBody', state.hotHereArtists,
      a => navigateSub('artist-detail', { artistId: a.artistId }));
    wireSwipeableClicks('songsHotHereBody', state.hotHereSongs, s => showSongPopup(s));
  }

  function songThumbHtml(s) {
    const art = s.albumId != null
      ? `<img src="/api/song-library/albums/${s.albumId}/coverArt" alt=""
              onerror="this.outerHTML='<div class=\\'rp-thumb-placeholder\\'>&#127925;</div>'">`
      : `<div class="rp-thumb-placeholder">&#127925;</div>`;
    return `<div class="rp-thumb-card">
      <div class="rp-thumb-img">${art}</div>
      <div class="rp-thumb-song">${escHtml(s.songName || '')}</div>
      <div class="rp-thumb-artist">${escHtml(s.artistName || '')}</div>
    </div>`;
  }

  function artistThumbHtml(a) {
    const art = (a.albums && a.albums.length && a.albums[0].albumId != null)
      ? `<img src="/api/song-library/albums/${a.albums[0].albumId}/coverArt" alt=""
              onerror="this.outerHTML='<div class=\\'rp-thumb-placeholder\\'>&#127911;</div>'">`
      : `<div class="rp-thumb-placeholder">&#127911;</div>`;
    return `<div class="rp-thumb-card">
      <div class="rp-thumb-img">${art}</div>
      <div class="rp-thumb-song">${escHtml(a.artistName || '')}</div>
      <div class="rp-thumb-artist">${a.songCount != null ? escHtml(String(a.songCount)) + ' songs' : ''}</div>
    </div>`;
  }

  function renderSwipeableSongThumbs(songs, _prefix) {
    if (!songs || songs.length === 0) return '<div class="stub-placeholder">Nothing to show yet</div>';
    return `<div class="rp-thumb-row">${songs.map(songThumbHtml).join('')}</div>`;
  }

  function renderSwipeableArtistThumbs(artists) {
    if (!artists || artists.length === 0) return '<div class="stub-placeholder">Nothing to show yet</div>';
    return `<div class="rp-thumb-row">${artists.map(artistThumbHtml).join('')}</div>`;
  }

  function wireSwipeableClicks(containerId, items, onClick) {
    const body = document.getElementById(containerId);
    if (!body) return;
    body.querySelectorAll('.rp-thumb-card').forEach((card, i) => {
      card.style.cursor = 'pointer';
      card.addEventListener('click', () => onClick(items[i]));
    });
  }

  function renderRecentPlaysAll() {
    const songs = state.recentPlays || [];
    const rows = songs.length === 0
      ? '<div class="stub-placeholder">No plays yet</div>'
      : songs.map(s => songListRowHtml(s)).join('');

    contentPanel.innerHTML = subScreenShell('My Recent Plays', `<div class="recent-plays-all">${rows}</div>`);
    wireBackBtn();
    contentPanel.querySelectorAll('.result-row').forEach((row, i) => {
      row.style.cursor = 'pointer';
      row.addEventListener('click', () => showSongPopup(songs[i]));
    });
  }

  function renderArtistsHotHereAll() {
    const artists = state.hotHereArtists || [];
    const rows = artists.length === 0
      ? '<div class="stub-placeholder">Nothing to show yet</div>'
      : artists.map(a => artistListRowHtml(a)).join('');

    contentPanel.innerHTML = subScreenShell('Artists Hot Here', `<div class="recent-plays-all">${rows}</div>`);
    wireBackBtn();
    contentPanel.querySelectorAll('.result-row').forEach((row, i) => {
      row.style.cursor = 'pointer';
      row.addEventListener('click', () => navigateSub('artist-detail', { artistId: artists[i].artistId }));
    });
  }

  function renderSongsHotHereAll() {
    const songs = state.hotHereSongs || [];
    const rows = songs.length === 0
      ? '<div class="stub-placeholder">Nothing to show yet</div>'
      : songs.map(s => songListRowHtml(s)).join('');

    contentPanel.innerHTML = subScreenShell('Songs Hot Here', `<div class="recent-plays-all">${rows}</div>`);
    wireBackBtn();
    contentPanel.querySelectorAll('.result-row').forEach((row, i) => {
      row.style.cursor = 'pointer';
      row.addEventListener('click', () => showSongPopup(songs[i]));
    });
  }

  function songListRowHtml(s) {
    const art = s.albumId != null
      ? `<img class="result-thumb" src="/api/song-library/albums/${s.albumId}/coverArt" alt=""
              onerror="this.outerHTML='<div class=\\'result-thumb-placeholder\\'>&#127925;</div>'">`
      : `<div class="result-thumb-placeholder">&#127925;</div>`;
    return `<div class="result-row">
      ${art}
      <div class="result-info">
        <div class="result-title">${escHtml(s.songName || '')}</div>
        <div class="result-sub">${escHtml(s.artistName || '')}</div>
      </div>
    </div>`;
  }

  function artistListRowHtml(a) {
    const art = (a.albums && a.albums.length && a.albums[0].albumId != null)
      ? `<img class="result-thumb" src="/api/song-library/albums/${a.albums[0].albumId}/coverArt" alt=""
              onerror="this.outerHTML='<div class=\\'result-thumb-placeholder\\'>&#127911;</div>'">`
      : `<div class="result-thumb-placeholder">&#127911;</div>`;
    return `<div class="result-row">
      ${art}
      <div class="result-info">
        <div class="result-title">${escHtml(a.artistName || '')}</div>
        <div class="result-sub">${a.songCount != null ? escHtml(String(a.songCount)) + ' songs' : ''}</div>
      </div>
    </div>`;
  }

  // ── Header data loaders ─────────────────────────────────────────────────
  async function loadCredits(widget) {
    if (!state.token) {
      state.numCredits = 0;
      if (widget) widget.textContent = 'Credits: 0';
      return;
    }
    try {
      const profile = await api('/api/users/me');
      state.numCredits = profile.numCredits ?? 0;
      if (widget) widget.textContent = `Credits: ${state.numCredits}`;
    } catch {
      state.numCredits = 0;
      if (widget) widget.textContent = 'Credits: 0';
    }
  }

  async function loadNowPlaying(widget) {
    if (!widget) return;
    try {
      const song = await api('/api/song-player/nowPlayingSong');
      setNowPlayingWidget(widget, song);
    } catch {
      setNowPlayingWidget(widget, null);
    }
  }

  function setNowPlayingWidget(widget, song) {
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
  }

  // ── Add Funds tab content ───────────────────────────────────────────────
  async function loadAddFunds(container) {
    container.innerHTML = '<div class="stub-placeholder">Loading packages…</div>';
    try {
      const packages = await api('/api/users/credit-packages');
      renderAddFundsContent(container, packages);
    } catch {
      container.innerHTML = '<div class="stub-placeholder">Could not load packages.</div>';
    }
  }

  function renderAddFundsContent(container, packages) {
    let selectedId = packages[0]?.id || '';

    function packageCardHtml(pkg) {
      const selected = pkg.id === selectedId;
      return `
        <div class="package-card ${selected ? 'selected' : ''}" data-id="${pkg.id}">
          <input type="radio" class="package-radio" name="package" value="${pkg.id}"
                 ${selected ? 'checked' : ''}>
          <span class="package-coin-icon">&#129689;</span>
          <div class="package-details">
            <div class="package-credits-amount"><strong>${pkg.credits}</strong> Credits</div>
            <span class="package-bonus-tag">+${pkg.bonusCredits} BONUS CREDITS *</span>
          </div>
          <div class="package-price-box">
            ${pkg.badge ? `<div class="package-badge-label">&#11088; ${pkg.badge}</div>` : ''}
            <div class="package-price-amount">$${Number(pkg.priceUsd).toFixed(0)}</div>
          </div>
        </div>`;
    }

    container.innerHTML = `
      <div class="add-funds-content">
        <div id="packagesContainer">
          ${packages.map(packageCardHtml).join('')}
        </div>
        <p class="bonus-note">* Bonus credits are earned when paid credits are spent in the same night</p>

        <div class="payment-section-title">Default Payment Method</div>
        <div class="payment-method-card">
          <span class="payment-gpay-icon">G Pay</span>
          <div class="payment-method-info">
            <div class="payment-method-name">Google Pay</div>
            <div class="payment-method-number">6325</div>
          </div>
          <button class="payment-remove-btn">Remove</button>
        </div>

        <button class="add-funds-action-btn" id="addFundsBtn">Add Funds</button>
      </div>`;

    container.querySelectorAll('.package-card').forEach((card) => {
      card.addEventListener('click', () => {
        selectedId = card.dataset.id;
        container.querySelectorAll('.package-card').forEach((c) => {
          c.classList.toggle('selected', c.dataset.id === selectedId);
          c.querySelector('.package-radio').checked = c.dataset.id === selectedId;
        });
      });
    });

    document.getElementById('addFundsBtn').addEventListener('click', async () => {
      const btn = document.getElementById('addFundsBtn');
      btn.disabled = true;
      btn.textContent = 'Processing…';
      try {
        await api('/api/users/add-funds', { method: 'POST', body: JSON.stringify({ packageId: selectedId }) });
        btn.textContent = 'Done!';
      } catch (err) {
        btn.textContent = 'Not yet available';
        setTimeout(() => { btn.textContent = 'Add Funds'; btn.disabled = false; }, 2000);
      }
    });
  }

  // ── Sub-screen: My Account ──────────────────────────────────────────────
  async function renderMyAccount(_params = {}) {
    contentPanel.innerHTML = subScreenShell('My Account', '<div class="stub-placeholder">Loading…</div>');
    wireBackBtn();

    let profile;
    try {
      profile = await api('/api/users/me');
    } catch {
      contentPanel.querySelector('.sub-content').innerHTML = '<div class="stub-placeholder">Could not load account.</div>';
      return;
    }

    const balance = profile.balanceUsd != null ? `$${Number(profile.balanceUsd).toFixed(2)} (USD)` : `${profile.numCredits} Credits`;

    contentPanel.querySelector('.sub-content').innerHTML = `
      <div class="account-funds-box">
        <div class="funds-icon">&#128176;</div>
        <div>
          <div class="funds-label">Current Funds:</div>
          <div class="funds-amount">${balance}</div>
        </div>
      </div>

      <div class="menu-section-label">Personal</div>
      <div class="menu-list">
        <button class="menu-row" id="manageAccountBtn">
          <span class="menu-row-icon">&#128100;</span>
          <span class="menu-row-label">Manage Account</span>
          <span class="menu-row-arrow">&#8250;</span>
        </button>
        <button class="menu-row" id="transactionHistoryBtn">
          <span class="menu-row-icon">&#128203;</span>
          <span class="menu-row-label">Transaction History</span>
          <span class="menu-row-arrow">&#8250;</span>
        </button>
      </div>

      <div class="menu-section-label">General</div>
      <div class="menu-list">
        <button class="menu-row" id="helpBtn">
          <span class="menu-row-icon">&#10067;</span>
          <span class="menu-row-label">Help</span>
          <span class="menu-row-arrow">&#8250;</span>
        </button>
        <button class="menu-row" id="settingsBtn">
          <span class="menu-row-icon">&#9881;</span>
          <span class="menu-row-label">Settings</span>
          <span class="menu-row-arrow">&#8250;</span>
        </button>
        <button class="menu-row" id="termsBtn">
          <span class="menu-row-icon">&#128196;</span>
          <span class="menu-row-label">Terms and Conditions</span>
          <span class="menu-row-arrow">&#8250;</span>
        </button>
        <button class="menu-row" id="privacyBtn">
          <span class="menu-row-icon">&#128274;</span>
          <span class="menu-row-label">Privacy Policy</span>
          <span class="menu-row-arrow">&#8250;</span>
        </button>
      </div>

      <button class="menu-list logout-row" id="logoutBtn">Log Out</button>`;

    document.getElementById('manageAccountBtn').addEventListener('click', () => navigateSub('manage-account', { profile }));
    document.getElementById('transactionHistoryBtn').addEventListener('click', () => navigateSub('transaction-history'));
    document.getElementById('helpBtn').addEventListener('click', () => navigateSub('help'));
    document.getElementById('settingsBtn').addEventListener('click', () => navigateSub('settings'));
    document.getElementById('termsBtn').addEventListener('click', () => navigateSub('terms'));
    document.getElementById('privacyBtn').addEventListener('click', () => navigateSub('privacy'));
    document.getElementById('logoutBtn').addEventListener('click', () => {
      clearAuth();
      renderMain('music');
    });
  }

  // ── Sub-screen: Manage Account ──────────────────────────────────────────
  function renderManageAccount(params = {}) {
    contentPanel.innerHTML = subScreenShell('Manage Account', `
      <div class="menu-list">
        <button class="menu-row" id="userProfileBtn">
          <span class="menu-row-icon">&#128100;</span>
          <span class="menu-row-label">User Profile</span>
          <span class="menu-row-arrow">&#8250;</span>
        </button>
        <button class="menu-row" id="changePasswordBtn">
          <span class="menu-row-icon">&#128272;</span>
          <span class="menu-row-label">Password</span>
          <span class="menu-row-arrow">&#8250;</span>
        </button>
        <button class="menu-row danger" id="deleteAccountBtn">
          <span class="menu-row-icon danger-icon">&#128465;</span>
          <span class="menu-row-label">Delete Account</span>
          <span class="menu-row-arrow">&#8250;</span>
        </button>
      </div>`);

    wireBackBtn();
    document.getElementById('userProfileBtn').addEventListener('click', () => navigateSub('user-profile', params));
    document.getElementById('changePasswordBtn').addEventListener('click', () => navigateSub('change-password'));
    document.getElementById('deleteAccountBtn').addEventListener('click', () => navigateSub('delete-account'));
  }

  // ── Sub-screen: User Profile ────────────────────────────────────────────
  async function renderUserProfile(params = {}) {
    let profile = params.profile;
    if (!profile) {
      try { profile = await api('/api/users/me'); } catch { profile = {}; }
    }

    contentPanel.innerHTML = subScreenShell('User Profile', `
      <div class="form-group">
        <label class="form-label">Email Address</label>
        <input class="form-input" type="email" id="profileEmail" value="${profile.emailAddress || ''}" readonly>
      </div>
      <div class="form-group">
        <label class="form-label">First Name</label>
        <input class="form-input" type="text" id="profileFirstName" value="${profile.firstName || ''}">
      </div>
      <div class="form-group">
        <label class="form-label">Last Name</label>
        <input class="form-input" type="text" id="profileLastName" value="${profile.lastName || ''}">
      </div>
      <div class="form-spacer"></div>
      <div class="form-actions">
        <button class="form-btn cancel" id="cancelProfileBtn">Cancel</button>
        <button class="form-btn save" id="saveProfileBtn">Save</button>
      </div>`);

    wireBackBtn();
    document.getElementById('cancelProfileBtn').addEventListener('click', goBack);
    document.getElementById('saveProfileBtn').addEventListener('click', async () => {
      const btn = document.getElementById('saveProfileBtn');
      btn.disabled = true;
      btn.textContent = 'Saving…';
      try {
        await api('/api/users/me', {
          method: 'PUT',
          body: JSON.stringify({
            firstName: document.getElementById('profileFirstName').value,
            lastName: document.getElementById('profileLastName').value,
          }),
        });
        goBack();
      } catch {
        btn.textContent = 'Error — try again';
        btn.disabled = false;
      }
    });
  }

  // ── Sub-screen: Change Password ─────────────────────────────────────────
  function renderChangePassword() {
    contentPanel.innerHTML = subScreenShell('Password', `
      <div class="form-group">
        <label class="form-label">Current Password</label>
        <input class="form-input" type="password" id="currentPassword" placeholder="Enter current password">
      </div>
      <div class="form-group">
        <label class="form-label">New Password</label>
        <input class="form-input" type="password" id="newPassword" placeholder="Enter new password">
      </div>
      <div class="form-group">
        <label class="form-label">Confirm New Password</label>
        <input class="form-input" type="password" id="confirmPassword" placeholder="Confirm new password">
      </div>
      <div id="pwError" class="form-error"></div>
      <div class="form-spacer"></div>
      <div class="form-actions">
        <button class="form-btn cancel" id="cancelPwBtn">Cancel</button>
        <button class="form-btn save" id="savePwBtn">Save</button>
      </div>`);

    wireBackBtn();
    document.getElementById('cancelPwBtn').addEventListener('click', goBack);
    document.getElementById('savePwBtn').addEventListener('click', async () => {
      const current = document.getElementById('currentPassword').value;
      const newPw = document.getElementById('newPassword').value;
      const confirm = document.getElementById('confirmPassword').value;
      const errEl = document.getElementById('pwError');
      errEl.textContent = '';
      if (newPw !== confirm) { errEl.textContent = 'New passwords do not match.'; return; }
      if (newPw.length < 6) { errEl.textContent = 'Password must be at least 6 characters.'; return; }
      const btn = document.getElementById('savePwBtn');
      btn.disabled = true; btn.textContent = 'Saving…';
      try {
        await api('/api/users/change-password', {
          method: 'POST',
          body: JSON.stringify({ currentPassword: current, newPassword: newPw }),
        });
        goBack();
      } catch (err) {
        errEl.textContent = 'Failed to change password. Check your current password.';
        btn.disabled = false; btn.textContent = 'Save';
      }
    });
  }

  // ── Sub-screen: Delete Account ──────────────────────────────────────────
  function renderDeleteAccount() {
    contentPanel.innerHTML = subScreenShell('Delete Account', `
      <div class="delete-confirm-box">
        <div class="delete-warning-icon">&#9888;&#65039;</div>
        <p class="delete-warning-text">
          Are you sure you want to delete your account?<br>
          This action <strong>cannot be undone</strong>.
        </p>
      </div>
      <div id="deleteError" class="form-error"></div>
      <div class="form-actions">
        <button class="form-btn cancel" id="cancelDeleteBtn">Cancel</button>
        <button class="form-btn delete-btn" id="confirmDeleteBtn">Delete Account</button>
      </div>`);

    wireBackBtn();
    document.getElementById('cancelDeleteBtn').addEventListener('click', goBack);
    document.getElementById('confirmDeleteBtn').addEventListener('click', async () => {
      const btn = document.getElementById('confirmDeleteBtn');
      btn.disabled = true; btn.textContent = 'Deleting…';
      try {
        await api('/api/users/me', { method: 'DELETE' });
        clearAuth();
        renderMain('music');
      } catch {
        document.getElementById('deleteError').textContent = 'Delete account not yet available.';
        btn.disabled = false; btn.textContent = 'Delete Account';
      }
    });
  }

  // ── Sub-screen: Generic stub ────────────────────────────────────────────
  function renderStub(title) {
    contentPanel.innerHTML = subScreenShell(title,
      `<div class="stub-placeholder">${title} — coming soon</div>`);
    wireBackBtn();
  }

  // ── Auth screens ────────────────────────────────────────────────────────
  function renderLogin(errorMessage) {
    contentPanel.innerHTML = `
      <div class="centered-view">
        <div class="auth-box">
          <div class="auth-logo"><img src="/images/JukeANatorLogo.png" alt="JukeANator"></div>
          <h1>Login</h1>
          <form id="loginForm">
            ${errorMessage ? `<div class="error-msg">${errorMessage}</div>` : ''}
            <label>Email <input type="email" id="loginEmail" autocomplete="email" required></label>
            <label>Password <input type="password" id="loginPassword" autocomplete="current-password" required></label>
            <button type="submit" class="auth-btn">Login</button>
            <button type="button" id="showRegisterBtn" class="auth-btn">Create Account</button>
            <button type="button" id="loginCancelBtn" class="auth-btn">Cancel</button>
          </form>
        </div>
      </div>`;

    document.getElementById('loginCancelBtn').addEventListener('click', () => {
      state.pendingScreen = null;
      renderMain(state.currentMainTab);
    });
    document.getElementById('showRegisterBtn').addEventListener('click', () => renderRegister());
    document.getElementById('loginForm').addEventListener('submit', async (e) => {
      e.preventDefault();
      try {
        const auth = await api('/api/users/login', {
          method: 'POST',
          body: JSON.stringify({
            emailAddress: document.getElementById('loginEmail').value,
            password: document.getElementById('loginPassword').value,
          }),
        });
        setAuth(auth);
        afterLogin();
      } catch {
        renderLogin('Login failed. Check your email and password.');
      }
    });
  }

  function renderRegister(errorMessage) {
    contentPanel.innerHTML = `
      <div class="centered-view">
        <div class="auth-box">
          <div class="auth-logo"><img src="/images/JukeANatorLogo.png" alt="JukeANator"></div>
          <h1>Create Account</h1>
          <form id="registerForm" autocomplete="off">
            ${errorMessage ? `<div class="error-msg">${errorMessage}</div>` : ''}
            <label>First name <input type="text" id="registerFirstName" autocomplete="off" required></label>
            <label>Last name <input type="text" id="registerLastName" autocomplete="off" required></label>
            <label>Email <input type="email" id="registerEmail" autocomplete="off" required></label>
            <label>Password <input type="password" id="registerPassword" autocomplete="new-password" required></label>
            <button type="submit" class="auth-btn">Submit</button>
            <button type="button" id="registerCancelBtn" class="auth-btn">Cancel</button>
          </form>
        </div>
      </div>`;

    document.getElementById('registerCancelBtn').addEventListener('click', () => renderLogin());
    document.getElementById('registerForm').addEventListener('submit', async (e) => {
      e.preventDefault();
      try {
        const auth = await api('/api/users/register', {
          method: 'POST',
          body: JSON.stringify({
            firstName: document.getElementById('registerFirstName').value,
            lastName: document.getElementById('registerLastName').value,
            emailAddress: document.getElementById('registerEmail').value,
            password: document.getElementById('registerPassword').value,
          }),
        });
        setAuth(auth);
        afterLogin();
      } catch {
        renderRegister('Could not create account. That email may already be registered.');
      }
    });
  }

  function afterLogin() {
    state.pendingScreen = null;
    renderMain('music');
  }

  // ── Search ──────────────────────────────────────────────────────────────

  // On-screen keyboard layout — AMI-style 4-row layout
  const KBD_ABC = [
    ['q','w','e','r','t','y','u','i','o','p'],
    ['a','s','d','f','g','h','j','k','l'],
    ['⇧','z','x','c','v','b','n','m','⌫'],
  ];
  const KBD_NUM = [
    ['1','2','3','4','5','6','7','8','9','0'],
    ['!','@','#','$','%','^','&','*','"',"'"],
    ['(',')',  '[',']','/','\\','?',':',';','⌫'],
  ];

  function buildKeyboard(mode, shiftState) {
    const layout = mode === 'abc' ? KBD_ABC : KBD_NUM;
    const caps = mode === 'abc' && shiftState !== 'none';

    function keyHtml(k) {
      if (k === '⌫') return `<button class="kbd-key kbd-back" data-key="BACK">⌫</button>`;
      if (k === '⇧') {
        const activeClass = shiftState === 'locked' ? ' kbd-shift-locked' : shiftState === 'once' ? ' kbd-shift-active' : '';
        return `<button class="kbd-key kbd-shift${activeClass}" data-key="SHIFT">⇧</button>`;
      }
      const label = caps ? k.toUpperCase() : k;
      return `<button class="kbd-key" data-key="${k}">${label}</button>`;
    }

    const rows = layout.map(row =>
      `<div class="kbd-row">${row.map(keyHtml).join('')}</div>`
    ).join('');

    const toggleLabel = mode === 'abc' ? '?123' : 'ABC';
    const actionRow = `<div class="kbd-row kbd-action-row">
      <button class="kbd-key kbd-toggle" data-key="TOGGLE">${toggleLabel}</button>
      <button class="kbd-key kbd-punct" data-key=",">,</button>
      <button class="kbd-key kbd-space" data-key=" "></button>
      <button class="kbd-key kbd-punct" data-key=".">.</button>
      <button class="kbd-key kbd-search" id="kbdSearchBtn">&#128269; Search</button>
    </div>`;

    return rows + actionRow;
  }

  async function loadSearchHistory() {
    // Prefer the copy already fetched by loadHomePage to avoid a second round-trip.
    if (state.searchHistory) return state.searchHistory;
    if (state.token) {
      try { return await api('/api/users/search-history'); } catch { /* fall through */ }
    }
    try { return JSON.parse(localStorage.getItem('searchHistory') || '[]'); } catch { return []; }
  }

  async function saveSearchQuery(query) {
    state.searchHistory = null; // invalidate cache so next loadSearchHistory re-fetches
    if (state.token) {
      try { await api('/api/users/search-history', { method: 'POST', body: JSON.stringify({ query }) }); } catch { /* ignore */ }
    } else {
      try {
        let h = JSON.parse(localStorage.getItem('searchHistory') || '[]');
        h = h.filter(q => q !== query);
        h.unshift(query);
        if (h.length > 10) h = h.slice(0, 10);
        localStorage.setItem('searchHistory', JSON.stringify(h));
      } catch { /* ignore */ }
    }
  }

  async function deleteSearchHistoryItem(index) {
    state.searchHistory = null; // invalidate cache
    if (state.token) {
      try { await api(`/api/users/search-history/${index}`, { method: 'DELETE' }); } catch { /* ignore */ }
    } else {
      try {
        let h = JSON.parse(localStorage.getItem('searchHistory') || '[]');
        h.splice(index, 1);
        localStorage.setItem('searchHistory', JSON.stringify(h));
      } catch { /* ignore */ }
    }
  }

  async function renderSearchEntry() {
    let kbdMode = 'abc';
    let buffer = '';
    let shiftState = 'none'; // 'none' | 'once' | 'locked'
    let lastShiftClick = 0;

    const history = await loadSearchHistory();

    function historyHtml(items) {
      if (!items.length) return '<div class="search-empty">No recent searches</div>';
      return items.map((q, i) => `
        <div class="search-history-item" data-index="${i}">
          <span class="search-history-clock">&#128336;</span>
          <span class="search-history-text">${escHtml(q)}</span>
          <button class="search-history-remove" data-index="${i}" title="Remove">&#10005;</button>
        </div>`).join('');
    }

    function updateDisplay() {
      const display = document.getElementById('searchDisplay');
      const clearBtn = document.getElementById('searchClearBtn');
      if (display) {
        display.textContent = buffer || 'Search for music';
        display.className = 'search-input-display' + (buffer ? '' : ' placeholder');
      }
      if (clearBtn) clearBtn.style.display = buffer ? 'flex' : 'none';
    }

    function render() {
      contentPanel.innerHTML = `
        <div class="search-screen">
          <div class="search-top-bar">
            <button class="search-back-btn" id="searchBackBtn">&#8592;</button>
            <div class="search-input-bar">
              <span class="search-icon">&#128269;</span>
              <div class="search-input-display ${buffer ? '' : 'placeholder'}" id="searchDisplay">
                ${buffer ? escHtml(buffer) : 'Search for music'}
              </div>
              <button class="search-clear-btn" id="searchClearBtn" style="display:${buffer ? 'flex' : 'none'}">&#10005;</button>
            </div>
          </div>
          <div class="search-history-list" id="searchHistoryList">
            ${historyHtml(history)}
          </div>
          <div class="onscreen-keyboard" id="onscreenKbd">
            ${buildKeyboard(kbdMode, shiftState)}
          </div>
        </div>`;

      document.getElementById('searchBackBtn').addEventListener('click', () => goBack());
      document.getElementById('searchClearBtn').addEventListener('click', () => { buffer = ''; updateDisplay(); });

      // History item click — run search from history
      document.getElementById('searchHistoryList').addEventListener('click', async (e) => {
        const removeBtn = e.target.closest('.search-history-remove');
        if (removeBtn) {
          const idx = parseInt(removeBtn.dataset.index, 10);
          history.splice(idx, 1);
          await deleteSearchHistoryItem(idx);
          document.getElementById('searchHistoryList').innerHTML = historyHtml(history);
          // re-index data-index attributes
          document.querySelectorAll('.search-history-item').forEach((el, i) => {
            el.dataset.index = i;
            el.querySelector('.search-history-remove').dataset.index = i;
          });
          return;
        }
        const item = e.target.closest('.search-history-item');
        if (item) {
          buffer = history[parseInt(item.dataset.index, 10)];
          await executeSearch(buffer);
        }
      });

      // Keyboard
      document.getElementById('onscreenKbd').addEventListener('click', async (e) => {
        const btn = e.target.closest('.kbd-key');
        if (!btn) return;
        const key = btn.dataset.key;
        if (btn.id === 'kbdSearchBtn') {
          if (buffer.trim()) await executeSearch(buffer.trim());
          return;
        }
        if (key === 'SHIFT') {
          const now = Date.now();
          const isDoubleClick = now - lastShiftClick < 350;
          lastShiftClick = now;
          if (shiftState === 'locked') {
            shiftState = 'none';
          } else if (isDoubleClick) {
            shiftState = 'locked';
          } else if (shiftState === 'once') {
            shiftState = 'none';
          } else {
            shiftState = 'once';
          }
          document.getElementById('onscreenKbd').innerHTML = buildKeyboard(kbdMode, shiftState);
          return;
        }
        if (key === 'BACK') {
          buffer = buffer.slice(0, -1);
        } else if (key === 'TOGGLE') {
          kbdMode = kbdMode === 'abc' ? 'num' : 'abc';
          shiftState = 'none';
          document.getElementById('onscreenKbd').innerHTML = buildKeyboard(kbdMode, shiftState);
          return;
        } else {
          const isLetter = kbdMode === 'abc' && key.length === 1 && /[a-z]/i.test(key);
          buffer += (isLetter && shiftState !== 'none') ? key.toUpperCase() : key;
          if (shiftState === 'once') {
            shiftState = 'none';
            document.getElementById('onscreenKbd').innerHTML = buildKeyboard(kbdMode, shiftState);
          }
        }
        updateDisplay();
      });
    }

    async function executeSearch(query) {
      await saveSearchQuery(query);
      try {
        const result = await api(`/api/song-library/search?searchFor=${encodeURIComponent(query)}&limit=20`);
        navigateSub('search-results', { query, result });
      } catch {
        navigateSub('search-results', { query, result: { artists: [], albums: [], songs: [] } });
      }
    }

    render();
  }

  function escHtml(str) {
    return str.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
  }

  function coverArtHtml(albumId, fallbackEmoji) {
    if (albumId != null) {
      return `<img class="result-thumb" src="/api/song-library/albums/${albumId}/coverArt"
               alt="" onerror="this.outerHTML=\`<div class='result-thumb-placeholder'>${fallbackEmoji}</div>\`">`;
    }
    return `<div class="result-thumb-placeholder">${fallbackEmoji}</div>`;
  }

  function artistResultRow(a) {
    const thumb = a.artistId != null
      ? `<img class="result-thumb" src="/api/song-library/artists/${a.artistId}/coverArt"
               alt="" onerror="this.outerHTML=\`<div class='result-thumb-placeholder'>&#127911;</div>\`">`
      : `<div class="result-thumb-placeholder">&#127911;</div>`;
    return `<div class="result-row artist-result-row" data-artist-id="${a.artistId ?? ''}">
      ${thumb}
      <div class="result-info">
        <div class="result-title">${escHtml(a.artistName || '')}</div>
        <div class="result-sub">Artist</div>
      </div>
    </div>`;
  }

  function albumResultRow(a) {
    const thumb = coverArtHtml(a.albumId, '&#128191;');
    return `<div class="result-row album-result-row" data-album-id="${a.albumId ?? ''}">
      ${thumb}
      <div class="result-info">
        <div class="result-title">${escHtml(a.name || '')}</div>
        <div class="result-sub">Album &middot; ${escHtml(a.artistName || '')}</div>
      </div>
    </div>`;
  }

  function songResultRow(s) {
    const thumb = coverArtHtml(s.albumId, '&#127925;');
    const name = s.songName || s.title || '';
    const encoded = encodeURIComponent(JSON.stringify(s));
    return `<div class="result-row song-result-row" data-song="${encoded}">
      ${thumb}
      <div class="result-info">
        <div class="result-title">${escHtml(name)}</div>
        <div class="result-sub">Song &middot; ${escHtml(s.artistName || '')}</div>
      </div>
      <button class="result-menu-btn" title="More options">&#8942;</button>
    </div>`;
  }

  function renderSearchResults(query, result) {
    const artists = result.artists || [];
    const albums  = result.albums  || [];
    const songs   = result.songs   || [];

    const TOP_N = 5;
    const topArtists = artists.slice(0, TOP_N);
    const topAlbums  = albums.slice(0,  TOP_N);
    const topSongs   = songs.slice(0,   TOP_N);

    function topPanelHtml() {
      if (!topArtists.length && !topAlbums.length && !topSongs.length) {
        return `<div class="search-empty">No results found</div>`;
      }
      let html = '';
      if (topArtists.length) {
        html += `<div class="result-section-label">Artists</div>` + topArtists.map(artistResultRow).join('');
      }
      if (topAlbums.length) {
        html += `<div class="result-section-label">Albums</div>` + topAlbums.map(albumResultRow).join('');
      }
      if (topSongs.length) {
        html += `<div class="result-section-label">Songs</div>` + topSongs.map(songResultRow).join('');
      }
      return html;
    }

    contentPanel.innerHTML = `
      <div class="search-results-screen">
        <div class="search-top-bar">
          <button class="search-back-btn" id="searchResultsBackBtn">&#8592;</button>
          <div class="search-input-bar">
            <span class="search-icon">&#128269;</span>
            <div class="search-input-display">${escHtml(query)}</div>
          </div>
        </div>
        <div class="search-tabs-bar">
          <button class="search-tab active" data-tab="top">Top</button>
          <button class="search-tab" data-tab="artists">Artists</button>
          <button class="search-tab" data-tab="albums">Albums</button>
          <button class="search-tab" data-tab="songs">Songs</button>
        </div>
        <div class="search-tab-panels">
          <div class="search-tab-panel active" id="tab-top">${topPanelHtml()}</div>
          <div class="search-tab-panel" id="tab-artists">
            ${artists.length ? artists.map(artistResultRow).join('') : '<div class="search-empty">No artists found</div>'}
          </div>
          <div class="search-tab-panel" id="tab-albums">
            ${albums.length ? albums.map(albumResultRow).join('') : '<div class="search-empty">No albums found</div>'}
          </div>
          <div class="search-tab-panel" id="tab-songs">
            ${songs.length ? songs.map(songResultRow).join('') : '<div class="search-empty">No songs found</div>'}
          </div>
        </div>
      </div>`;

    document.getElementById('searchResultsBackBtn').addEventListener('click', () => goBack());

    // Artist / song row clicks — scoped to this screen's own wrapper (not the
    // persistent contentPanel) so listeners don't accumulate across re-renders
    // (e.g. when navigating back to search results).
    contentPanel.querySelector('.search-results-screen').addEventListener('click', (e) => {
      const artistRow = e.target.closest('.artist-result-row');
      if (artistRow) {
        const artistId = artistRow.dataset.artistId;
        if (artistId) navigateSub('artist-detail', { artistId: Number(artistId) });
        return;
      }
      const albumRow = e.target.closest('.album-result-row');
      if (albumRow) {
        const albumId = albumRow.dataset.albumId;
        if (albumId) navigateSub('album-detail', { albumId: Number(albumId) });
        return;
      }
      const row = e.target.closest('.song-result-row');
      if (!row) return;
      try {
        const song = JSON.parse(decodeURIComponent(row.dataset.song));
        showSongPopup(song);
      } catch { /* ignore */ }
    });

    // Tab switching
    contentPanel.querySelectorAll('.search-tab').forEach(tab => {
      tab.addEventListener('click', () => {
        contentPanel.querySelectorAll('.search-tab').forEach(t => t.classList.remove('active'));
        contentPanel.querySelectorAll('.search-tab-panel').forEach(p => p.classList.remove('active'));
        tab.classList.add('active');
        document.getElementById('tab-' + tab.dataset.tab).classList.add('active');
      });
    });
  }

  // ── Artist detail screen ────────────────────────────────────────────────

  function artistSongRow(s, i) {
    const thumb = coverArtHtml(s.albumId, '&#127925;');
    return `<div class="result-row" data-index="${i}">
      ${thumb}
      <div class="result-info">
        <div class="result-title">${escHtml(s.songName || '')}</div>
        <div class="result-sub">${escHtml(s.albumName || '')}</div>
      </div>
      ${popularityBarsHtml(s.numPlays)}
    </div>`;
  }

  function artistAlbumRow(al) {
    const thumb = coverArtHtml(al.albumId, '&#128191;');
    const year = (al.releaseDate || '').slice(0, 4);
    return `<div class="result-row" data-album-id="${al.albumId ?? ''}">
      ${thumb}
      <div class="result-info">
        <div class="result-title">${escHtml(al.albumName || '')}</div>
        <div class="result-sub">${escHtml(year)}</div>
      </div>
    </div>`;
  }

  async function renderArtistDetail(params = {}) {
    contentPanel.innerHTML = subScreenShell('Artist', '<div class="stub-placeholder">Loading…</div>');
    wireBackBtn();

    let artist;
    try {
      artist = await api(`/api/song-library/artists/${params.artistId}`);
    } catch {
      contentPanel.querySelector('.sub-content').innerHTML = '<div class="stub-placeholder">Could not load artist.</div>';
      return;
    }

    const albums = [...(artist.albums || [])].sort((a, b) => (b.releaseDate || '').localeCompare(a.releaseDate || ''));
    const songs = (artist.albums || [])
      .flatMap(al => al.songs || [])
      .sort((a, b) => (b.numPlays || 0) - (a.numPlays || 0));

    contentPanel.querySelector('.sub-title').textContent = artist.artistName || '';
    contentPanel.querySelector('.sub-content').innerHTML = `
      <div class="artist-detail-counts">${artist.songCount ?? songs.length} Songs, ${artist.albumCount ?? albums.length} Albums</div>
      <div class="artist-tabs-bar">
        <button class="artist-tab active" data-tab="songs">Songs</button>
        <button class="artist-tab" data-tab="albums">Albums</button>
      </div>
      <div class="artist-sort-label">
        <span id="artistSortText">Sorted by Popularity</span>
        <span class="artist-sort-icon">&#8693;</span>
      </div>
      <div class="artist-tab-panels">
        <div class="artist-tab-panel active" id="artist-tab-songs">
          ${songs.length ? songs.map(artistSongRow).join('') : '<div class="search-empty">No songs found</div>'}
        </div>
        <div class="artist-tab-panel" id="artist-tab-albums">
          ${albums.length ? albums.map(artistAlbumRow).join('') : '<div class="search-empty">No albums found</div>'}
        </div>
      </div>`;

    contentPanel.querySelectorAll('.artist-tab').forEach(tab => {
      tab.addEventListener('click', () => {
        contentPanel.querySelectorAll('.artist-tab').forEach(t => t.classList.remove('active'));
        contentPanel.querySelectorAll('.artist-tab-panel').forEach(p => p.classList.remove('active'));
        tab.classList.add('active');
        document.getElementById('artist-tab-' + tab.dataset.tab).classList.add('active');
        document.getElementById('artistSortText').textContent = 'Sorted by Popularity';
      });
    });

    contentPanel.querySelector('#artist-tab-songs').addEventListener('click', (e) => {
      const row = e.target.closest('.result-row');
      if (!row) return;
      const song = songs[parseInt(row.dataset.index, 10)];
      if (song) showSongPopup(song);
    });

    contentPanel.querySelector('#artist-tab-albums').addEventListener('click', (e) => {
      const row = e.target.closest('.result-row');
      if (!row) return;
      const albumId = row.dataset.albumId;
      if (albumId) navigateSub('album-detail', { albumId: Number(albumId) });
    });
  }

  // ── Album detail screen ─────────────────────────────────────────────────

  // Mirrors SongTrackCellRenderer.barsForPlays thresholds in the JFC/Swing UI —
  // keep the two in sync if the popularity model changes.
  const POPULARITY_T1 = 10, POPULARITY_T2 = 25, POPULARITY_T3 = 50;

  function barsForPlays(plays) {
    if (plays >= POPULARITY_T3) return 3;
    if (plays >= POPULARITY_T2) return 2;
    if (plays >= POPULARITY_T1) return 1;
    return 0;
  }

  function popularityBarsHtml(numPlays) {
    const active = barsForPlays(numPlays || 0);
    const bars = [1, 2, 3]
      .map(n => `<span class="popularity-bar${n <= active ? ' active' : ''}"></span>`)
      .join('');
    return `<div class="popularity-bars">${bars}</div>`;
  }

  function albumTrackRow(song, i) {
    const num = song.trackNumber != null ? song.trackNumber : i + 1;
    return `<div class="album-track-row" data-index="${i}">
      ${popularityBarsHtml(song.numPlays)}
      <div class="album-track-num">${String(num).padStart(2, '0')}</div>
      <div class="album-track-title">${escHtml(song.songName || '')}</div>
    </div>`;
  }

  async function renderAlbumDetail(params = {}) {
    contentPanel.innerHTML = subScreenShell('Album', '<div class="stub-placeholder">Loading…</div>');
    wireBackBtn();

    let album;
    try {
      album = await api(`/api/song-library/albums/${params.albumId}`);
    } catch {
      contentPanel.querySelector('.sub-content').innerHTML = '<div class="stub-placeholder">Could not load album.</div>';
      return;
    }

    const songs = [...(album.songs || [])].sort((a, b) => (a.trackNumber || 0) - (b.trackNumber || 0));
    const year = (album.releaseDate || '').slice(0, 4);
    const coverHtml = album.albumId != null
      ? `<img class="album-detail-cover" src="/api/song-library/albums/${album.albumId}/coverArt"
              alt="" onerror="this.outerHTML=\`<div class='album-detail-cover album-detail-cover-placeholder'>&#128191;</div>\`">`
      : `<div class="album-detail-cover album-detail-cover-placeholder">&#128191;</div>`;

    contentPanel.querySelector('.sub-title').textContent = album.albumName || '';
    contentPanel.querySelector('.sub-content').innerHTML = `
      <div class="album-detail-header">
        ${coverHtml}
        <div class="album-detail-header-info">
          <div class="album-detail-name">${escHtml(album.albumName || '')}</div>
          <div class="album-detail-artist" id="albumDetailArtist">${escHtml(album.artistName || '')}</div>
          <div class="album-detail-meta">${year ? year + ' &middot; ' : ''}${songs.length} Songs</div>
        </div>
      </div>
      <div class="album-track-list">
        ${songs.length ? songs.map(albumTrackRow).join('') : '<div class="search-empty">No songs found</div>'}
      </div>`;

    contentPanel.querySelector('.album-track-list').addEventListener('click', (e) => {
      const row = e.target.closest('.album-track-row');
      if (!row) return;
      const song = songs[parseInt(row.dataset.index, 10)];
      if (song) showSongPopup(song);
    });

    const artistEl = document.getElementById('albumDetailArtist');
    if (artistEl && album.artistId != null) {
      artistEl.classList.add('album-detail-artist-link');
      artistEl.addEventListener('click', () => navigateSub('artist-detail', { artistId: album.artistId }));
    }
  }

  // ── Song bottom-sheet popup ─────────────────────────────────────────────

  let _songPopupTimer = null;

  function dismissSongPopup() {
    clearTimeout(_songPopupTimer);
    const overlay = document.getElementById('songPopupOverlay');
    if (!overlay) return;
    overlay.classList.remove('song-popup-visible');
    setTimeout(() => overlay.remove(), 300);
  }

  function resetSongPopupTimer() {
    clearTimeout(_songPopupTimer);
    _songPopupTimer = setTimeout(dismissSongPopup, 20000);
  }

  async function showSongPopup(song) {
    // Remove any existing popup
    const existing = document.getElementById('songPopupOverlay');
    if (existing) existing.remove();
    clearTimeout(_songPopupTimer);

    const credits = state.numCredits;
    const highest = await api('/api/song-queue/highestPriority').catch(() => 1) || 1;
    const priorityLevel   = highest + 1;
    const costPriority    = priorityLevel * 2;   // Web UI = double the JFC cost
    const canPlay         = credits >= COST_PLAY;
    const canPriority     = credits >= costPriority;

    const name      = escHtml(song.songName || song.title || '');
    const albumName = escHtml(song.albumName || '');
    const artist    = escHtml(song.artistName || '');
    const crawlText = `${albumName} · ${artist}`;

    const thumbHtml = song.albumId != null
      ? `<img class="song-popup-thumb" src="/api/song-library/albums/${song.albumId}/coverArt"
              alt="" onerror="this.outerHTML='<div class=\\'song-popup-thumb song-popup-thumb-placeholder\\'>&#127925;</div>'">`
      : `<div class="song-popup-thumb song-popup-thumb-placeholder">&#127925;</div>`;

    const priorityClass       = canPriority ? 'song-popup-action' : 'song-popup-action song-popup-action--warn';
    const playClass           = canPlay     ? 'song-popup-action' : 'song-popup-action song-popup-action--warn';
    const priorityCreditClass = canPriority ? 'spa-credits'       : 'spa-credits spa-credits--warn';
    const playCreditClass     = canPlay     ? 'spa-credits'       : 'spa-credits spa-credits--warn';

    const overlay = document.createElement('div');
    overlay.id = 'songPopupOverlay';
    overlay.className = 'song-popup-overlay';
    overlay.innerHTML = `
      <div class="song-popup" id="songPopup">
        <div class="song-popup-handle"></div>
        <div class="song-popup-header">
          ${thumbHtml}
          <div class="song-popup-meta">
            <div class="song-popup-title">${name}</div>
            <div class="song-popup-crawl-wrap">
              <div class="song-popup-crawl">${crawlText}&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;${crawlText}</div>
            </div>
          </div>
        </div>
        <div class="${priorityClass}" id="spaPlayPriority">
          <span class="spa-label">Play Priority Song</span>
          <span class="${priorityCreditClass}">${costPriority} Credits${canPriority ? '' : ' ⚠'}</span>
        </div>
        <div class="${playClass}" id="spaPlay">
          <span class="spa-label">Play Song</span>
          <span class="${playCreditClass}">${COST_PLAY} Credits${canPlay ? '' : ' ⚠'}</span>
        </div>
        <div class="song-popup-action song-popup-action--icon" id="spaArtist">
          <span class="spa-icon">&#128100;</span>
          <span class="spa-label">View This Artist</span>
        </div>
        <div class="song-popup-action song-popup-action--icon" id="spaFavorite">
          <span class="spa-icon">&#10084;</span>
          <span class="spa-label" id="spaFavoriteLabel">${state.favoriteSongIds.has(`${song.albumId}_${song.songId}`) ? 'Remove from My Favorites' : 'Add to My Favorites'}</span>
        </div>
        <div class="song-popup-action song-popup-action--icon" id="spaPlaylist">
          <span class="spa-icon">&#127932;</span>
          <span class="spa-label">Add to a Playlist &hellip;</span>
        </div>
      </div>`;

    document.getElementById('app-shell').appendChild(overlay);

    // Slide in after next frame
    requestAnimationFrame(() => {
      requestAnimationFrame(() => overlay.classList.add('song-popup-visible'));
    });

    // Dismiss on overlay background click
    overlay.addEventListener('click', (e) => {
      if (e.target === overlay) dismissSongPopup();
    });

    // Reset timer on any interaction inside popup
    document.getElementById('songPopup').addEventListener('click', resetSongPopupTimer);

    async function submitPlay(priority) {
      try {
        await api('/api/song-queue/addSong', {
          method: 'POST',
          body: JSON.stringify({ albumId: song.albumId, songId: song.songId, priority })
        });
        dismissSongPopup();
      } catch (err) {
        alert('Could not add song to queue: ' + (err.message || err));
      }
    }

    document.getElementById('spaPlayPriority').addEventListener('click', async () => {
      if (!canPriority) { resetSongPopupTimer(); return; }
      resetSongPopupTimer();
      await submitPlay(priorityLevel);
    });

    document.getElementById('spaPlay').addEventListener('click', async () => {
      if (!canPlay) { resetSongPopupTimer(); return; }
      resetSongPopupTimer();
      await submitPlay(1);
    });

    document.getElementById('spaArtist').addEventListener('click', () => {
      dismissSongPopup();
      if (song.artistId != null) navigateSub('artist-detail', { artistId: song.artistId });
    });

    document.getElementById('spaFavorite').addEventListener('click', async () => {
      if (!state.token) { dismissSongPopup(); state.pendingScreen = null; renderLogin(); return; }
      resetSongPopupTimer();
      const key = `${song.albumId}_${song.songId}`;
      const inFavs = state.favoriteSongIds.has(key);
      const label = document.getElementById('spaFavoriteLabel');
      try {
        if (inFavs) {
          await api('/api/users/playlists/favorites/songs', {
            method: 'DELETE',
            body: JSON.stringify({ albumId: song.albumId, songId: song.songId }),
          });
          state.favoriteSongIds.delete(key);
          if (label) label.textContent = 'Add to My Favorites';
        } else {
          await api('/api/users/playlists/favorites/songs', {
            method: 'POST',
            body: JSON.stringify({ albumId: song.albumId, songId: song.songId }),
          });
          state.favoriteSongIds.add(key);
          if (label) label.textContent = 'Remove from My Favorites';
        }
        refreshPlaylistsState();
      } catch (err) {
        alert('Could not update My Favorites: ' + (err.message || err));
      }
    });

    document.getElementById('spaPlaylist').addEventListener('click', () => {
      if (!state.token) { dismissSongPopup(); state.pendingScreen = null; renderLogin(); return; }
      dismissSongPopup();
      showSelectPlaylistSheet(song);
    });

    resetSongPopupTimer();
  }

  // ── Playlist helpers ────────────────────────────────────────────────────

  async function refreshPlaylistsState() {
    if (!state.token) return;
    try {
      const [playlists, favIds] = await Promise.all([
        api('/api/users/playlists').catch(() => []),
        api('/api/users/playlists/favorites/songs').catch(() => []),
      ]);
      state.myPlaylists     = playlists || [];
      state.favoriteSongIds = new Set((favIds || []).map(si => `${si.albumId}_${si.songId}`));
    } catch { /* ignore */ }
  }

  function playlistCoverArtHtml(p, cssClass) {
    const cls = cssClass || 'rp-thumb-img';
    if (p.name === 'My Favorites') {
      return `<div class="${cls}"><img src="/images/MyFavorites_Playlist.png" alt="" style="width:100%;height:100%;object-fit:contain;"></div>`;
    }
    if (p.firstSongAlbumId != null) {
      return `<div class="${cls}"><img src="/api/song-library/albums/${p.firstSongAlbumId}/coverArt" alt=""
        onerror="this.src='/images/Generic_Playlist.png'" style="width:100%;height:100%;object-fit:cover;"></div>`;
    }
    return `<div class="${cls}"><img src="/images/Generic_Playlist.png" alt="" style="width:100%;height:100%;object-fit:contain;"></div>`;
  }

  function playlistTileHtml(p) {
    const count = p.songCount === 1 ? '1 song' : `${p.songCount} songs`;
    return `<div class="rp-thumb-card playlist-tile">
      ${playlistCoverArtHtml(p)}
      <div class="rp-thumb-song">${escHtml(p.name)}</div>
      <div class="rp-thumb-artist">${escHtml(count)}</div>
    </div>`;
  }

  function createNewPlaylistTileHtml() {
    return `<div class="rp-thumb-card playlist-tile create-playlist-tile">
      <div class="rp-thumb-img playlist-tile-create-img">+</div>
      <div class="rp-thumb-song">Create New</div>
      <div class="rp-thumb-artist">&nbsp;</div>
    </div>`;
  }

  function renderPlaylistTileRow(playlists) {
    if (!playlists || playlists.length === 0) {
      return `<div class="rp-thumb-row">${createNewPlaylistTileHtml()}</div>`;
    }
    return `<div class="rp-thumb-row">${playlists.map(playlistTileHtml).join('')}${createNewPlaylistTileHtml()}</div>`;
  }

  function wirePlaylistTileClicks(containerId, playlists) {
    const body = document.getElementById(containerId);
    if (!body) return;
    const tiles = body.querySelectorAll('.playlist-tile');
    tiles.forEach((tile, i) => {
      tile.style.cursor = 'pointer';
      if (tile.classList.contains('create-playlist-tile')) {
        tile.addEventListener('click', () => showCreatePlaylistDialog({ onCreated: () => renderMyPlaylistsAll() }));
      } else {
        tile.addEventListener('click', () => navigateSub('playlist-detail', { playlist: playlists[i] }));
      }
    });
  }

  function renderMyPlaylistsAll() {
    const tiles = (state.myPlaylists || []).map(playlistTileHtml).join('') + createNewPlaylistTileHtml();
    contentPanel.innerHTML = subScreenShell('My Playlists', `
      <div class="playlist-grid">${tiles}</div>`);
    wireBackBtn();

    const playlists = state.myPlaylists || [];
    contentPanel.querySelectorAll('.playlist-tile').forEach((tile, i) => {
      tile.style.cursor = 'pointer';
      if (tile.classList.contains('create-playlist-tile')) {
        tile.addEventListener('click', () => showCreatePlaylistDialog({ onCreated: () => renderMyPlaylistsAll() }));
      } else {
        tile.addEventListener('click', () => navigateSub('playlist-detail', { playlist: playlists[i] }));
      }
    });
  }

  async function renderPlaylistDetail(params = {}) {
    const playlist = params.playlist || {};
    const name = playlist.name || '';

    contentPanel.innerHTML = subScreenShell(escHtml(name), '<div class="stub-placeholder">Loading…</div>');
    wireBackBtn();

    let songs = [];
    try {
      songs = await api(`/api/users/playlists/${encodeURIComponent(name)}/songs`);
    } catch {
      contentPanel.querySelector('.sub-content').innerHTML = '<div class="stub-placeholder">Could not load playlist.</div>';
      return;
    }

    function renderSongs(songList) {
      if (!songList.length) {
        return '<div class="stub-placeholder">No songs yet. Add songs from the song menu.</div>';
      }
      return songList.map((s, i) => `
        <div class="playlist-song-row" data-index="${i}">
          <img class="result-thumb" src="/api/song-library/albums/${s.albumId}/coverArt" alt=""
               onerror="this.outerHTML='<div class=\\'result-thumb-placeholder\\'>&#127925;</div>'">
          <div class="result-info">
            <div class="result-title">${escHtml(s.songName || '')}</div>
            <div class="result-sub">${escHtml(s.artistName || '')}</div>
          </div>
          <div class="playlist-song-controls">
            <button class="playlist-order-btn" data-dir="up" data-index="${i}" title="Move up" ${i === 0 ? 'disabled' : ''}>&#8593;</button>
            <button class="playlist-order-btn" data-dir="down" data-index="${i}" title="Move down" ${i === songList.length - 1 ? 'disabled' : ''}>&#8595;</button>
            <button class="playlist-remove-btn" data-index="${i}" title="Remove">&#10005;</button>
          </div>
        </div>`).join('');
    }

    const subContent = contentPanel.querySelector('.sub-content');
    subContent.innerHTML = `
      <div class="playlist-detail-header">
        ${playlistCoverArtHtml(playlist, 'playlist-detail-cover-wrap')}
        <div class="playlist-detail-info">
          <div class="playlist-detail-name">${escHtml(name)}</div>
          <div class="playlist-detail-count">${songs.length} song${songs.length !== 1 ? 's' : ''}</div>
        </div>
      </div>
      <div class="playlist-song-list" id="playlistSongList">${renderSongs(songs)}</div>`;

    async function moveAndSave(fromIdx, toIdx) {
      const moved = songs.splice(fromIdx, 1)[0];
      songs.splice(toIdx, 0, moved);
      document.getElementById('playlistSongList').innerHTML = renderSongs(songs);
      wireSongListEvents();
      // Update count
      subContent.querySelector('.playlist-detail-count').textContent =
        `${songs.length} song${songs.length !== 1 ? 's' : ''}`;
      try {
        await api(`/api/users/playlists/${encodeURIComponent(name)}/songs`, {
          method: 'PUT',
          body: JSON.stringify(songs.map(s => ({ albumId: s.albumId, songId: s.songId }))),
        });
        await refreshPlaylistsState();
      } catch (err) {
        alert('Could not save order: ' + (err.message || err));
      }
    }

    async function removeAndSave(idx) {
      songs.splice(idx, 1);
      document.getElementById('playlistSongList').innerHTML = renderSongs(songs);
      wireSongListEvents();
      subContent.querySelector('.playlist-detail-count').textContent =
        `${songs.length} song${songs.length !== 1 ? 's' : ''}`;
      try {
        await api(`/api/users/playlists/${encodeURIComponent(name)}/songs`, {
          method: 'PUT',
          body: JSON.stringify(songs.map(s => ({ albumId: s.albumId, songId: s.songId }))),
        });
        await refreshPlaylistsState();
      } catch (err) {
        alert('Could not remove song: ' + (err.message || err));
      }
    }

    function wireSongListEvents() {
      const list = document.getElementById('playlistSongList');
      if (!list) return;
      list.querySelectorAll('.playlist-order-btn').forEach(btn => {
        btn.addEventListener('click', (e) => {
          e.stopPropagation();
          const idx = parseInt(btn.dataset.index, 10);
          const dir = btn.dataset.dir;
          if (dir === 'up' && idx > 0) moveAndSave(idx, idx - 1);
          if (dir === 'down' && idx < songs.length - 1) moveAndSave(idx, idx + 1);
        });
      });
      list.querySelectorAll('.playlist-remove-btn').forEach(btn => {
        btn.addEventListener('click', (e) => {
          e.stopPropagation();
          const idx = parseInt(btn.dataset.index, 10);
          removeAndSave(idx);
        });
      });
      list.querySelectorAll('.playlist-song-row').forEach((row, i) => {
        row.style.cursor = 'pointer';
        row.addEventListener('click', () => showSongPopup(songs[i]));
      });
    }

    wireSongListEvents();
  }

  function showSelectPlaylistSheet(song) {
    const existing = document.getElementById('selectPlaylistOverlay');
    if (existing) existing.remove();

    const nonFavoritePlaylists = (state.myPlaylists || []).filter(
      p => p.name !== 'My Favorites'
    );

    const listHtml = nonFavoritePlaylists.map((p, i) => `
      <div class="select-playlist-row" data-index="${i}">
        ${playlistCoverArtHtml(p, 'select-playlist-thumb')}
        <div class="select-playlist-name">${escHtml(p.name)}</div>
      </div>`).join('');

    const overlay = document.createElement('div');
    overlay.id = 'selectPlaylistOverlay';
    overlay.className = 'song-popup-overlay';
    overlay.innerHTML = `
      <div class="song-popup select-playlist-sheet" id="selectPlaylistSheet">
        <div class="song-popup-handle"></div>
        <div class="select-playlist-title">Select a playlist</div>
        ${listHtml}
        <div class="select-playlist-row create-playlist-row" id="selectPlaylistCreate">
          <div class="select-playlist-thumb select-playlist-create-icon">+</div>
          <div class="select-playlist-name">Create New Playlist</div>
        </div>
      </div>`;

    document.getElementById('app-shell').appendChild(overlay);
    requestAnimationFrame(() => requestAnimationFrame(() => overlay.classList.add('song-popup-visible')));

    overlay.addEventListener('click', (e) => {
      if (e.target === overlay) dismissSelectPlaylistSheet();
    });

    nonFavoritePlaylists.forEach((p, i) => {
      overlay.querySelectorAll('.select-playlist-row:not(.create-playlist-row)')[i]
        ?.addEventListener('click', async () => {
          dismissSelectPlaylistSheet();
          try {
            await api(`/api/users/playlists/${encodeURIComponent(p.name)}/songs`, {
              method: 'POST',
              body: JSON.stringify({ albumId: song.albumId, songId: song.songId }),
            });
            await refreshPlaylistsState();
          } catch (err) {
            alert('Could not add to playlist: ' + (err.message || err));
          }
        });
    });

    document.getElementById('selectPlaylistCreate').addEventListener('click', () => {
      dismissSelectPlaylistSheet();
      showCreatePlaylistDialog({ onCreated: async (name) => {
        try {
          await api(`/api/users/playlists/${encodeURIComponent(name)}/songs`, {
            method: 'POST',
            body: JSON.stringify({ albumId: song.albumId, songId: song.songId }),
          });
          await refreshPlaylistsState();
        } catch { /* ignore */ }
      }});
    });
  }

  function dismissSelectPlaylistSheet() {
    const overlay = document.getElementById('selectPlaylistOverlay');
    if (!overlay) return;
    overlay.classList.remove('song-popup-visible');
    setTimeout(() => overlay.remove(), 300);
  }

  function showCreatePlaylistDialog(opts = {}) {
    const existing = document.getElementById('createPlaylistDialog');
    if (existing) existing.remove();

    const dialog = document.createElement('div');
    dialog.id = 'createPlaylistDialog';
    dialog.className = 'create-playlist-backdrop';
    dialog.innerHTML = `
      <div class="create-playlist-box">
        <div class="create-playlist-title">Create New Playlist</div>
        <input class="create-playlist-input" id="newPlaylistName" type="text" placeholder="Playlist name" maxlength="64">
        <div class="create-playlist-actions">
          <button class="create-playlist-btn cancel" id="createPlaylistCancel">Cancel</button>
          <button class="create-playlist-btn save" id="createPlaylistSave">Save</button>
        </div>
      </div>`;

    document.getElementById('app-shell').appendChild(dialog);
    document.getElementById('newPlaylistName').focus();

    document.getElementById('createPlaylistCancel').addEventListener('click', () => dialog.remove());

    document.getElementById('createPlaylistSave').addEventListener('click', async () => {
      const nameInput = document.getElementById('newPlaylistName');
      const name = nameInput ? nameInput.value.trim() : '';
      if (!name) return;
      const btn = document.getElementById('createPlaylistSave');
      btn.disabled = true;
      btn.textContent = 'Saving…';
      try {
        await api('/api/users/playlists', {
          method: 'POST',
          body: JSON.stringify({ playlistName: name }),
        });
        await refreshPlaylistsState();
        dialog.remove();
        if (opts.onCreated) await opts.onCreated(name);
      } catch (err) {
        btn.disabled = false;
        btn.textContent = 'Save';
        nameInput.placeholder = 'Name already taken or invalid';
      }
    });

    dialog.addEventListener('click', (e) => { if (e.target === dialog) dialog.remove(); });
  }

  // ── WebSocket ───────────────────────────────────────────────────────────
  let stompClient = null;

  function connectWebSocket() {
    if (stompClient && stompClient.connected) return;
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    stompClient.debug = () => {};
    const connectHeaders = state.token ? { token: state.token } : {};
    stompClient.connect(connectHeaders, () => {

      stompClient.subscribe('/topic/now-playing', (frame) => {
        const widget = document.getElementById('nowPlayingWidget');
        if (!widget) return;
        const msg = JSON.parse(frame.body);
        setNowPlayingWidget(widget, msg.song);
      });

      // User-specific updates — only fire for the logged-in user
      if (state.token) {
        stompClient.subscribe('/user/queue/credits', (frame) => {
          const msg = JSON.parse(frame.body);
          state.numCredits = msg.numCredits ?? 0;
          const widget = document.getElementById('creditsValue');
          if (widget) widget.textContent = `Credits: ${state.numCredits}`;
        });

        stompClient.subscribe('/user/queue/recent-plays', (frame) => {
          const song = JSON.parse(frame.body);
          state.recentPlays.unshift(song);
          if (state.recentPlays.length > 10) state.recentPlays.length = 10;
          const body = document.getElementById('recentPlaysBody');
          if (body) {
            body.innerHTML = renderSwipeableSongThumbs(state.recentPlays, 'rp');
            wireSwipeableClicks('recentPlaysBody', state.recentPlays, s => showSongPopup(s));
          }
        });
      }

    }, () => setTimeout(connectWebSocket, 3000));
  }

  // ── Init ────────────────────────────────────────────────────────────────
  renderMain('music');
  connectWebSocket();
})();
