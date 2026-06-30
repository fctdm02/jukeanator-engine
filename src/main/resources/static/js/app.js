(() => {
  // ── State ───────────────────────────────────────────────────────────────
  const state = {
    token: localStorage.getItem('jwt'),
    role: localStorage.getItem('role'),
    emailAddress: localStorage.getItem('emailAddress'),
    pendingScreen: null, // { screen, params } to navigate to after login
    navStack: [],        // { screen, params } entries for back navigation
    currentMainTab: 'music',
  };

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
      state.navStack.pop(); // will be re-pushed by renderSubScreen
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

        <main class="home-content" id="homeContent">
          ${tab === 'music' ? '<div class="stub-placeholder">HOME — coming soon</div>' : ''}
        </main>

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

    await Promise.all([
      loadCredits(document.getElementById('creditsValue')),
      loadNowPlaying(document.getElementById('nowPlayingWidget')),
    ]);

    if (tab === 'addfunds') {
      loadAddFunds(document.getElementById('homeContent'));
    }
  }

  // ── Header data loaders ─────────────────────────────────────────────────
  async function loadCredits(widget) {
    if (!widget) return;
    if (!state.token) { widget.textContent = 'Credits: 0'; return; }
    try {
      const profile = await api('/api/users/me');
      widget.textContent = `Credits: ${profile.numCredits ?? 0}`;
    } catch { widget.textContent = 'Credits: 0'; }
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
    const pending = state.pendingScreen;
    state.pendingScreen = null;
    if (!pending) { renderMain('music'); return; }
    if (pending.screen === 'addfunds') { renderMain('addfunds'); }
    else if (pending.screen === 'my-account') { navigateSub('my-account'); }
    else { renderMain('music'); }
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
        setNowPlayingWidget(widget, msg.song);
      });
    }, () => setTimeout(connectWebSocket, 3000));
  }

  // ── Init ────────────────────────────────────────────────────────────────
  renderMain('music');
  connectWebSocket();
})();
