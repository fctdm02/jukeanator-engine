(() => {
  // ── State ───────────────────────────────────────────────────────────────
  const state = {
    token: localStorage.getItem('jwt'),
    role: localStorage.getItem('role'),
    emailAddress: localStorage.getItem('emailAddress'),
    locationId: null,      // this instance's own location; fetched once at boot (see init below)
    currentLocation: null, // {locationId, name, logoName} shown in the header; fetched at boot
    locations: [],         // full picker list from GET /api/locations; empty on standalone/slave
                            // (master-only endpoint) — see init below
    pendingScreen: null,   // { screen, params } to navigate to after login
    navStack: [],          // { screen, params } entries for back navigation
    currentMainTab: 'music',
    searchHistory: null,   // cached from GET /api/users/home; null = not yet loaded
    recentPlays: [],       // cached from GET /api/users/home; updated live via STOMP
    hotHereArtists: [],    // cached from home page API; refreshed on loadHomePage
    hotHereAlbums: [],     // cached from home page API; refreshed on loadHomePage
    hotHereSongs: [],      // cached from home page API; refreshed on loadHomePage
    numCredits: 0,         // cached credit count; refreshed on loadCredits()
    myPlaylists: [],       // [{name, songCount, firstSongAlbumId}] from GET /api/users/playlists
    favoriteSongIds: new Set(), // Set of 'albumId_songId' strings
    queueHasSongs: false,   // drives "View Queue" button visibility; kept live via /topic/queue
    pricingConfig: null,    // {priorityCostMultiplier, webCostMultiplier, creditsPerDollar, ...}
                            // from GET /api/users/pricing-config; refreshed at boot and on
                            // selectLocation() — see loadPricingConfig()
    geoFence: null,         // {enforced, radiusMeters, allowSimulatedPosition, latitude, ...}
                            // from GET /api/locations/{id}/geo-fence; refreshed at boot and on
                            // selectLocation() — see loadGeoFenceStatus()
  };

  // Fallback used only until the real config loads (matches JukeANatorUserInterfaceProperties'
  // own field defaults) so nothing here can divide/multiply against an unset value.
  const DEFAULT_PRICING_CONFIG = {
    priorityCostMultiplier: 2, webCostMultiplier: 2, creditsPerDollar: 3,
    displayCurrencyForCost: false,
  };

  async function loadPricingConfig() {
    try {
      state.pricingConfig = await api(`/api/users/pricing-config?locationId=${state.locationId}`);
    } catch {
      state.pricingConfig = null;
    }
  }

  function pricingConfig() {
    return state.pricingConfig || DEFAULT_PRICING_CONFIG;
  }

  // Every Web/Mobile UI cost is the equivalent JFC/Swing cost x webCostMultiplier -- mirrors
  // CreditCostCalculator server-side (see its javadoc for why the Swing formulas are duplicated
  // here rather than shared: the JFC/Swing UI's own cost math lives in Swing-only Java classes
  // this JS can't call). At webCostMultiplier=1, Web/Mobile costs exactly match JFC/Swing.

  /**
   * isPriorityPlay mirrors which action the user picked -- normal play is JFC/Swing's fixed
   * 1-credit cost; priority play is priority * priorityCostMultiplier. This can't be inferred
   * from priority alone: highestPriority is 1 whenever the queue's current top entry is a
   * priority-0 background song, so a genuine priority play can carry the same priority (1) a
   * normal play always does.
   */
  function queueAddCost(priority, isPriorityPlay) {
    const cfg = pricingConfig();
    const swingCost = isPriorityPlay ? priority * cfg.priorityCostMultiplier : 1;
    return swingCost * cfg.webCostMultiplier;
  }

  /** Reordering/removing an already-queued song — mirrors QueuePanel's fixed priority * 3 (min 1). */
  function queueActionCost(priority) {
    const cfg = pricingConfig();
    const swingCost = Math.max(1, (priority != null ? priority : 1) * 3);
    return swingCost * cfg.webCostMultiplier;
  }

  /**
   * Formats a credit amount for display -- "Ncr" (or "N Credits", passing unit: 'Credits', to
   * match a given call site's own pre-existing wording) -- or the actual dollar cost (e.g.
   * "$0.67", rounded to the nearest cent) when displayCurrencyForCost is enabled. Mirrors
   * CreditManager.formatCredits, except the Web/Mobile UI has no per-session "money inserted"
   * concept (accounts carry a persistent balance topped up via credit packages), so this always
   * converts at the location's base creditsPerDollar rate -- the same rate getProfile() already
   * uses server-side for balanceUsd.
   */
  function formatCredits(credits, unit = 'cr') {
    const cfg = pricingConfig();
    if (!cfg.displayCurrencyForCost) {
      return unit === 'cr' ? `${credits}cr` : `${credits} Credits`;
    }
    return `$${(credits / cfg.creditsPerDollar).toFixed(2)}`;
  }

  /** Formats a credit shortfall -- "ADD N CREDIT(S)" or "ADD $X.XX". */
  function formatShortfall(neededCredits) {
    const cfg = pricingConfig();
    if (!cfg.displayCurrencyForCost) {
      return `ADD ${neededCredits} CREDIT${neededCredits === 1 ? '' : 'S'}`;
    }
    return `ADD $${(neededCredits / cfg.creditsPerDollar).toFixed(2)}`;
  }

  /** The header widget's balance text -- "Credits: N" or "Balance: $X.XX". */
  function creditsWidgetText(numCredits) {
    const cfg = pricingConfig();
    return cfg.displayCurrencyForCost
      ? `Balance: $${(numCredits / cfg.creditsPerDollar).toFixed(2)}`
      : `Credits: ${numCredits}`;
  }

  const contentPanel = document.getElementById('contentPanel');

  // Some screens (e.g. subScreenShell-based ones) have no internally-scrolling container of
  // their own -- #contentPanel itself is the real scroll region for them, matching how the rest
  // of the app already works. A screen that wants to do infinite-scroll against #contentPanel
  // registers a handler here instead of attaching its own 'scroll' listener directly, so only
  // one is ever active. Cleared explicitly at the top of every navigation entry point (below) --
  // NOT from the MutationObserver's callback, since MutationObserver callbacks fire as a
  // microtask (batched until the current synchronous stack empties). A render function that
  // registers its handler before its own first `await` would have that registration silently
  // wiped out the moment that `await` yields, by a *stale* reset queued from this same render
  // pass's earlier `contentPanel.innerHTML = ...` mutation -- resetting synchronously up front
  // instead avoids that race entirely, regardless of when a screen registers its handler.
  let activeContentPanelScrollHandler = null;
  function setContentPanelScrollHandler(handler) {
    activeContentPanelScrollHandler = handler;
  }
  contentPanel.addEventListener('scroll', () => activeContentPanelScrollHandler?.());

  // Whenever a screen's top-level markup is replaced (navigation, back, tab
  // switch), snap the scroll position back to the top so the new header is
  // never left hidden above the fold from the previous screen's scroll spot.
  new MutationObserver(() => { contentPanel.scrollTop = 0; })
    .observe(contentPanel, { childList: true });

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
    reconnectWebSocket(); // so /user/queue/... now reaches this user
  }

  function clearAuth() {
    const wasSignedIn = state.token != null;
    state.token = null;
    state.role = null;
    state.emailAddress = null;
    localStorage.removeItem('jwt');
    localStorage.removeItem('role');
    localStorage.removeItem('emailAddress');
    if (wasSignedIn) reconnectWebSocket(); // stop receiving the previous user's updates
  }

  // ── API helper ──────────────────────────────────────────────────────────
  // song-library/song-player/song-queue endpoints are scoped by locationId
  // server-side (/api/locations/{locationId}/...); rewrite bare calls to those
  // three so every caller can keep writing the short, unscoped path.
  // Throws (rather than requesting /api/locations/null/...) if the location
  // has not been resolved yet; api() callers already handle rejected calls.
  function locationScopedPath(path) {
    const m = /^\/api\/(song-library|song-player|song-queue)(\/.*|$)/.exec(path);
    if (!m) return path;
    if (state.locationId == null) {
      throw new Error(`Cannot call ${path}: location has not been resolved yet`);
    }
    return `/api/locations/${state.locationId}/${m[1]}${m[2]}`;
  }

  // Refusals whose message is written for the patron and shown as-is: a geo-fence refusal, an
  // unaffordable queue operation (the balance changed since this page last saw it, e.g. spent on
  // another device), a queue attempt on a jukebox's own web page, a failed Add Funds purchase, a
  // song the queue's rules refuse right now, and the account forms' routine mistakes.
  const PATRON_FACING_ERRORS = new Set([
    'GeoFenceViolationException', 'InsufficientCreditsException', 'QueueAccessDeniedException',
    'PaymentException', 'SongNotEligibleException', 'EmailAlreadyRegisteredException',
    'IncorrectPasswordException',
  ]);

  /** `?locationId=…` for endpoints that answer for a location (e.g. Hot Here), or '' if unknown. */
  function locationQuery() {
    return state.locationId != null ? `?locationId=${encodeURIComponent(state.locationId)}` : '';
  }

  async function api(path, options = {}) {
    const res = await fetch(locationScopedPath(path), {
      ...options,
      headers: { 'Content-Type': 'application/json', ...authHeaders(), ...(options.headers || {}) },
    });
    if (res.status === 401 && state.token) {
      // Stale/invalid token (e.g. left over from before the user store was reset) -
      // treat this the same as "not logged in" instead of surfacing a raw failure.
      clearAuth();
      renderLogin();
    }
    if (!res.ok) {
      const body = await res.json().catch(() => null);
      if (body && PATRON_FACING_ERRORS.has(body.error)) {
        if (body.error === 'InsufficientCreditsException') {
          loadCredits(document.getElementById('creditsValue'));
        }
        throw geoFenceError(body.message);
      }
      // Anything else keeps its status and the server's message, for screens that can show it
      // (e.g. a 400 from the account forms' validation, whose messages are written for the patron).
      const err = new Error(`${options.method || 'GET'} ${path} failed: ${res.status}`);
      err.status = res.status;
      err.serverMessage = body && body.message;
      throw err;
    }
    const text = await res.text();
    return text ? JSON.parse(text) : null;
  }

  // ── Geo-fencing ─────────────────────────────────────────────────────────
  // At a geo-fenced location (master only), queue operations must carry the device's position in
  // X-Geo-* headers, which the server checks against the location's coordinates. When
  // allowSimulatedPosition is on (QA only), a hand-entered position from the account menu's
  // "Simulate My Location" can stand in for the device, e.g. on a phone using a plain-http LAN
  // address, where browsers block the Geolocation API.

  /**
   * An Error whose message is meant for the patron, shown without any "Could not..." prefix --
   * geo-fence refusals, and the server's other patron-facing queue refusals (see api()).
   */
  function geoFenceError(message) {
    const err = new Error(message);
    err.geoFence = true;
    return err;
  }

  /** The message to show for a failed queue operation. */
  function queueErrorMessage(err, prefix) {
    return err && err.geoFence ? err.message : prefix + ((err && err.message) || err);
  }

  async function loadGeoFenceStatus() {
    state.geoFence = null;
    if (state.locationId == null) return;
    try {
      state.geoFence = await api(`/api/locations/${state.locationId}/geo-fence`);
    } catch {
      state.geoFence = null;
    }
  }

  function simulatedPositionKey() {
    return `simulatedPosition_${state.locationId}`;
  }

  function loadSimulatedPosition() {
    try {
      const saved = JSON.parse(localStorage.getItem(simulatedPositionKey()) || 'null');
      return saved && Number.isFinite(saved.latitude) && Number.isFinite(saved.longitude)
        && Number.isFinite(saved.accuracy) ? saved : null;
    } catch {
      return null;
    }
  }

  function saveSimulatedPosition(position) {
    try {
      if (position) localStorage.setItem(simulatedPositionKey(), JSON.stringify(position));
      else localStorage.removeItem(simulatedPositionKey());
    } catch {
      // Storage unavailable (e.g. private mode); the simulated position just isn't kept.
    }
  }

  /** Resolves to {latitude, longitude, accuracy, timestamp, simulated} or rejects with a geoFenceError. */
  function getGeoPosition() {
    const simulated = state.geoFence?.allowSimulatedPosition ? loadSimulatedPosition() : null;
    if (simulated) {
      return Promise.resolve({ ...simulated, timestamp: Date.now(), simulated: true });
    }
    if (!window.isSecureContext || !navigator.geolocation) {
      return Promise.reject(geoFenceError(
        'This location only accepts songs from patrons who are there, but your device location '
        + 'cannot be read over this connection. Please use the secure (https) site.'));
    }
    return new Promise((resolve, reject) => {
      navigator.geolocation.getCurrentPosition(
        (pos) => resolve({
          latitude: pos.coords.latitude,
          longitude: pos.coords.longitude,
          accuracy: pos.coords.accuracy,
          timestamp: pos.timestamp || Date.now(),
          simulated: false,
        }),
        (err) => reject(geoFenceError(err.code === err.PERMISSION_DENIED
          ? 'This location only accepts songs from patrons who are there. Please allow location '
            + 'access for this site in your browser settings, then try again.'
          : 'Your device location could not be determined. Please try again.')),
        { enableHighAccuracy: true, timeout: 10000, maximumAge: 30000 });
    });
  }

  /** api() for queue operations: adds the X-Geo-* headers when the location is geo-fenced. */
  async function geoApi(path, options = {}) {
    if (!state.geoFence?.enforced) return api(path, options);
    const pos = await getGeoPosition();
    const geoHeaders = {
      'X-Geo-Latitude': String(pos.latitude),
      'X-Geo-Longitude': String(pos.longitude),
      'X-Geo-Accuracy': String(pos.accuracy),
      'X-Geo-Timestamp': String(Math.round(pos.timestamp)),
    };
    if (pos.simulated) geoHeaders['X-Geo-Simulated'] = 'true';
    return api(path, { ...options, headers: { ...(options.headers || {}), ...geoHeaders } });
  }

  /** QA only: lets a tester enter the position to send in place of the device's own. */
  function showSimulateLocationDialog() {
    const fence = state.geoFence || {};
    const current = loadSimulatedPosition();
    const close = () => dialog.remove();
    const dialog = openAppDialog(`
      ${appDialogHeadHtml('Simulate My Location',
        'QA only. This position is sent with queue operations instead of the device location.')}
      <input class="app-dialog-input" id="simLatitude" type="number" step="any" placeholder="Latitude">
      <input class="app-dialog-input" id="simLongitude" type="number" step="any" placeholder="Longitude">
      <input class="app-dialog-input" id="simAccuracy" type="number" step="any" min="0" placeholder="Accuracy (m)">
      <div class="app-dialog-actions">
        <button class="app-dialog-btn cancel" id="simUseLocation">Use Location's Coordinates</button>
        <button class="app-dialog-btn cancel" id="simOffset">Offset 250 m North</button>
      </div>
      <div class="app-dialog-error"></div>
      <div class="app-dialog-actions">
        <button class="app-dialog-btn cancel" id="simClear">Clear</button>
        <button class="app-dialog-btn cancel" id="simCancel">Cancel</button>
        <button class="app-dialog-btn primary" id="simSave">Save</button>
      </div>`, close);

    const lat = dialog.querySelector('#simLatitude');
    const lon = dialog.querySelector('#simLongitude');
    const acc = dialog.querySelector('#simAccuracy');
    const error = dialog.querySelector('.app-dialog-error');
    lat.value = current ? current.latitude : '';
    lon.value = current ? current.longitude : '';
    acc.value = current ? current.accuracy : 10;

    dialog.querySelector('#simUseLocation').addEventListener('click', () => {
      if (fence.latitude == null || fence.longitude == null) {
        error.textContent = 'This location has no coordinates.';
        return;
      }
      lat.value = fence.latitude;
      lon.value = fence.longitude;
      acc.value = 10;
    });
    dialog.querySelector('#simOffset').addEventListener('click', () => {
      const base = parseFloat(lat.value);
      if (!Number.isFinite(base)) {
        error.textContent = 'Enter a latitude first.';
        return;
      }
      // One degree of latitude is about 111,320 m everywhere.
      lat.value = (base + 250 / 111320).toFixed(7);
      acc.value = 10;
    });
    dialog.querySelector('#simClear').addEventListener('click', () => {
      saveSimulatedPosition(null);
      close();
    });
    dialog.querySelector('#simCancel').addEventListener('click', close);
    dialog.querySelector('#simSave').addEventListener('click', () => {
      const position = {
        latitude: parseFloat(lat.value),
        longitude: parseFloat(lon.value),
        accuracy: parseFloat(acc.value),
      };
      if (!Number.isFinite(position.latitude) || !Number.isFinite(position.longitude)
          || !Number.isFinite(position.accuracy) || position.accuracy < 0) {
        error.textContent = 'Enter a latitude, longitude and accuracy.';
        return;
      }
      saveSimulatedPosition(position);
      close();
    });
    lat.focus();
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
  // footerHtml (optional) is pinned just above the bottom tabs, e.g. a Cancel/Save bar.
  function subScreenShell(title, bodyHtml, footerHtml = '') {
    return `
      <div class="sub-screen">
        <header class="sub-header">
          <button class="back-btn" id="backBtn">&#8592;</button>
          <h1 class="sub-title">${title}</h1>
        </header>
        <div class="sub-content">${bodyHtml}</div>
        ${footerHtml ? `<div class="sub-footer">${footerHtml}` : ''}
        <nav class="bottom-tabs">
          <button class="bottom-tab ${state.currentMainTab === 'music' ? 'active' : ''}" id="tabMusic">
            <span class="tab-icon">&#9835;</span><span>Music</span>
          </button>
          <button class="bottom-tab ${state.currentMainTab === 'addfunds' ? 'active' : ''}" id="tabAddFunds">
            <span class="tab-icon">&#128176;</span><span>Add Funds</span>
          </button>
        </nav>
        ${footerHtml ? '</div>' : ''}
      </div>`;
  }

  function renderSubScreen(screen, params = {}) {
    activeContentPanelScrollHandler = null;
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
      case 'albums-hot-here-all':    renderAlbumsHotHereAll();        break;
      case 'songs-hot-here-all':     renderSongsHotHereAll();         break;
      case 'my-playlists-all':       renderMyPlaylistsAll();          break;
      case 'playlist-detail':        renderPlaylistDetail(params);    break;
      case 'playlist-edit-order':    renderPlaylistEditOrder(params); break;
      case 'playlist-multi-select':  renderPlaylistMultiSelect(params); break;
      case 'search-entry':           renderSearchEntry();             break;
      case 'search-results':    renderSearchResults(params.query, params.result); break;
      case 'artist-detail':     renderArtistDetail(params);      break;
      case 'album-detail':      renderAlbumDetail(params);       break;
      case 'song-queue':        renderSongQueue();                break;
      default:                  renderMain(state.currentMainTab);
    }
  }

  function wireBackBtn() {
    const btn = document.getElementById('backBtn');
    if (btn) btn.addEventListener('click', goBack);

    document.getElementById('tabMusic')?.addEventListener('click', () => renderMain('music'));
    document.getElementById('tabAddFunds')?.addEventListener('click', () => {
      if (!state.token) {
        state.pendingScreen = { screen: 'addfunds' };
        renderLogin();
      } else {
        renderMain('addfunds');
      }
    });
  }

  // ── Main frame ──────────────────────────────────────────────────────────
  async function renderMain(tab = 'music') {
    activeContentPanelScrollHandler = null;
    state.currentMainTab = tab;
    state.navStack = [];

    contentPanel.innerHTML = `
      <div class="app-frame">
        <header class="top-bar">
          <div class="account-panel">
            <div class="account-left">
              <button class="location-btn${state.locations.length > 1 ? ' location-btn--pickable' : ''}" id="locationBtn">
                ${locationButtonInnerHtml()}
              </button>
              <span class="credits-value" id="creditsValue">${creditsWidgetText(0)}</span>
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

    document.getElementById('locationBtn').addEventListener('click', () => {
      if (state.locations.length > 1) showLocationPickerSheet();
    });

    document.getElementById('accountBtn').addEventListener('click', () => {
      if (state.token) {
        navigateSub('my-account');
      } else {
        state.pendingScreen = { screen: 'my-account' };
        renderLogin();
      }
    });

    document.getElementById('searchInput').addEventListener('focus', () => {
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
          api(`/api/users/home${locationQuery()}`),
          api('/api/users/playlists').catch(() => []),
          api('/api/users/playlists/favorites/songs').catch(() => []),
        ]);
        state.searchHistory    = homePage.searchHistory    || [];
        state.recentPlays      = homePage.myRecentPlays    || [];
        state.hotHereArtists   = homePage.artistsHotHere   || [];
        state.hotHereAlbums    = homePage.albumsHotHere    || [];
        state.hotHereSongs     = homePage.songsHotHere     || [];
        state.myPlaylists      = playlists || [];
        state.favoriteSongIds  = new Set((favIds || []).map(si => `${si.albumId}_${si.songId}`));
      } else {
        const publicPage = await api(`/api/users/home-public${locationQuery()}`);
        state.hotHereArtists   = publicPage.artistsHotHere || [];
        state.hotHereAlbums    = publicPage.albumsHotHere  || [];
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
          <h2 class="home-section-title">Albums Hot Here</h2>
          <button class="home-section-view-all" id="albumsHotHereViewAll">View All</button>
        </div>
        <div class="home-section-body" id="albumsHotHereBody">${renderSwipeableAlbumThumbs(state.hotHereAlbums)}</div>
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
    document.getElementById('albumsHotHereViewAll')
      ?.addEventListener('click', () => navigateSub('albums-hot-here-all'));
    document.getElementById('songsHotHereViewAll')
      ?.addEventListener('click', () => navigateSub('songs-hot-here-all'));
    wireSwipeableClicks('artistsHotHereBody', state.hotHereArtists,
      a => navigateSub('artist-detail', { artistId: a.artistId }));
    wireSwipeableClicks('albumsHotHereBody', state.hotHereAlbums,
      a => navigateSub('album-detail', { albumId: a.albumId }));
    wireSwipeableClicks('songsHotHereBody', state.hotHereSongs, s => showSongPopup(s));
  }

  function songThumbHtml(s) {
    const art = s.albumId != null
      ? `<img src="/api/locations/${state.locationId}/song-library/albums/${s.albumId}/coverArt" alt=""
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
      ? `<img src="/api/locations/${state.locationId}/song-library/albums/${a.albums[0].albumId}/coverArt" alt=""
              onerror="this.outerHTML='<div class=\\'rp-thumb-placeholder\\'>&#127911;</div>'">`
      : `<div class="rp-thumb-placeholder">&#127911;</div>`;
    return `<div class="rp-thumb-card">
      <div class="rp-thumb-img">${art}</div>
      <div class="rp-thumb-song">${escHtml(a.artistName || '')}</div>
      <div class="rp-thumb-artist">${a.songCount != null ? escHtml(String(a.songCount)) + ' songs' : ''}</div>
    </div>`;
  }

  function albumThumbHtml(a) {
    const art = a.albumId != null
      ? `<img src="/api/locations/${state.locationId}/song-library/albums/${a.albumId}/coverArt" alt=""
              onerror="this.outerHTML='<div class=\\'rp-thumb-placeholder\\'>&#128191;</div>'">`
      : `<div class="rp-thumb-placeholder">&#128191;</div>`;
    return `<div class="rp-thumb-card">
      <div class="rp-thumb-img">${art}</div>
      <div class="rp-thumb-song">${escHtml(a.albumName || '')}</div>
      <div class="rp-thumb-artist">${escHtml(a.artistName || '')}</div>
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

  function renderSwipeableAlbumThumbs(albums) {
    if (!albums || albums.length === 0) return '<div class="stub-placeholder">Nothing to show yet</div>';
    return `<div class="rp-thumb-row">${albums.map(albumThumbHtml).join('')}</div>`;
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

  /**
   * Shared infinite-scroll "View All" screen for one Hot Here category. Unlike the home screen's
   * teaser row (state.hotHereArtists/hotHereAlbums/hotHereSongs, capped at 10 items), this fetches directly from
   * the paginated /popular endpoint so the user can keep scrolling arbitrarily deep -- fetching the
   * next server page and appending whenever the scroll position nears the bottom, and stopping once
   * a fetch returns no further items.
   *
   * @param title screen title
   * @param pageParam which of artistPage/albumPage/songPage this category advances
   * @param resultField the SearchResultDto field ('artists', 'albums', or 'songs') to read from the response
   * @param rowFn row-HTML renderer for one item
   * @param onRowClick called with the clicked item
   */
  async function renderHotHereAll(title, pageParam, resultField, rowFn, onRowClick) {
    contentPanel.innerHTML = subScreenShell(title, '<div class="stub-placeholder">Loading…</div>');
    wireBackBtn();

    let items = [];
    let nextPage = 0;
    let exhausted = false;
    let loading = false;

    async function loadPage() {
      if (loading || exhausted) return;
      loading = true;
      try {
        const res = await api(`/api/song-library/popular?${pageParam}=${nextPage}`);
        const newItems = res[resultField] || [];
        if (newItems.length === 0) {
          exhausted = true;
          if (items.length === 0) {
            container.innerHTML = '<div class="stub-placeholder">Nothing to show yet</div>';
          }
        } else {
          items = items.concat(newItems);
          nextPage += 1;
          container.insertAdjacentHTML('beforeend', newItems.map(rowFn).join(''));
        }
      } catch {
        // Transient failure -- leave state as-is, allow the next scroll to retry.
      } finally {
        loading = false;
      }
    }

    const subContent = contentPanel.querySelector('.sub-content');
    subContent.innerHTML = '<div class="recent-plays-all"></div>';
    const container = subContent.querySelector('.recent-plays-all');

    // Delegated click handling so rows appended by later pages don't need their own listeners.
    container.addEventListener('click', (e) => {
      const row = e.target.closest('.result-row');
      if (!row) return;
      const rows = Array.from(container.querySelectorAll('.result-row'));
      const idx = rows.indexOf(row);
      if (idx >= 0 && idx < items.length) onRowClick(items[idx]);
    });

    // .sub-content has no bounded height of its own here -- it just grows to fit all its
    // content (see subScreenShell/.sub-content in style.css), so #contentPanel is the element
    // that actually scrolls. Register against that instead of subContent, which would never
    // fire a 'scroll' event at all.
    setContentPanelScrollHandler(() => {
      if (contentPanel.scrollTop + contentPanel.clientHeight >= contentPanel.scrollHeight - 150) {
        loadPage();
      }
    });

    await loadPage();
  }

  function renderArtistsHotHereAll() {
    renderHotHereAll('Artists Hot Here', 'artistPage', 'artists', artistListRowHtml,
      a => navigateSub('artist-detail', { artistId: a.artistId }));
  }

  function renderAlbumsHotHereAll() {
    renderHotHereAll('Albums Hot Here', 'albumPage', 'albums', albumListRowHtml,
      a => navigateSub('album-detail', { albumId: a.albumId }));
  }

  function renderSongsHotHereAll() {
    renderHotHereAll('Songs Hot Here', 'songPage', 'songs', songListRowHtml,
      s => showSongPopup(s));
  }

  function songListRowHtml(s) {
    const art = s.albumId != null
      ? `<img class="result-thumb" src="/api/locations/${state.locationId}/song-library/albums/${s.albumId}/coverArt" alt=""
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
      ? `<img class="result-thumb" src="/api/locations/${state.locationId}/song-library/albums/${a.albums[0].albumId}/coverArt" alt=""
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

  function albumListRowHtml(a) {
    const art = a.albumId != null
      ? `<img class="result-thumb" src="/api/locations/${state.locationId}/song-library/albums/${a.albumId}/coverArt" alt=""
              onerror="this.outerHTML='<div class=\\'result-thumb-placeholder\\'>&#128191;</div>'">`
      : `<div class="result-thumb-placeholder">&#128191;</div>`;
    return `<div class="result-row">
      ${art}
      <div class="result-info">
        <div class="result-title">${escHtml(a.albumName || '')}</div>
        <div class="result-sub">${escHtml(a.artistName || '')}</div>
      </div>
    </div>`;
  }

  // ── Header data loaders ─────────────────────────────────────────────────
  async function loadCredits(widget) {
    if (!state.token) {
      state.numCredits = 0;
      if (widget) widget.textContent = creditsWidgetText(0);
      return;
    }
    try {
      const profile = await api('/api/users/me');
      state.numCredits = profile.numCredits ?? 0;
      if (widget) widget.textContent = creditsWidgetText(state.numCredits);
    } catch {
      state.numCredits = 0;
      if (widget) widget.textContent = creditsWidgetText(0);
    }
  }

  async function loadNowPlaying(widget) {
    if (!widget) return;
    try {
      const song = await api('/api/song-player/nowPlayingSong');
      // Initial "View Queue" visibility, before any /topic/queue push arrives:
      // hidden unless something is actually playing right now.
      state.queueHasSongs = !!song;
      setNowPlayingWidget(widget, song);
    } catch {
      state.queueHasSongs = false;
      setNowPlayingWidget(widget, null);
    }
  }

  // Toggles the "View Queue" button per state.queueHasSongs without touching the
  // rest of the now-playing widget markup (used by the live /topic/queue handler).
  function updateViewQueueVisibility() {
    const btn = document.getElementById('viewQueueBtn');
    if (btn) btn.hidden = !state.queueHasSongs;
  }

  function setNowPlayingWidget(widget, song) {
    const viewQueueBtnHtml =
      `<button class="now-playing-view-queue" id="viewQueueBtn" ${state.queueHasSongs ? '' : 'hidden'}>View Queue</button>`;
    if (!song) {
      widget.innerHTML = `
        <div class="now-playing-idle">
          <div class="now-playing-title-row">
            <div class="idle-title">No music playing</div>
            ${viewQueueBtnHtml}
          </div>
          <div class="idle-sub">Let's play some music!</div>
        </div>`;
    } else {
      const crawlText = `${escHtml(song.artistName || '')}${song.albumName ? ' &middot; ' + escHtml(song.albumName) : ''}`;
      widget.innerHTML = `
        <img class="now-playing-album-link" src="/api/locations/${state.locationId}/song-library/albums/${song.albumId}/coverArt" alt="${escHtml(song.albumName || '')}"
             onerror="this.remove()">
        <div class="now-playing-text">
          <div class="now-playing-title-row">
            <div class="song-name now-playing-album-link">${escHtml(song.songName || '')}</div>
            ${viewQueueBtnHtml}
          </div>
          <div class="now-playing-crawl-wrap now-playing-album-link">
            <div class="now-playing-crawl">${crawlText}&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;${crawlText}</div>
          </div>
        </div>`;
      wireNowPlayingAlbumLinks(widget, song);
    }
    document.getElementById('viewQueueBtn')?.addEventListener('click', () => navigateSub('song-queue'));
  }

  // Tapping the currently playing song (its art or text) opens that song's album.
  function wireNowPlayingAlbumLinks(container, song) {
    if (song?.albumId == null) return;
    container.querySelectorAll('.now-playing-album-link').forEach(el => {
      el.addEventListener('click', () => navigateSub('album-detail', { albumId: song.albumId }));
    });
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
            <span class="package-bonus-tag">+${pkg.bonusCredits} BONUS CREDIT${pkg.bonusCredits === 1 ? '' : 'S'}</span>
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

    document.getElementById('addFundsBtn').addEventListener('click', () => {
      const pkg = packages.find(p => p.id === selectedId);
      if (pkg) showPaymentMethodSheet(pkg, (response) => renderTransactionSuccess(response));
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

    const balance = pricingConfig().displayCurrencyForCost && profile.balanceUsd != null
      ? `$${Number(profile.balanceUsd).toFixed(2)} (USD)`
      : `${profile.numCredits} Credits`;

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
        ${state.geoFence?.allowSimulatedPosition ? `
        <button class="menu-row" id="simulateLocationBtn">
          <span class="menu-row-icon">&#128205;</span>
          <span class="menu-row-label">Simulate My Location (QA)</span>
          <span class="menu-row-arrow">&#8250;</span>
        </button>` : ''}
      </div>

      <button class="menu-list logout-row" id="logoutBtn">Log Out</button>`;

    document.getElementById('manageAccountBtn').addEventListener('click', () => navigateSub('manage-account', { profile }));
    document.getElementById('transactionHistoryBtn').addEventListener('click', () => navigateSub('transaction-history'));
    document.getElementById('helpBtn').addEventListener('click', () => navigateSub('help'));
    document.getElementById('settingsBtn').addEventListener('click', () => navigateSub('settings'));
    document.getElementById('termsBtn').addEventListener('click', () => navigateSub('terms'));
    document.getElementById('privacyBtn').addEventListener('click', () => navigateSub('privacy'));
    document.getElementById('simulateLocationBtn')?.addEventListener('click', showSimulateLocationDialog);
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
      } catch (err) {
        btn.textContent = 'Save';
        btn.disabled = false;
        showAppAlert({ title: 'Edit Profile', message: err.status === 400 && err.serverMessage
          ? err.serverMessage : 'Could not save your profile. Please try again.' });
      }
    });
  }

  // ── Sub-screen: Change Password ─────────────────────────────────────────
  function renderChangePassword() {
    contentPanel.innerHTML = subScreenShell('Password', `
      <div class="form-group">
        <label class="form-label" for="currentPassword">Current Password</label>
        ${peekablePassword('currentPassword', { className: 'form-input',
          placeholder: 'Enter current password', autocomplete: 'current-password' })}
      </div>
      <div class="form-group">
        <label class="form-label" for="newPassword">New Password</label>
        ${peekablePassword('newPassword', { className: 'form-input', placeholder: 'Enter new password' })}
      </div>
      <div class="form-group">
        <label class="form-label" for="confirmPassword">Confirm New Password</label>
        ${peekablePassword('confirmPassword', { className: 'form-input', placeholder: 'Confirm new password' })}
      </div>
      <div class="password-hint">${PASSWORD_POLICY_TEXT}</div>
      <div id="pwError" class="form-error"></div>
      <div class="form-spacer"></div>
      <div class="form-actions">
        <button class="form-btn cancel" id="cancelPwBtn">Cancel</button>
        <button class="form-btn save" id="savePwBtn">Save</button>
      </div>`);

    wireBackBtn();
    wirePasswordPeeks(contentPanel);
    wirePasswordValidity(document.getElementById('newPassword'),
      document.getElementById('confirmPassword'));
    document.getElementById('cancelPwBtn').addEventListener('click', goBack);
    document.getElementById('savePwBtn').addEventListener('click', async () => {
      const current = document.getElementById('currentPassword').value;
      const newPw = document.getElementById('newPassword').value;
      const confirm = document.getElementById('confirmPassword').value;
      const errEl = document.getElementById('pwError');
      errEl.textContent = '';
      const problem = passwordProblem(newPw);
      if (problem) { errEl.textContent = problem; return; }
      if (newPw !== confirm) { errEl.textContent = 'New passwords do not match.'; return; }
      const btn = document.getElementById('savePwBtn');
      btn.disabled = true; btn.textContent = 'Saving…';
      try {
        await api('/api/users/change-password', {
          method: 'POST',
          body: JSON.stringify({ currentPassword: current, newPassword: newPw }),
        });
        goBack();
      } catch (err) {
        // A wrong current password (400) and the server's password rules both explain themselves.
        errEl.textContent = err.geoFence || err.status === 400
          ? (err.serverMessage || err.message) : 'Could not change your password. Please try again.';
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
            <label>Password ${peekablePassword('loginPassword', { autocomplete: 'current-password' })}</label>
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
    wirePasswordPeeks(contentPanel);
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
            <div class="error-msg" id="registerError" ${errorMessage ? '' : 'hidden'}>${escHtml(errorMessage || '')}</div>
            <label>First name <input type="text" id="registerFirstName" autocomplete="off" required></label>
            <label>Last name <input type="text" id="registerLastName" autocomplete="off" required></label>
            <label>Email <input type="email" id="registerEmail" autocomplete="off" required></label>
            <label>Password ${peekablePassword('registerPassword')}</label>
            <label>Confirm ${peekablePassword('registerConfirmPassword')}</label>
            <div class="password-hint">${PASSWORD_POLICY_TEXT}</div>
            <button type="submit" class="auth-btn">Submit</button>
            <button type="button" id="registerCancelBtn" class="auth-btn">Cancel</button>
          </form>
        </div>
      </div>`;

    wirePasswordPeeks(contentPanel);
    wirePasswordValidity(document.getElementById('registerPassword'),
      document.getElementById('registerConfirmPassword'));
    document.getElementById('registerCancelBtn').addEventListener('click', () => renderLogin());
    document.getElementById('registerForm').addEventListener('submit', async (e) => {
      e.preventDefault();
      const errEl = document.getElementById('registerError');
      const showError = (message) => { errEl.textContent = message; errEl.hidden = false; };
      errEl.hidden = true;
      const password = document.getElementById('registerPassword').value;
      const problem = passwordProblem(password);
      if (problem) { showError(problem); return; }
      if (password !== document.getElementById('registerConfirmPassword').value) {
        showError('Passwords do not match.');
        return;
      }
      try {
        const auth = await api('/api/users/register', {
          method: 'POST',
          body: JSON.stringify({
            firstName: document.getElementById('registerFirstName').value,
            lastName: document.getElementById('registerLastName').value,
            emailAddress: document.getElementById('registerEmail').value,
            password,
          }),
        });
        setAuth(auth);
        afterLogin();
      } catch (err) {
        // The server's own validation and duplicate-email messages are written for the patron.
        showError(err.geoFence || err.status === 400 ? (err.serverMessage || err.message)
          : 'Could not create your account. Please try again.');
      }
    });
  }

  /** A new-password input with a "peek" toggle; call wirePasswordPeeks once it is in the DOM. */
  function peekablePassword(id, { className = '', placeholder = '', autocomplete = 'new-password' } = {}) {
    return `
      <span class="password-peek-wrap">
        <input type="password" id="${id}" class="${className}" placeholder="${placeholder}"
               autocomplete="${autocomplete}" required>
        <button type="button" class="password-peek-btn" data-peek-for="${id}"
                aria-label="Show password" aria-pressed="false" title="Show password">${EYE_ICON}</button>
      </span>`;
  }

  /** Toggles each peek button's input between masked and plain text. */
  function wirePasswordPeeks(root) {
    root.querySelectorAll('.password-peek-btn').forEach((btn) => {
      btn.addEventListener('click', () => {
        const input = document.getElementById(btn.dataset.peekFor);
        const reveal = input.type === 'password';
        input.type = reveal ? 'text' : 'password';
        btn.innerHTML = reveal ? EYE_OFF_ICON : EYE_ICON;
        btn.setAttribute('aria-pressed', String(reveal));
        btn.setAttribute('aria-label', reveal ? 'Hide password' : 'Show password');
        btn.title = btn.getAttribute('aria-label');
        input.focus();
      });
    });
  }

  /**
   * Live red/green borders: the new password turns green once it meets passwordProblem's rules, and
   * the confirmation once it is non-empty and matches the new password.
   */
  function wirePasswordValidity(passwordInput, confirmInput) {
    const mark = (input, ok) => {
      input.classList.toggle('pw-valid', ok);
      input.classList.toggle('pw-invalid', !ok);
    };
    const update = () => {
      mark(passwordInput, !passwordProblem(passwordInput.value));
      mark(confirmInput, confirmInput.value !== '' && confirmInput.value === passwordInput.value);
    };
    passwordInput.addEventListener('input', update);
    confirmInput.addEventListener('input', update);
    update();
  }

  const EYE_ICON =`<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor"
      stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      <path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12z"/><circle cx="12" cy="12" r="3"/></svg>`;
  const EYE_OFF_ICON = `<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor"
      stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      <path d="M17.94 17.94A10.07 10.07 0 0 1 12 19c-7 0-11-7-11-7a18.45 18.45 0 0 1 5.06-5.94"/>
      <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 7 11 7a18.5 18.5 0 0 1-2.16 3.19"/>
      <path d="M14.12 14.12a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>`;

  const PASSWORD_POLICY_TEXT =
    'Your password must be at least 8 characters long and include at least one letter and one number.';

  /**
   * Why a new password is not allowed, or null -- mirrors the server's AccountValidator: at least
   * 8 characters, with at least one letter and one number. Keep both sides in sync.
   */
  function passwordProblem(password) {
    if (!password || password.length < 8 || !/\p{L}/u.test(password) || !/\d/.test(password)) {
      return PASSWORD_POLICY_TEXT;
    }
    if (new TextEncoder().encode(password).length > 72) {
      return 'Your password must be at most 72 characters long.';
    }
    return null;
  }

  function afterLogin() {
    state.pendingScreen = null;
    renderMain('music');
  }

  // ── Search ──────────────────────────────────────────────────────────────

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

    function render() {
      contentPanel.innerHTML = `
        <div class="search-screen">
          <div class="search-top-bar">
            <button class="search-back-btn" id="searchBackBtn">&#8592;</button>
            <div class="search-input-bar">
              <span class="search-icon">&#128269;</span>
              <input type="search" class="search-input-field" id="searchEntryInput"
                     placeholder="Search for music" autocomplete="off" enterkeyhint="search">
              <button class="search-clear-btn" id="searchClearBtn" style="display:none">&#10005;</button>
            </div>
          </div>
          <div class="search-history-list" id="searchHistoryList">
            ${historyHtml(history)}
          </div>
          <nav class="bottom-tabs">
            <button class="bottom-tab ${state.currentMainTab === 'music' ? 'active' : ''}" id="tabMusic">
              <span class="tab-icon">&#9835;</span><span>Music</span>
            </button>
            <button class="bottom-tab ${state.currentMainTab === 'addfunds' ? 'active' : ''}" id="tabAddFunds">
              <span class="tab-icon">&#128176;</span><span>Add Funds</span>
            </button>
          </nav>
        </div>`;

      wireBackBtn();

      const input = document.getElementById('searchEntryInput');
      const clearBtn = document.getElementById('searchClearBtn');

      document.getElementById('searchBackBtn').addEventListener('click', () => goBack());

      input.addEventListener('input', () => {
        clearBtn.style.display = input.value ? 'flex' : 'none';
      });
      input.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && input.value.trim()) executeSearch(input.value.trim());
      });
      input.addEventListener('search', () => {
        if (input.value.trim()) executeSearch(input.value.trim());
      });

      clearBtn.addEventListener('click', () => {
        input.value = '';
        clearBtn.style.display = 'none';
        input.focus();
      });

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
          await executeSearch(history[parseInt(item.dataset.index, 10)]);
        }
      });

      // Give the search bar keyboard focus immediately — on touch devices this
      // is what triggers the browser's native on-screen keyboard.
      input.focus();
    }

    async function executeSearch(query) {
      await saveSearchQuery(query);
      try {
        const result = await api(`/api/song-library/search?searchFor=${encodeURIComponent(query)}`);
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
      return `<img class="result-thumb" src="/api/locations/${state.locationId}/song-library/albums/${albumId}/coverArt"
               alt="" onerror="this.outerHTML=\`<div class='result-thumb-placeholder'>${fallbackEmoji}</div>\`">`;
    }
    return `<div class="result-thumb-placeholder">${fallbackEmoji}</div>`;
  }

  function artistResultRow(a) {
    const thumb = a.artistId != null
      ? `<img class="result-thumb" src="/api/locations/${state.locationId}/song-library/artists/${a.artistId}/coverArt"
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
        <div class="result-title">${escHtml(a.albumName || '')}</div>
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
        <div class="result-sub">Song &middot; ${escHtml(s.artistName || '')} &middot; ${escHtml(s.albumName || '')}</div>
      </div>
      <button class="result-menu-btn" title="More options">&#8942;</button>
    </div>`;
  }

  // Category keys map to both the SearchResultDto field name and the REST query param that
  // advances that category's own server page -- artists/albums/songs each scroll (and thus
  // page) independently, mirroring the JFC/Swing UI's per-category paging buffers.
  const SEARCH_CATEGORY_PAGE_PARAM = { artists: 'artistPage', albums: 'albumPage', songs: 'songPage' };
  const SEARCH_CATEGORY_ROW_FN = { artists: artistResultRow, albums: albumResultRow, songs: songResultRow };

  function renderSearchResults(query, result) {
    const artists = result.artists || [];
    const albums  = result.albums  || [];
    const songs   = result.songs   || [];

    // Per-category infinite-scroll state -- page 0 is already in hand (the lists above), so the
    // next fetch for each category starts at page 1. A category starting out empty has nothing
    // further to fetch.
    const scrollState = {
      artists: { nextPage: 1, exhausted: artists.length === 0, loading: false },
      albums:  { nextPage: 1, exhausted: albums.length === 0,  loading: false },
      songs:   { nextPage: 1, exhausted: songs.length === 0,   loading: false },
    };

    async function loadMoreCategory(category) {
      const st = scrollState[category];
      if (st.loading || st.exhausted) return;
      st.loading = true;
      try {
        const params = new URLSearchParams({ searchFor: query });
        params.set(SEARCH_CATEGORY_PAGE_PARAM[category], st.nextPage);
        const res = await api(`/api/song-library/search?${params.toString()}`);
        const newItems = res[category] || [];
        if (newItems.length === 0) {
          st.exhausted = true;
        } else {
          st.nextPage += 1;
          const panel = document.getElementById('tab-' + category);
          if (panel) {
            panel.insertAdjacentHTML('beforeend', newItems.map(SEARCH_CATEGORY_ROW_FN[category]).join(''));
          }
        }
      } catch {
        // Transient failure -- leave exhausted/nextPage untouched so the next scroll retries.
      } finally {
        st.loading = false;
      }
    }

    function wireInfiniteScroll(category) {
      const panel = document.getElementById('tab-' + category);
      if (!panel) return;
      panel.addEventListener('scroll', () => {
        if (panel.scrollTop + panel.clientHeight >= panel.scrollHeight - 150) {
          loadMoreCategory(category);
        }
      });
    }

    const TOP_N = 3;
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
            <button class="search-clear-btn" id="searchResultsClearBtn">&#10005;</button>
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
        <nav class="bottom-tabs">
          <button class="bottom-tab ${state.currentMainTab === 'music' ? 'active' : ''}" id="tabMusic">
            <span class="tab-icon">&#9835;</span><span>Music</span>
          </button>
          <button class="bottom-tab ${state.currentMainTab === 'addfunds' ? 'active' : ''}" id="tabAddFunds">
            <span class="tab-icon">&#128176;</span><span>Add Funds</span>
          </button>
        </nav>
      </div>`;

    wireBackBtn();

    document.getElementById('searchResultsBackBtn').addEventListener('click', () => goBack());
    document.getElementById('searchResultsClearBtn').addEventListener('click', () => goBack());

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

    // Infinite scroll — Artists/Albums/Songs tabs only; "Top" stays a fixed 3-item preview.
    wireInfiniteScroll('artists');
    wireInfiniteScroll('albums');
    wireInfiniteScroll('songs');
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
      const url = params.albumId != null
        ? `/api/song-library/artistByAlbum/${params.albumId}`
        : `/api/song-library/artists/${params.artistId}`;
      artist = await api(url);
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
      ? `<img class="album-detail-cover" src="/api/locations/${state.locationId}/song-library/albums/${album.albumId}/coverArt"
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
    if (artistEl && album.albumId != null) {
      artistEl.classList.add('album-detail-artist-link');
      artistEl.addEventListener('click', () => navigateSub('artist-detail', { albumId: album.albumId }));
    }
  }

  // ── Song Queue screen ────────────────────────────────────────────────────

  const QUEUE_TOP_N = 3;

  // Set while the Song Queue screen is showing; the /topic/queue and /topic/now-playing
  // handlers call it so the screen follows song transitions and other users' queue changes.
  let activeSongQueueRefresh = null;

  async function renderSongQueue() {
    contentPanel.innerHTML = subScreenShell('Song Queue', '<div class="stub-placeholder">Loading&hellip;</div>');
    wireBackBtn();
    const subContent = contentPanel.querySelector('.sub-content');

    let nowPlaying = null;
    let topQueue = [];
    let selectedIndex = -1;
    let reloadSeq = 0;

    function songKey(entry) {
      const s = entry?.song;
      return s ? `${s.albumId}:${s.songId}` : null;
    }

    async function reload() {
      const seq = ++reloadSeq;
      const selectedKey = selectedIndex >= 0 ? songKey(topQueue[selectedIndex]) : null;
      const [np, queue] = await Promise.all([
        api('/api/song-player/nowPlayingSong').catch(() => null),
        api('/api/song-queue/queuedSongs').catch(() => []),
      ]);
      // Drop stale responses (a newer reload started) and responses for a screen that's gone.
      if (seq !== reloadSeq || !subContent.isConnected) return;
      nowPlaying = np;
      topQueue = (queue || []).slice(0, QUEUE_TOP_N);
      // Keep the same song selected as it moves, or clear the selection once it leaves the top N.
      selectedIndex = selectedKey ? topQueue.findIndex(e => songKey(e) === selectedKey) : -1;
      render();
    }

    activeSongQueueRefresh = () => {
      if (!subContent.isConnected) {
        activeSongQueueRefresh = null;
        return;
      }
      reload();
    };

    function nowPlayingHtml() {
      if (!nowPlaying) {
        return `<div class="queue-now-playing-row">
          <div class="queue-now-playing-info">
            <div class="queue-now-playing-song">No music playing</div>
          </div>
        </div>`;
      }
      const art = nowPlaying.albumId != null
        ? `<img class="queue-now-playing-thumb" src="/api/locations/${state.locationId}/song-library/albums/${nowPlaying.albumId}/coverArt"
                alt="" onerror="this.remove()">`
        : '';
      return `<div class="queue-now-playing-row now-playing-album-link">
        ${art}
        <div class="queue-now-playing-info">
          <div class="queue-now-playing-song">${escHtml(nowPlaying.songName || '')}</div>
          <div class="queue-now-playing-artist">${escHtml(nowPlaying.artistName || '')}</div>
        </div>
      </div>`;
    }

    function queueRowHtml(entry, i) {
      const s = entry.song || {};
      return `<div class="queue-song-row${i === selectedIndex ? ' selected' : ''}" data-index="${i}">
        ${coverArtHtml(s.albumId, '&#127925;')}
        <div class="result-info">
          <div class="result-title">${escHtml(s.songName || '')}</div>
          <div class="result-sub">${escHtml(s.artistName || '')}</div>
        </div>
      </div>`;
    }

    function actionButtonHtml(action, label) {
      return `<button class="queue-action-btn state-grey" data-action="${action}" disabled>
        <span class="qab-label">${label}</span>
        <span class="qab-sub"></span>
      </button>`;
    }

    function render() {
      subContent.innerHTML = `
        <div class="queue-now-playing-label">Now Playing:</div>
        ${nowPlayingHtml()}
        <div class="queue-section-label">Play Queue (Next ${QUEUE_TOP_N} Songs)</div>
        <div class="queue-song-list" id="queueSongList">
          ${topQueue.length ? topQueue.map(queueRowHtml).join('')
            : '<div class="stub-placeholder">The queue is currently empty.</div>'}
        </div>
        <div class="queue-action-buttons">
          ${actionButtonHtml('up', 'Move Song Up')}
          ${actionButtonHtml('down', 'Move Song Down')}
          ${actionButtonHtml('remove', 'Remove Song')}
        </div>
        <div class="queue-order-note">
          <span class="queue-order-note-icon">&#8505;</span>
          Order may change pending additional song selections purchased with priority play or queue operations by other users.  In addition, your song may be removed, at cost by others.  This likely will not happen if you do not troll the jukebox.
        </div>`;

      wireNowPlayingAlbumLinks(subContent, nowPlaying);

      subContent.querySelectorAll('.queue-song-row').forEach((row, i) => {
        row.addEventListener('click', () => {
          selectedIndex = i;
          subContent.querySelectorAll('.queue-song-row').forEach(r => r.classList.remove('selected'));
          row.classList.add('selected');
          updateActionButtons();
        });
      });

      subContent.querySelectorAll('.queue-action-btn').forEach(btn => {
        btn.addEventListener('click', () => handleAction(btn.dataset.action));
      });

      updateActionButtons();
    }

    function updateActionButtons() {
      const upBtn = subContent.querySelector('.queue-action-btn[data-action="up"]');
      const downBtn = subContent.querySelector('.queue-action-btn[data-action="down"]');
      const removeBtn = subContent.querySelector('.queue-action-btn[data-action="remove"]');
      if (!upBtn || !downBtn || !removeBtn) return;

      const entry = selectedIndex >= 0 ? topQueue[selectedIndex] : null;
      const cost = entry ? queueActionCost(entry.priority) : 0;
      const afford = state.numCredits >= cost;

      function apply(btn, locked) {
        const sub = btn.querySelector('.qab-sub');
        btn.classList.remove('state-grey', 'state-normal', 'state-warn');
        btn.disabled = true;
        if (!entry || locked) {
          btn.classList.add('state-grey');
          sub.textContent = '';
          return;
        }
        if (afford) {
          btn.classList.add('state-normal');
          sub.textContent = formatCredits(cost);
          btn.disabled = false;
        } else {
          btn.classList.add('state-warn');
          const needed = cost - state.numCredits;
          sub.textContent = formatShortfall(needed);
        }
      }

      apply(upBtn, selectedIndex <= 0);
      apply(downBtn, selectedIndex < 0 || selectedIndex >= topQueue.length - 1);
      apply(removeBtn, false);
    }

    async function handleAction(action) {
      if (selectedIndex < 0) return;
      const entry = topQueue[selectedIndex];
      const song = entry.song;
      const endpoint = action === 'up' ? 'moveSongUpInQueue'
        : action === 'down' ? 'moveSongDownInQueue'
        : 'removeSongDownFromQueue';
      try {
        await geoApi(`/api/song-queue/${endpoint}`, {
          method: 'POST',
          body: JSON.stringify({ albumId: song.albumId, songId: song.songId }),
        });
        await reload();
      } catch (err) {
        showAppAlert({ title: 'Song Queue', message: queueErrorMessage(err, 'Could not update the queue: ') });
      }
    }

    await reload();
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

  /**
   * Why the queue's rules refuse `song` right now, or null. Only checked for a signed-in patron
   * (the endpoint needs an account); null when it cannot be checked, leaving the server -- which
   * enforces the same rules on every add -- to decide when the patron taps Play.
   */
  async function songIneligibleReason(song) {
    if (!state.token || song.albumId == null || song.songId == null) return null;
    try {
      const results = await api('/api/song-queue/checkSongsEligibility', {
        method: 'POST',
        body: JSON.stringify({
          songIdentifiers: [{ locationId: state.locationId, albumId: song.albumId, songId: song.songId }],
          priority: 1,
        }),
      });
      return (results && results[0] && results[0].ineligibleReason) || null;
    } catch {
      return null;
    }
  }

  /** A song's ineligibility, phrased for the patron (mirrors SongQueueServiceImpl.ineligibleMessage). */
  function ineligibleSongMessage(songName, reason) {
    const trimmed = (reason || '').replace(/[.\s]+$/, '');
    const title = `"${songName || 'This song'}"`;
    return /^(has|is|was) /.test(trimmed)
      ? `${title} ${trimmed}.`
      : `${title} can't be played right now: ${trimmed}.`;
  }

  async function showSongPopup(song, opts = {}) {
    // Remove any existing popup
    const existing = document.getElementById('songPopupOverlay');
    if (existing) existing.remove();
    clearTimeout(_songPopupTimer);

    // Read-only mode (e.g. tapping a song already in the Song Queue) shows the same sheet
    // minus the two "add to queue" actions — there's nothing to add, it's already queued.
    const readOnly = !!opts.readOnly;

    const credits = state.numCredits;
    // highestPriority already IS the next available priority level (queue's top + 1, or 2 if
    // empty) -- mirrors AddSongToQueueCard, which uses it directly as both the cost basis and
    // the priority to queue at. Do not add another +1 here.
    const [priorityLevel, ineligibleReason] = readOnly ? [1, null] : await Promise.all([
      api('/api/song-queue/highestPriority').catch(() => 1).then(level => level || 1),
      songIneligibleReason(song),
    ]);
    const ineligible      = ineligibleReason != null;
    const costPlay        = queueAddCost(1, false);
    const costPriority    = queueAddCost(priorityLevel, true);
    const canPlay         = !ineligible && credits >= costPlay;
    const canPriority     = !ineligible && credits >= costPriority;

    const name      = escHtml(song.songName || song.title || '');
    const albumName = escHtml(song.albumName || '');
    const artist    = escHtml(song.artistName || '');
    const crawlText = `${albumName} · ${artist}`;

    const thumbHtml = song.albumId != null
      ? `<img class="song-popup-thumb" src="/api/locations/${state.locationId}/song-library/albums/${song.albumId}/coverArt"
              alt="" onerror="this.outerHTML='<div class=\\'song-popup-thumb song-popup-thumb-placeholder\\'>&#127925;</div>'">`
      : `<div class="song-popup-thumb song-popup-thumb-placeholder">&#127925;</div>`;

    // An ineligible song greys both play actions out (it is not a credits problem); otherwise an
    // unaffordable action is flagged with the credits warning.
    const actionClass = (can) => ineligible ? 'song-popup-action song-popup-action--disabled'
      : can ? 'song-popup-action' : 'song-popup-action song-popup-action--warn';
    const creditClass = (can) => ineligible || can ? 'spa-credits' : 'spa-credits spa-credits--warn';
    const priorityClass       = actionClass(canPriority);
    const playClass           = actionClass(canPlay);
    const priorityCreditClass = creditClass(canPriority);
    const playCreditClass     = creditClass(canPlay);
    const warnMark = (can) => ineligible || can ? '' : ' ⚠';

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
        ${readOnly ? '' : `
        ${state.geoFence?.enforced ? `
        <div class="song-popup-geo-note">&#128205; You must be at this location to queue songs.</div>` : ''}
        ${ineligible ? `
        <div class="song-popup-geo-note">${escHtml(ineligibleSongMessage(song.songName, ineligibleReason))}</div>` : ''}
        <div class="${playClass}" id="spaPlay">
          <span class="spa-label">Play Song</span>
          <span class="${playCreditClass}">${formatCredits(costPlay, 'Credits')}${warnMark(canPlay)}</span>
        </div>
        <div class="${priorityClass}" id="spaPlayPriority">
          <span class="spa-label">Play Priority Song</span>
          <span class="${priorityCreditClass}">${formatCredits(costPriority, 'Credits')}${warnMark(canPriority)}</span>
        </div>`}
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

    // Ignore every tap until the sheet has finished sliding in: its actions move under the
    // finger meanwhile, so a quick second tap (e.g. a double-tap on the song that opened it)
    // could otherwise land on Play / Play Priority and spend credits, or on the background and
    // close it. Registered in the capture phase, so it runs before any action's own handler.
    let settled = false;
    const settle = () => { settled = true; };
    overlay.addEventListener('click', (e) => {
      if (!settled) {
        e.preventDefault();
        e.stopImmediatePropagation();
      }
    }, true);
    const sheet = overlay.querySelector('.song-popup');
    sheet.addEventListener('transitionend', (e) => {
      if (e.target === sheet && e.propertyName === 'transform') settle();
    });

    // Slide in after next frame
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        overlay.classList.add('song-popup-visible');
        // Fallback for when no transition runs (e.g. reduced motion): the slide takes 300ms.
        setTimeout(settle, 400);
      });
    });

    // Dismiss on overlay background click or handle tap
    overlay.addEventListener('click', (e) => {
      if (e.target === overlay) dismissSongPopup();
    });
    overlay.querySelector('.song-popup-handle').addEventListener('click', dismissSongPopup);

    // Reset timer on any interaction inside popup
    document.getElementById('songPopup').addEventListener('click', resetSongPopupTimer);

    async function submitPlay(priority, isPriorityPlay) {
      try {
        await geoApi('/api/song-queue/addSong', {
          method: 'POST',
          body: JSON.stringify({
            albumId: song.albumId, songId: song.songId, priority,
            priorityPlay: isPriorityPlay
          })
        });
        dismissSongPopup();
      } catch (err) {
        showAppAlert({ title: 'Play Song', message: queueErrorMessage(err, 'Could not add the song to the queue: ') });
      }
    }

    document.getElementById('spaPlayPriority')?.addEventListener('click', async () => {
      if (!canPriority) { resetSongPopupTimer(); return; }
      resetSongPopupTimer();
      await submitPlay(priorityLevel, true);
    });

    document.getElementById('spaPlay')?.addEventListener('click', async () => {
      if (!canPlay) { resetSongPopupTimer(); return; }
      resetSongPopupTimer();
      await submitPlay(1, false);
    });

    document.getElementById('spaArtist').addEventListener('click', () => {
      dismissSongPopup();
      if (song.albumId != null) navigateSub('artist-detail', { albumId: song.albumId });
      else if (song.artistId != null) navigateSub('artist-detail', { artistId: song.artistId });
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
            body: JSON.stringify({ locationId: state.locationId, albumId: song.albumId, songId: song.songId }),
          });
          state.favoriteSongIds.delete(key);
          if (label) label.textContent = 'Add to My Favorites';
        } else {
          await api('/api/users/playlists/favorites/songs', {
            method: 'POST',
            body: JSON.stringify({ locationId: state.locationId, albumId: song.albumId, songId: song.songId }),
          });
          state.favoriteSongIds.add(key);
          if (label) label.textContent = 'Remove from My Favorites';
        }
        refreshPlaylistsState();
      } catch (err) {
        showAppAlert({ title: 'My Favorites', message: 'Could not update My Favorites: ' + (err.message || err) });
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
      return `<div class="${cls}"><img src="/api/locations/${state.locationId}/song-library/albums/${p.firstSongAlbumId}/coverArt" alt=""
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

  const PENCIL_ICON_SVG = `<svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true" fill="none"
      stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
      <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z"/></svg>`;
  const SELECT_MULTIPLE_ICON_SVG = `<svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true" fill="none"
      stroke="currentColor" stroke-width="2" stroke-linejoin="round">
      <rect x="8" y="3" width="13" height="13" rx="2"/><path d="M16 16v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-9a2 2 0 0 1 2-2h3"/></svg>`;

  /** The band under the title: "Sorted by Track Order" plus the live song count. */
  function playlistSortBandHtml(count) {
    return `
      <div class="playlist-sort-band">
        <div class="playlist-sort-label">Sorted by Track Order</div>
        <div class="playlist-sort-count">Total Songs: <span class="playlist-song-total">${count}</span></div>
      </div>`;
  }

  function playlistSongInfoHtml(s) {
    return `
      ${coverArtHtml(s.albumId, '&#127925;')}
      <div class="result-info">
        <div class="result-title">${escHtml(s.songName || '')}</div>
        <div class="result-sub">${escHtml(s.artistName || '')}</div>
      </div>`;
  }

  function playlistSongsPath(name) {
    return `/api/users/playlists/${encodeURIComponent(name)}/songs`;
  }

  // ── Sub-screen: Playlist (view) ─────────────────────────────────────────
  async function renderPlaylistDetail(params = {}) {
    const playlist = params.playlist || {};
    const name = playlist.name || '';
    const isMyFavorites = name === 'My Favorites';

    contentPanel.innerHTML = subScreenShell(escHtml(name), '<div class="stub-placeholder">Loading…</div>');
    wireBackBtn();

    let songs = [];
    try {
      songs = await api(playlistSongsPath(name)) || [];
    } catch {
      contentPanel.querySelector('.sub-content').innerHTML = '<div class="stub-placeholder">Could not load playlist.</div>';
      return;
    }

    const songListHtml = songs.length
      ? songs.map((s, i) => `<div class="playlist-song-row" data-index="${i}">${playlistSongInfoHtml(s)}</div>`).join('')
      : '<div class="stub-placeholder">No songs yet. Add songs from the song menu.</div>';

    contentPanel.querySelector('.sub-content').innerHTML = `
      ${playlistSortBandHtml(songs.length)}
      <div class="playlist-view-actions">
        <button class="playlist-select-multiple-btn" id="selectMultipleBtn" ${songs.length ? '' : 'disabled'}>
          ${SELECT_MULTIPLE_ICON_SVG}<span>Select Multiple</span>
        </button>
        <div class="playlist-edit-anchor" id="playlistEditAnchor">
          <button class="playlist-edit-btn" id="playlistEditBtn" title="Edit playlist"
                  aria-label="Edit playlist" aria-haspopup="menu" aria-expanded="false">${PENCIL_ICON_SVG}</button>
        </div>
      </div>
      <div class="playlist-song-list">${songListHtml}</div>`;

    contentPanel.querySelectorAll('.playlist-song-row').forEach((row, i) => {
      row.addEventListener('click', () => showSongPopup(songs[i]));
    });

    document.getElementById('selectMultipleBtn').addEventListener('click', () => {
      navigateSub('playlist-multi-select', { playlist, songs });
    });

    // ── Pencil menu ──
    const editBtn = document.getElementById('playlistEditBtn');

    function closeEditMenu() {
      document.getElementById('playlistEditMenu')?.remove();
      editBtn.setAttribute('aria-expanded', 'false');
      document.removeEventListener('click', onOutsideClick, true);
    }

    function onOutsideClick(e) {
      const menu = document.getElementById('playlistEditMenu');
      if (!menu || (!menu.contains(e.target) && !editBtn.contains(e.target))) closeEditMenu();
    }

    editBtn.addEventListener('click', () => {
      if (document.getElementById('playlistEditMenu')) { closeEditMenu(); return; }
      const items = [{ id: 'order', label: 'Edit Track Order' }];
      if (!isMyFavorites) items.push({ id: 'rename', label: 'Rename' }, { id: 'delete', label: 'Delete' });
      const menu = document.createElement('div');
      menu.id = 'playlistEditMenu';
      menu.className = 'playlist-edit-menu';
      menu.setAttribute('role', 'menu');
      menu.innerHTML = items.map(item =>
        `<button class="playlist-edit-menu-item" role="menuitem" data-action="${item.id}">${item.label}</button>`).join('');
      document.getElementById('playlistEditAnchor').appendChild(menu);
      editBtn.setAttribute('aria-expanded', 'true');
      document.addEventListener('click', onOutsideClick, true);

      menu.querySelectorAll('.playlist-edit-menu-item').forEach(item => {
        item.addEventListener('click', () => {
          closeEditMenu();
          const action = item.dataset.action;
          if (action === 'order') navigateSub('playlist-edit-order', { playlist, songs });
          if (action === 'rename') renamePlaylist();
          if (action === 'delete') deletePlaylist();
        });
      });
    });

    async function renamePlaylist() {
      const newName = await showAppPrompt({
        title: 'Edit Playlist Name',
        placeholder: 'Playlist name',
        value: name,
        onSave: async (value) => {
          if (value === name) return null;
          if (value === 'My Favorites') return 'That name is reserved.';
          try {
            await api(`/api/users/playlists/${encodeURIComponent(name)}`, {
              method: 'PUT',
              body: JSON.stringify({ playlistName: value }),
            });
            return null;
          } catch (err) {
            return /: 409$/.test(err.message || '')
              ? 'A playlist with that name already exists.'
              : 'Could not rename the playlist.';
          }
        },
      });
      if (newName == null || newName === name) return;
      await refreshPlaylistsState();
      const renamed = { ...playlist, name: newName };
      const current = state.navStack[state.navStack.length - 1];
      if (current && current.screen === 'playlist-detail') current.params = { ...current.params, playlist: renamed };
      renderPlaylistDetail({ ...params, playlist: renamed });
    }

    async function deletePlaylist() {
      const confirmed = await showAppConfirm({
        title: 'Delete Playlist',
        message: `Are you sure you want to delete "${name}" from your playlists?`,
        confirmLabel: 'Delete',
      });
      if (!confirmed) return;
      try {
        await api(`/api/users/playlists/${encodeURIComponent(name)}`, { method: 'DELETE' });
        await refreshPlaylistsState();
        goBack();
      } catch (err) {
        showAppAlert({ title: 'Delete Playlist', message: 'Could not delete the playlist: ' + (err.message || err) });
      }
    }
  }

  // ── Sub-screen: Playlist — Edit Track Order ─────────────────────────────
  // Removals and reordering only change a working copy; Save writes the whole list in one PUT.
  function renderPlaylistEditOrder(params = {}) {
    const playlist = params.playlist || {};
    const name = playlist.name || '';
    const working = (params.songs || []).slice();

    contentPanel.innerHTML = subScreenShell(escHtml(name), `
      ${playlistSortBandHtml(working.length)}
      <div class="playlist-song-list" id="playlistOrderList"></div>`, `
      <div class="playlist-bottom-bar">
        <button class="playlist-bar-btn cancel" id="editOrderCancelBtn">Cancel</button>
        <button class="playlist-bar-btn primary" id="editOrderSaveBtn">Save</button>
      </div>`);
    wireBackBtn();

    const list = document.getElementById('playlistOrderList');

    function renderList() {
      contentPanel.querySelector('.playlist-song-total').textContent = working.length;
      list.innerHTML = working.length
        ? working.map((s, i) => `
          <div class="playlist-order-row" data-index="${i}">
            <button class="playlist-order-remove" data-index="${i}" title="Remove from playlist"
                    aria-label="Remove ${escHtml(s.songName || '')} from playlist"><span></span></button>
            ${playlistSongInfoHtml(s)}
            <div class="playlist-order-handle" title="Drag to reorder" aria-label="Drag to reorder">
              <span></span><span></span><span></span>
            </div>
          </div>`).join('')
        : '<div class="stub-placeholder">This playlist is empty.</div>';

      list.querySelectorAll('.playlist-order-remove').forEach(btn => {
        btn.addEventListener('click', () => {
          working.splice(parseInt(btn.dataset.index, 10), 1);
          renderList();
        });
      });
      list.querySelectorAll('.playlist-order-handle').forEach(handle => {
        handle.addEventListener('pointerdown', (e) => startDrag(e, handle));
      });
    }

    // Drag a row by its handle: the row follows the pointer, the rows it passes slide over to
    // make room, and the move is applied to the working copy on release.
    function startDrag(e, handle) {
      if (e.button !== undefined && e.button !== 0) return;
      e.preventDefault();
      const row = handle.closest('.playlist-order-row');
      const rows = Array.from(list.querySelectorAll('.playlist-order-row'));
      const fromIdx = rows.indexOf(row);
      const rowStep = rows.length > 1 ? rows[1].offsetTop - rows[0].offsetTop : row.offsetHeight;
      const startY = e.clientY;
      let toIdx = fromIdx;

      handle.setPointerCapture(e.pointerId);
      row.classList.add('dragging');

      function onMove(ev) {
        const minDy = -fromIdx * rowStep;
        const maxDy = (rows.length - 1 - fromIdx) * rowStep;
        const dy = Math.max(minDy, Math.min(maxDy, ev.clientY - startY));
        row.style.transform = `translateY(${dy}px)`;
        toIdx = fromIdx + Math.round(dy / rowStep);
        rows.forEach((r, i) => {
          if (r === row) return;
          let shift = 0;
          if (fromIdx < toIdx && i > fromIdx && i <= toIdx) shift = -rowStep;
          if (toIdx < fromIdx && i >= toIdx && i < fromIdx) shift = rowStep;
          r.style.transform = shift ? `translateY(${shift}px)` : '';
        });
      }

      function onEnd() {
        handle.removeEventListener('pointermove', onMove);
        handle.removeEventListener('pointerup', onEnd);
        handle.removeEventListener('pointercancel', onEnd);
        if (toIdx !== fromIdx) {
          const [moved] = working.splice(fromIdx, 1);
          working.splice(toIdx, 0, moved);
        }
        renderList();
      }

      handle.addEventListener('pointermove', onMove);
      handle.addEventListener('pointerup', onEnd);
      handle.addEventListener('pointercancel', onEnd);
    }

    document.getElementById('editOrderCancelBtn').addEventListener('click', goBack);
    document.getElementById('editOrderSaveBtn').addEventListener('click', async () => {
      const btn = document.getElementById('editOrderSaveBtn');
      btn.disabled = true;
      btn.textContent = 'Saving…';
      try {
        await api(playlistSongsPath(name), {
          method: 'PUT',
          body: JSON.stringify(working.map(s => ({ albumId: s.albumId, songId: s.songId }))),
        });
        await refreshPlaylistsState();
        goBack();
      } catch (err) {
        btn.disabled = false;
        btn.textContent = 'Save';
        showAppAlert({ title: 'Edit Track Order', message: 'Could not save the playlist: ' + (err.message || err) });
      }
    });

    renderList();
  }

  // ── Sub-screen: Playlist — Multi-Select Mode ────────────────────────────
  // Each song is checked with checkSongsEligibility before it can be selected (one song per tap,
  // or every unchecked song at once for "Select all songs"); results are kept for the life of the
  // screen. addMultipleSongs still re-checks every song and charges only for those it queues.
  async function renderPlaylistMultiSelect(params = {}) {
    const playlist = params.playlist || {};
    const name = playlist.name || '';
    const songs = params.songs || [];

    contentPanel.innerHTML = subScreenShell(
      `Multi-Select Mode<span class="sub-title-credits" id="msCredits">${escHtml(creditsWidgetText(state.numCredits))}</span>`,
      '<div class="stub-placeholder">Loading…</div>', `
      <div class="playlist-bottom-bar" id="msPlayBar" hidden>
        <button class="playlist-bar-btn primary wide" id="msPlayBtn"></button>
      </div>`);
    wireBackBtn();

    // Pair each displayed song with its stored identifier, which carries the song's locationId.
    let storedIds = [];
    try {
      [storedIds] = await Promise.all([
        api(`/api/users/playlists/${encodeURIComponent(name)}/songIdentifiers`).then(ids => ids || []),
        loadCredits(null),
      ]);
    } catch {
      contentPanel.querySelector('.sub-content').innerHTML = '<div class="stub-placeholder">Could not load playlist.</div>';
      return;
    }
    document.getElementById('msCredits').textContent = creditsWidgetText(state.numCredits);

    const unpaired = storedIds.slice();
    const ids = songs.map(s => {
      const k = unpaired.findIndex(id => id.albumId === s.albumId && id.songId === s.songId);
      return k >= 0 ? unpaired.splice(k, 1)[0] : { albumId: s.albumId, songId: s.songId };
    });

    const selected = new Set();
    const checking = new Set();
    const eligibility = new Map(); // song index -> null (eligible) or the ineligible reason

    contentPanel.querySelector('.sub-content').innerHTML = `
      <div class="multi-select-row multi-select-all" id="msSelectAll">
        <span class="ms-circle"></span>
        <span class="multi-select-all-label">Select all songs</span>
      </div>
      <div class="playlist-song-list">
        ${songs.map((s, i) => `
          <div class="multi-select-row" data-index="${i}">
            <span class="ms-circle"></span>
            ${playlistSongInfoHtml(s)}
          </div>`).join('')}
      </div>`;

    const rows = Array.from(contentPanel.querySelectorAll('.multi-select-row[data-index]'));
    const selectAllRow = document.getElementById('msSelectAll');
    const playBar = document.getElementById('msPlayBar');
    const playBtn = document.getElementById('msPlayBtn');
    const perSong = queueAddCost(1, false);

    // "All" means every song known to be selectable is selected (ineligible songs never can be).
    function allSelectableSelected() {
      return selected.size > 0
        && songs.every((s, i) => eligibility.has(i) && (eligibility.get(i) !== null || selected.has(i)));
    }

    function songCountText(n) {
      return `${n} song${n !== 1 ? 's' : ''}`;
    }

    function update() {
      rows.forEach((row, i) => {
        row.classList.toggle('selected', selected.has(i));
        row.classList.toggle('checking', checking.has(i));
        row.setAttribute('aria-checked', selected.has(i) ? 'true' : 'false');
      });
      selectAllRow.classList.toggle('selected', allSelectableSelected());
      selectAllRow.classList.toggle('checking', checking.size > 1);

      const n = selected.size;
      const total = n * perSong;
      playBar.hidden = n === 0;
      playBtn.classList.toggle('warn', total > state.numCredits);
      playBtn.textContent = total > state.numCredits
        ? `Play ${songCountText(n)} · ${formatShortfall(total - state.numCredits)}`
        : `Play ${songCountText(n)} (${formatCredits(total)})`;
    }

    function ineligibleMessage(i) {
      return ineligibleSongMessage(songs[i].songName, eligibility.get(i));
    }

    async function checkEligibility(indices) {
      const results = await api('/api/song-queue/checkSongsEligibility', {
        method: 'POST',
        body: JSON.stringify({ songIdentifiers: indices.map(i => ids[i]), priority: 1 }),
      }) || [];
      indices.forEach((i, k) => {
        const result = results[k];
        eligibility.set(i, result ? (result.ineligibleReason || null) : 'could not be checked');
      });
    }

    async function runCheck(indices) {
      indices.forEach(i => checking.add(i));
      update();
      try {
        await checkEligibility(indices);
        return true;
      } catch {
        showAppAlert({ title: "Can't Select Songs", message: 'Could not check whether the songs can be played. Please try again.' });
        return false;
      } finally {
        indices.forEach(i => checking.delete(i));
        update();
      }
    }

    async function toggleSong(i) {
      if (checking.has(i)) return;
      if (selected.has(i)) { selected.delete(i); update(); return; }
      if (!eligibility.has(i) && !(await runCheck([i]))) return;
      if (eligibility.get(i) !== null) {
        showAppAlert({ title: "Can't Select Song", message: ineligibleMessage(i) });
        return;
      }
      selected.add(i);
      update();
    }

    async function toggleAll() {
      if (checking.size) return;
      if (allSelectableSelected()) { selected.clear(); update(); return; }
      const unchecked = songs.map((s, i) => i).filter(i => !eligibility.has(i));
      if (unchecked.length && !(await runCheck(unchecked))) return;
      const skipped = [];
      songs.forEach((s, i) => {
        if (eligibility.get(i) === null) selected.add(i);
        else skipped.push(i);
      });
      update();
      if (skipped.length) {
        showAppAlert({
          title: `${songCountText(skipped.length)} couldn't be selected`,
          message: skipped.map(ineligibleMessage).join('\n\n'),
        });
      }
    }

    rows.forEach((row, i) => {
      row.setAttribute('role', 'checkbox');
      row.addEventListener('click', () => toggleSong(i));
    });
    selectAllRow.setAttribute('role', 'checkbox');
    selectAllRow.addEventListener('click', toggleAll);

    playBtn.addEventListener('click', async () => {
      const n = selected.size;
      if (!n) return;
      const total = n * perSong;
      if (total > state.numCredits) {
        const addFunds = await showAppConfirm({
          title: 'Not Enough Credits',
          message: `Playing ${songCountText(n)} costs ${formatCredits(total, 'Credits')}, but your balance is ${formatCredits(state.numCredits, 'Credits')}.`,
          confirmLabel: 'Add Funds',
        });
        if (addFunds) renderMain('addfunds');
        return;
      }

      playBtn.disabled = true;
      playBtn.textContent = 'Queueing…';
      try {
        const chosen = [...selected].sort((a, b) => a - b).map(i => ids[i]);
        const queued = await geoApi('/api/song-queue/addMultipleSongs', {
          method: 'POST',
          body: JSON.stringify({ songIdentifiers: chosen, priority: 1 }),
        }) || [];
        await loadCredits(null);
        const skipped = n - queued.length;
        await showAppAlert({
          title: queued.length ? 'Songs Queued' : 'No Songs Queued',
          message: `Queued ${queued.length} of ${songCountText(n)} for ${formatCredits(queued.length * perSong, 'Credits')}.`
            + (skipped ? `\n\n${songCountText(skipped)} ${skipped !== 1 ? 'were' : 'was'} skipped because ${skipped !== 1 ? 'they' : 'it'} could no longer be played.` : ''),
        });
        goBack();
      } catch (err) {
        playBtn.disabled = false;
        update();
        showAppAlert({ title: 'Could Not Play Songs', message: queueErrorMessage(err, 'Could not queue the songs: ') });
      }
    });

    update();
  }

  // ── Location header + picker sheet ─────────────────────────────────────

  function locationLogoSrc(location) {
    return location && location.logoName ? `/images/${location.logoName}` : '/images/JukeANatorLogo.png';
  }

  function locationButtonInnerHtml() {
    const loc = state.currentLocation;
    const name = loc ? loc.name : 'Select Location';
    const arrow = state.locations.length > 1 ? '<span class="location-arrow">&#8964;</span>' : '';
    return `
      <img class="location-logo" src="${locationLogoSrc(loc)}" alt=""
        onerror="this.src='/images/JukeANatorLogo.png'">
      <span class="location-name">${escHtml(name)}</span>
      ${arrow}`;
  }

  async function selectLocation(loc) {
    state.currentLocation = loc;
    state.locationId = loc.locationId;
    subscribeToLocation();
    await Promise.all([loadPricingConfig(), loadGeoFenceStatus()]);
    renderMain(state.currentMainTab);
  }

  function showLocationPickerSheet() {
    const existing = document.getElementById('locationPickerOverlay');
    if (existing) existing.remove();

    const listHtml = state.locations.map((loc, i) => `
      <div class="select-playlist-row" data-index="${i}">
        <div class="select-playlist-thumb">
          <img src="${locationLogoSrc(loc)}" alt="" onerror="this.src='/images/JukeANatorLogo.png'"
            style="width:100%;height:100%;object-fit:contain;">
        </div>
        <div class="select-playlist-name">${escHtml(loc.name)}${loc.online === false
          ? ' <span class="location-offline-badge">offline</span>' : ''}</div>
      </div>`).join('');

    const overlay = document.createElement('div');
    overlay.id = 'locationPickerOverlay';
    overlay.className = 'song-popup-overlay';
    overlay.innerHTML = `
      <div class="song-popup select-playlist-sheet" id="locationPickerSheet">
        <div class="song-popup-handle"></div>
        <div class="select-playlist-title">Select a location</div>
        ${listHtml}
      </div>`;

    document.getElementById('app-shell').appendChild(overlay);
    requestAnimationFrame(() => requestAnimationFrame(() => overlay.classList.add('song-popup-visible')));

    overlay.addEventListener('click', (e) => {
      if (e.target === overlay) dismissLocationPickerSheet();
    });

    state.locations.forEach((loc, i) => {
      overlay.querySelectorAll('.select-playlist-row')[i]?.addEventListener('click', () => {
        dismissLocationPickerSheet();
        selectLocation(loc);
      });
    });
  }

  function dismissLocationPickerSheet() {
    const overlay = document.getElementById('locationPickerOverlay');
    if (!overlay) return;
    overlay.classList.remove('song-popup-visible');
    setTimeout(() => overlay.remove(), 300);
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
              body: JSON.stringify({ locationId: state.locationId, albumId: song.albumId, songId: song.songId }),
            });
            await refreshPlaylistsState();
          } catch (err) {
            showAppAlert({ title: 'Add to Playlist', message: 'Could not add the song to the playlist: ' + (err.message || err) });
          }
        });
    });

    document.getElementById('selectPlaylistCreate').addEventListener('click', () => {
      dismissSelectPlaylistSheet();
      showCreatePlaylistDialog({ onCreated: async (name) => {
        try {
          await api(`/api/users/playlists/${encodeURIComponent(name)}/songs`, {
            method: 'POST',
            body: JSON.stringify({ locationId: state.locationId, albumId: song.albumId, songId: song.songId }),
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

  // ── Add Funds: Select Payment Method sheet ──────────────────────────────
  // Uses Braintree's modular braintree-web client SDK (Hosted Fields + PayPal Checkout +
  // Venmo + Google Pay + Apple Pay), NOT the deprecated Drop-in widget -- each method is its
  // own component, feature-detected and hidden when unsupported in this browser/device.

  // Apple Pay requires an Apple Developer account (paid) plus a merchant domain-association
  // file hosted on this server -- neither is set up yet, so this stays off regardless of
  // browser support until that's done. Flip to true once both are in place.
  const APPLE_PAY_ENABLED = false;

  let _hostedFieldsInstance = null;

  function loadScriptOnce(src, attrs = {}) {
    return new Promise((resolve, reject) => {
      if (document.querySelector(`script[data-src="${src}"]`)) { resolve(); return; }
      const script = document.createElement('script');
      script.src = src;
      script.dataset.src = src;
      Object.entries(attrs).forEach(([k, v]) => script.setAttribute(k, v));
      script.onload = () => resolve();
      script.onerror = () => reject(new Error(`Failed to load ${src}`));
      document.head.appendChild(script);
    });
  }

  function paymentMethodRowHtml(id, label) {
    return `
      <div class="payment-method-row" id="${id}" hidden>
        <div class="payment-method-row-label">${label}</div>
        <div class="pmr-mount" id="${id}Mount"></div>
      </div>`;
  }

  async function showPaymentMethodSheet(pkg, onSuccess) {
    const existing = document.getElementById('paymentMethodOverlay');
    if (existing) existing.remove();
    _hostedFieldsInstance = null;

    const overlay = document.createElement('div');
    overlay.id = 'paymentMethodOverlay';
    overlay.className = 'song-popup-overlay';
    overlay.innerHTML = `
      <div class="song-popup payment-method-sheet" id="paymentMethodSheet">
        <div class="song-popup-handle"></div>
        <div class="payment-sheet-title">Select Payment Method</div>
        <div id="paymentMethodStatus" class="stub-placeholder">Loading payment options…</div>
        <div id="paymentMethodList" hidden>
          ${paymentMethodRowHtml('pmCard', 'Credit or Debit Card')}
          ${paymentMethodRowHtml('pmPayPal', 'PayPal')}
          ${paymentMethodRowHtml('pmVenmo', 'Venmo')}
          ${paymentMethodRowHtml('pmGooglePay', 'Google Pay')}
          ${paymentMethodRowHtml('pmApplePay', 'Apple Pay')}
        </div>
        <button class="add-funds-action-btn" id="payNowBtn" hidden>Pay $${Number(pkg.priceUsd).toFixed(2)}</button>
      </div>`;

    document.getElementById('app-shell').appendChild(overlay);
    requestAnimationFrame(() => requestAnimationFrame(() => overlay.classList.add('song-popup-visible')));
    overlay.addEventListener('click', (e) => { if (e.target === overlay) dismissPaymentMethodSheet(); });
    overlay.querySelector('.song-popup-handle').addEventListener('click', dismissPaymentMethodSheet);

    async function submitPayment(nonce) {
      const payBtn = document.getElementById('payNowBtn');
      if (payBtn) { payBtn.disabled = true; payBtn.textContent = 'Processing…'; }
      try {
        const response = await api('/api/users/add-funds', {
          method: 'POST',
          body: JSON.stringify({ packageId: pkg.id, paymentMethodNonce: nonce }),
        });
        dismissPaymentMethodSheet();
        onSuccess(response);
      } catch (err) {
        if (payBtn) { payBtn.disabled = false; payBtn.textContent = `Pay $${Number(pkg.priceUsd).toFixed(2)}`; }
        showAppAlert({ title: 'Payment Failed', message: 'Payment failed: ' + (err.message || err) });
      }
    }

    try {
      const { clientToken } = await api('/api/users/payment/client-token');
      const client = await braintree.client.create({ authorization: clientToken });

      document.getElementById('paymentMethodStatus').hidden = true;
      document.getElementById('paymentMethodList').hidden = false;

      await Promise.all([
        initCardField(client),
        initPayPal(client, clientToken, pkg, submitPayment),
        initVenmo(client, submitPayment),
        initGooglePay(client, pkg, submitPayment),
        initApplePay(client, pkg, submitPayment),
      ]);

      const payBtn = document.getElementById('payNowBtn');
      if (_hostedFieldsInstance) {
        payBtn.hidden = false;
        payBtn.addEventListener('click', async () => {
          payBtn.disabled = true;
          payBtn.textContent = 'Processing…';
          try {
            const { nonce } = await _hostedFieldsInstance.tokenize();
            await submitPayment(nonce);
          } catch (err) {
            payBtn.disabled = false;
            payBtn.textContent = `Pay $${Number(pkg.priceUsd).toFixed(2)}`;
          }
        });
      }
    } catch (err) {
      document.getElementById('paymentMethodStatus').textContent = 'Could not load payment options.';
    }
  }

  function dismissPaymentMethodSheet() {
    const overlay = document.getElementById('paymentMethodOverlay');
    if (!overlay) return;
    if (_hostedFieldsInstance) {
      _hostedFieldsInstance.teardown().catch(() => {});
      _hostedFieldsInstance = null;
    }
    overlay.classList.remove('song-popup-visible');
    setTimeout(() => overlay.remove(), 300);
  }

  async function initCardField(client) {
    const row = document.getElementById('pmCard');
    row.hidden = false;
    row.querySelector('.pmr-mount').innerHTML = `
      <div class="card-field" id="cardNumber"></div>
      <div class="card-field-row">
        <div class="card-field" id="cardExpiry"></div>
        <div class="card-field" id="cardCvv"></div>
      </div>`;
    try {
      _hostedFieldsInstance = await braintree.hostedFields.create({
        client,
        styles: { input: { color: '#e8e8ee', 'font-size': '15px' } },
        fields: {
          number: { selector: '#cardNumber', placeholder: 'Card number' },
          expirationDate: { selector: '#cardExpiry', placeholder: 'MM/YY' },
          cvv: { selector: '#cardCvv', placeholder: 'CVV' },
        },
      });
    } catch (err) {
      row.hidden = true;
    }
  }

  async function initPayPal(client, clientToken, pkg, submitPayment) {
    const row = document.getElementById('pmPayPal');
    try {
      const paypalCheckoutInstance = await braintree.paypalCheckout.create({ client });
      await loadScriptOnce(
        'https://www.paypal.com/sdk/js?client-id=sb&currency=USD&intent=capture&commit=true',
        { 'data-client-token': clientToken });
      if (!window.paypal) { row.hidden = true; return; }
      row.hidden = false;
      paypal.Buttons({
        fundingSource: paypal.FUNDING.PAYPAL,
        style: { layout: 'horizontal', height: 40, tagline: false },
        createOrder: () => paypalCheckoutInstance.createPayment({
          flow: 'checkout',
          amount: Number(pkg.priceUsd).toFixed(2),
          currency: 'USD',
          intent: 'capture',
        }),
        onApprove: (data) => paypalCheckoutInstance.tokenizePayment(data)
          .then((payload) => submitPayment(payload.nonce)),
        onError: () => { row.hidden = true; },
      }).render(row.querySelector('.pmr-mount'));
    } catch (err) {
      row.hidden = true;
    }
  }

  async function initVenmo(client, submitPayment) {
    const row = document.getElementById('pmVenmo');
    try {
      const venmo = await braintree.venmo.create({ client, allowNewBrowserTab: false });
      if (!venmo.isBrowserSupported()) { row.hidden = true; return; }
      row.hidden = false;
      row.querySelector('.pmr-mount').innerHTML =
        '<button type="button" class="payment-method-row-btn">Pay with Venmo</button>';
      row.querySelector('.payment-method-row-btn').addEventListener('click', async () => {
        try {
          const { nonce } = await venmo.tokenize();
          submitPayment(nonce);
        } catch (err) { /* user cancelled or Venmo app unavailable */ }
      });
    } catch (err) {
      row.hidden = true;
    }
  }

  async function initGooglePay(client, pkg, submitPayment) {
    const row = document.getElementById('pmGooglePay');
    if (!window.google || !google.payments) { row.hidden = true; return; }
    try {
      const googlePayment = await braintree.googlePayment.create({ client, googlePayVersion: 2 });
      const paymentsClient = new google.payments.api.PaymentsClient({ environment: 'TEST' });
      const baseRequest = googlePayment.createPaymentDataRequest();
      const isReady = await paymentsClient.isReadyToPay({
        apiVersion: 2, apiVersionMinor: 0, allowedPaymentMethods: baseRequest.allowedPaymentMethods,
      });
      if (!isReady.result) { row.hidden = true; return; }
      row.hidden = false;
      row.querySelector('.pmr-mount').innerHTML =
        '<button type="button" class="payment-method-row-btn">Pay with Google Pay</button>';
      row.querySelector('.payment-method-row-btn').addEventListener('click', async () => {
        try {
          const paymentDataRequest = googlePayment.createPaymentDataRequest({
            transactionInfo: {
              currencyCode: 'USD',
              totalPriceStatus: 'FINAL',
              totalPrice: Number(pkg.priceUsd).toFixed(2),
            },
          });
          const paymentData = await paymentsClient.loadPaymentData(paymentDataRequest);
          const result = await googlePayment.parseResponse(paymentData);
          submitPayment(result.nonce);
        } catch (err) { /* user cancelled */ }
      });
    } catch (err) {
      row.hidden = true;
    }
  }

  async function initApplePay(client, pkg, submitPayment) {
    const row = document.getElementById('pmApplePay');
    if (!APPLE_PAY_ENABLED) { row.hidden = true; return; }
    if (!window.ApplePaySession || !ApplePaySession.canMakePayments()) { row.hidden = true; return; }
    try {
      const applePay = await braintree.applePay.create({ client });
      row.hidden = false;
      row.querySelector('.pmr-mount').innerHTML =
        '<button type="button" class="payment-method-row-btn">Pay with Apple Pay</button>';
      row.querySelector('.payment-method-row-btn').addEventListener('click', () => {
        const paymentRequest = applePay.createPaymentRequest({
          total: { label: 'JukeANator', amount: Number(pkg.priceUsd).toFixed(2) },
        });
        const session = new ApplePaySession(3, paymentRequest);
        session.onvalidatemerchant = (event) => {
          applePay.performValidation({ validationURL: event.validationURL, displayName: 'JukeANator' })
            .then((merchantSession) => session.completeMerchantValidation(merchantSession))
            .catch(() => session.abort());
        };
        session.onpaymentauthorized = (event) => {
          applePay.tokenize({ token: event.payment.token })
            .then((payload) => {
              session.completePayment(ApplePaySession.STATUS_SUCCESS);
              submitPayment(payload.nonce);
            })
            .catch(() => session.completePayment(ApplePaySession.STATUS_FAILURE));
        };
        session.begin();
      });
    } catch (err) {
      row.hidden = true;
    }
  }

  // ── Add Funds: Transaction Successful screen ────────────────────────────
  function renderTransactionSuccess(response) {
    state.numCredits = response.numCredits;

    contentPanel.innerHTML = `
      <div class="tx-success-screen">
        <div class="tx-success-icon">&#10003;</div>
        <div class="tx-success-title">Transaction Successful!</div>
        <div class="tx-success-row">
          <span>Credits Added</span>
          <span>${response.creditsAdded}${response.bonusCreditsAdded ? ` + ${response.bonusCreditsAdded} bonus` : ''}</span>
        </div>
        <div class="tx-success-row">
          <span>Payment Method</span>
          <span>${escHtml(response.paymentSource || '')}</span>
        </div>
        <div class="tx-success-row">
          <span>New Balance</span>
          <span>${escHtml(creditsWidgetText(response.numCredits))}</span>
        </div>
        <div class="tx-success-row tx-success-row--muted">
          <span>Transaction ID</span>
          <span>${escHtml(response.transactionId || '')}</span>
        </div>
        <button class="add-funds-action-btn" id="txSuccessReturnBtn">Return to Music Selection</button>
      </div>`;

    document.getElementById('txSuccessReturnBtn').addEventListener('click', () => renderMain('music'));
  }

  // ── Themed dialogs (in place of the browser's alert/confirm/prompt) ─────

  /** Opens a centered dialog box over the app; tapping the backdrop or pressing Escape calls onDismiss. */
  function openAppDialog(innerHtml, onDismiss) {
    const dialog = document.createElement('div');
    dialog.className = 'app-dialog-backdrop';
    dialog.innerHTML = `<div class="app-dialog-box" role="dialog" aria-modal="true">${innerHtml}</div>`;
    document.getElementById('app-shell').appendChild(dialog);
    dialog.addEventListener('click', (e) => { if (e.target === dialog) onDismiss(); });
    dialog.addEventListener('keydown', (e) => { if (e.key === 'Escape') onDismiss(); });
    return dialog;
  }

  function appDialogHeadHtml(title, message) {
    return (title ? `<div class="app-dialog-title">${escHtml(title)}</div>` : '')
      + (message ? `<div class="app-dialog-message">${escHtml(message)}</div>` : '');
  }

  /** Resolves once the user dismisses the dialog. */
  function showAppAlert({ title = '', message = '', okLabel = 'OK' } = {}) {
    return new Promise(resolve => {
      const close = () => { dialog.remove(); resolve(); };
      const dialog = openAppDialog(`
        ${appDialogHeadHtml(title, message)}
        <div class="app-dialog-actions">
          <button class="app-dialog-btn primary">${escHtml(okLabel)}</button>
        </div>`, close);
      const okBtn = dialog.querySelector('.app-dialog-btn.primary');
      okBtn.addEventListener('click', close);
      okBtn.focus();
    });
  }

  /** Resolves to true when the user confirms, false otherwise. */
  function showAppConfirm({ title = '', message = '', confirmLabel = 'OK', cancelLabel = 'Cancel' } = {}) {
    return new Promise(resolve => {
      const close = (result) => { dialog.remove(); resolve(result); };
      const dialog = openAppDialog(`
        ${appDialogHeadHtml(title, message)}
        <div class="app-dialog-actions">
          <button class="app-dialog-btn cancel">${escHtml(cancelLabel)}</button>
          <button class="app-dialog-btn primary">${escHtml(confirmLabel)}</button>
        </div>`, () => close(false));
      dialog.querySelector('.app-dialog-btn.cancel').addEventListener('click', () => close(false));
      const confirmBtn = dialog.querySelector('.app-dialog-btn.primary');
      confirmBtn.addEventListener('click', () => close(true));
      confirmBtn.focus();
    });
  }

  /**
   * Asks for a single line of text. onSave(value) runs while the dialog stays open; it resolves to
   * null on success or to an error message, which is shown inline so the user can try again.
   * Resolves to the saved value, or null when cancelled.
   */
  function showAppPrompt({ title = '', placeholder = '', value = '', saveLabel = 'Save',
                           cancelLabel = 'Cancel', maxLength = 64, onSave = async () => null } = {}) {
    return new Promise(resolve => {
      const close = (result) => { dialog.remove(); resolve(result); };
      const dialog = openAppDialog(`
        ${appDialogHeadHtml(title, '')}
        <input class="app-dialog-input" type="text" placeholder="${escHtml(placeholder)}" maxlength="${maxLength}">
        <div class="app-dialog-error"></div>
        <div class="app-dialog-actions">
          <button class="app-dialog-btn cancel">${escHtml(cancelLabel)}</button>
          <button class="app-dialog-btn primary">${escHtml(saveLabel)}</button>
        </div>`, () => close(null));
      const input = dialog.querySelector('.app-dialog-input');
      const error = dialog.querySelector('.app-dialog-error');
      const saveBtn = dialog.querySelector('.app-dialog-btn.primary');
      input.value = value;
      input.focus();
      input.select();

      async function save() {
        const entered = input.value.trim();
        if (!entered || saveBtn.disabled) return;
        saveBtn.disabled = true;
        saveBtn.textContent = 'Saving…';
        error.textContent = '';
        const failure = await onSave(entered);
        if (failure) {
          error.textContent = failure;
          saveBtn.disabled = false;
          saveBtn.textContent = saveLabel;
          input.focus();
          return;
        }
        close(entered);
      }

      dialog.querySelector('.app-dialog-btn.cancel').addEventListener('click', () => close(null));
      saveBtn.addEventListener('click', save);
      input.addEventListener('keydown', (e) => { if (e.key === 'Enter') save(); });
    });
  }

  async function showCreatePlaylistDialog(opts = {}) {
    const name = await showAppPrompt({
      title: 'Create New Playlist',
      placeholder: 'Playlist name',
      onSave: async (value) => {
        try {
          await api('/api/users/playlists', {
            method: 'POST',
            body: JSON.stringify({ playlistName: value }),
          });
          return null;
        } catch {
          return 'That name is already taken or is not valid.';
        }
      },
    });
    if (name == null) return;
    await refreshPlaylistsState();
    if (opts.onCreated) await opts.onCreated(name);
  }

  // ── WebSocket ───────────────────────────────────────────────────────────
  // The queue and Now Playing topics are per location (/topic/location/{id}/...), published the
  // same way in every app.mode; a patron's own credits and recent plays arrive on /user/queue/...,
  // which the server routes by the token sent on CONNECT -- so the connection is reopened whenever
  // the signed-in user changes (see setAuth/clearAuth) and the location topics are re-subscribed
  // whenever the location changes (see selectLocation).
  let stompClient = null;
  let stompReconnectTimer = null;
  let locationSubscriptions = [];

  function connectWebSocket() {
    if (stompClient && stompClient.connected) return;
    clearTimeout(stompReconnectTimer);
    const client = Stomp.over(new SockJS('/ws'));
    stompClient = client;
    client.debug = () => {};
    const connectHeaders = state.token ? { token: state.token } : {};
    client.connect(connectHeaders, () => {
      subscribeToLocation();

      // User-specific updates — only fire for the logged-in user
      if (state.token) {
        client.subscribe('/user/queue/credits', (frame) => {
          const msg = JSON.parse(frame.body);
          state.numCredits = msg.numCredits ?? 0;
          const widget = document.getElementById('creditsValue');
          if (widget) widget.textContent = creditsWidgetText(state.numCredits);
        });

        client.subscribe('/user/queue/recent-plays', (frame) => {
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
    }, () => {
      // Only the current connection reconnects -- one replaced by reconnectWebSocket() stays closed.
      if (stompClient === client) stompReconnectTimer = setTimeout(connectWebSocket, 3000);
    });
  }

  /** Reopens the connection as whoever is now signed in (or no one). */
  function reconnectWebSocket() {
    const previous = stompClient;
    stompClient = null;
    locationSubscriptions = [];
    clearTimeout(stompReconnectTimer);
    if (previous) {
      try { previous.disconnect(() => {}); } catch { /* already closed */ }
    }
    connectWebSocket();
  }

  /** (Re)subscribes to the current location's queue and Now Playing topics. */
  function subscribeToLocation() {
    locationSubscriptions.forEach(sub => { try { sub.unsubscribe(); } catch { /* closed */ } });
    locationSubscriptions = [];
    if (!stompClient || !stompClient.connected || state.locationId == null) return;
    const topic = `/topic/location/${state.locationId}`;

    locationSubscriptions.push(stompClient.subscribe(`${topic}/now-playing`, (frame) => {
      activeSongQueueRefresh?.();
      const widget = document.getElementById('nowPlayingWidget');
      if (!widget) return;
      const msg = JSON.parse(frame.body);
      setNowPlayingWidget(widget, msg.song);
    }));

    // The whole queue on every change, [] once it is empty -- keeps the "View Queue" button in
    // sync with whether there's anything queued, and the Song Queue screen (if showing) in sync
    // with the queue itself.
    locationSubscriptions.push(stompClient.subscribe(`${topic}/queue`, (frame) => {
      const queuedSongs = JSON.parse(frame.body) || [];
      state.queueHasSongs = queuedSongs.length > 0;
      updateViewQueueVisibility();
      activeSongQueueRefresh?.();
    }));
  }

  // ── Init ────────────────────────────────────────────────────────────────
  (async () => {
    try {
      state.locationId = await api('/api/users/own-location-id');
    } catch {
      state.locationId = null;
    }
    try {
      state.currentLocation = await api('/api/users/own-location');
    } catch {
      state.currentLocation = null;
    }
    try {
      // Master-only picker list; 404s on standalone/slave — no list to pick from there.
      state.locations = await api('/api/locations') || [];
    } catch {
      state.locations = [];
    }
    if (!state.currentLocation && state.locations.length) {
      state.currentLocation = state.locations[0];
      state.locationId = state.currentLocation.locationId;
    }
    await Promise.all([loadPricingConfig(), loadGeoFenceStatus()]);
    renderMain('music');
    connectWebSocket();
  })();
})();
