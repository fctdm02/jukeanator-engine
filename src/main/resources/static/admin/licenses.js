// License administration page (master mode). Shares the Web/Mobile UI's sign-in: the JWT is kept
// in localStorage under "jwt", the same key /app/js/app.js uses. Every value shown comes from the
// server and may originate from anonymous legacy heartbeats, so it is always inserted as text.
(function () {
  'use strict';

  const API = '/api/admin/licenses';
  const $ = (id) => document.getElementById(id);
  let editingCustomerId = null;

  function token() {
    try { return localStorage.getItem('jwt'); } catch (e) { return null; }
  }

  function setAuth(auth) {
    try {
      localStorage.setItem('jwt', auth.token);
      localStorage.setItem('role', auth.role);
      localStorage.setItem('emailAddress', auth.emailAddress);
    } catch (e) { /* storage unavailable: session-only sign-in */ }
    sessionToken = auth.token;
  }

  function clearAuth() {
    try {
      localStorage.removeItem('jwt');
      localStorage.removeItem('role');
      localStorage.removeItem('emailAddress');
    } catch (e) { /* ignore */ }
    sessionToken = null;
  }

  let sessionToken = token();

  async function api(path, options = {}) {
    const headers = { 'Content-Type': 'application/json' };
    if (sessionToken) headers.Authorization = 'Bearer ' + sessionToken;
    const res = await fetch(path, { ...options, headers });
    if (res.status === 401) {
      clearAuth();
      showLogin('Your session has expired. Please sign in again.');
      throw new Error('Not signed in');
    }
    if (res.status === 403) throw new Error('This account is not an administrator.');
    const text = await res.text();
    const body = text ? JSON.parse(text) : null;
    if (!res.ok) throw new Error((body && (body.message || body.error)) || ('HTTP ' + res.status));
    return body;
  }

  function el(tag, props = {}, ...children) {
    const node = document.createElement(tag);
    for (const [k, v] of Object.entries(props)) {
      if (k === 'class') node.className = v;
      else if (k.startsWith('on')) node.addEventListener(k.slice(2), v);
      else node.setAttribute(k, v);
    }
    for (const c of children) {
      if (c == null) continue;
      node.append(c instanceof Node ? c : document.createTextNode(String(c)));
    }
    return node;
  }

  function fmt(instant) {
    return instant ? new Date(instant).toLocaleString() : '';
  }

  // ------------------------------------------------------------------ views

  function showLogin(message) {
    $('adminView').hidden = true;
    $('loginView').hidden = false;
    $('loginError').textContent = message || '';
    $('who').textContent = '';
  }

  function showAdmin() {
    $('loginView').hidden = true;
    $('adminView').hidden = false;
    let email = '';
    try { email = localStorage.getItem('emailAddress') || ''; } catch (e) { /* ignore */ }
    $('who').replaceChildren(email + ' ',
      el('button', { type: 'button', onclick: () => { clearAuth(); showLogin(); } }, 'Sign out'));
    search();
  }

  async function search() {
    $('adminError').textContent = '';
    try {
      const q = encodeURIComponent($('searchBox').value.trim());
      render(await api(API + '/customers?q=' + q));
    } catch (e) {
      $('adminError').textContent = e.message;
    }
  }

  function render(customers) {
    const results = $('results');
    if (!customers.length) {
      results.replaceChildren(el('p', { class: 'hint' }, 'No customers found.'));
      return;
    }
    results.replaceChildren(...customers.map(renderCustomer));
  }

  function renderCustomer(c) {
    const head = el('div', { class: 'customer-head' },
      el('div', {},
        el('strong', {}, c.customerName),
        c.revoked ? el('span', { class: 'badge revoked' }, 'revoked') : null,
        c.autoCreated ? el('span', { class: 'badge auto' }, 'auto-created') : null,
        el('div', { class: 'meta' },
          c.email + '  |  type ' + c.licenseType + '  |  ' + c.availableLicenses
          + ' license(s) available  |  added ' + fmt(c.dateAdded))),
      el('div', { class: 'customer-actions' },
        el('button', { type: 'button', onclick: () => openCustomerDialog(c) }, 'Edit'),
        el('button', { type: 'button', onclick: () => addLicenses(c) }, 'Add licenses'),
        el('button', { type: 'button', class: c.revoked ? '' : 'danger',
          onclick: () => setRevoked(c) }, c.revoked ? 'Unrevoke' : 'Revoke')));

    const notes = c.notes ? el('p', { class: 'hint' }, c.notes) : null;

    let machines;
    if (!c.machines.length) {
      machines = el('p', { class: 'hint' }, 'No registered computers.');
    } else {
      const header = el('tr', {}, ...['Serial number', 'Paid through', 'Location', 'Client',
        'Last heartbeat', 'Heartbeats', 'License issued', ''].map((h) => el('th', {}, h)));
      const rows = c.machines.map((m) => el('tr', {},
        el('td', {}, m.serialNumber, m.revoked ? el('span', { class: 'badge revoked' }, 'revoked')
          : null),
        el('td', {}, m.lastPaidQuarter || '(perpetual)'),
        el('td', {}, m.locationName || ''),
        el('td', {}, m.clientKind + (m.appVersion ? ' ' + m.appVersion : '')
          + (m.appMode ? ' (' + m.appMode + ')' : '') + (m.buildDate ? ' build ' + m.buildDate
          : '')),
        el('td', {}, fmt(m.lastHeartbeatAt)),
        el('td', {}, m.heartbeatCount),
        el('td', {}, fmt(m.lastLicenseIssuedAt)),
        el('td', { class: 'actions' },
          el('button', { type: 'button', onclick: () => extend(m) }, '+1 year'),
          el('button', { type: 'button', onclick: () => setQuarter(m) }, 'Set quarter'),
          el('button', { type: 'button', onclick: () => moveMachine(m) }, 'Move'),
          el('button', { type: 'button', class: m.revoked ? '' : 'danger',
            onclick: () => setMachineRevoked(m) }, m.revoked ? 'Unrevoke' : 'Revoke'))));
      machines = el('div', { class: 'table-wrap' }, el('table', {}, el('thead', {}, header),
        el('tbody', {}, ...rows)));
    }
    return el('div', { class: 'customer' }, head, notes, machines);
  }

  // ------------------------------------------------------------------ actions

  async function run(action) {
    $('adminError').textContent = '';
    try {
      await action();
      await search();
    } catch (e) {
      $('adminError').textContent = e.message;
    }
  }

  function addLicenses(c) {
    const n = prompt('Add how many licenses for ' + c.email + '?', '1');
    if (n === null) return;
    const count = parseInt(n, 10);
    if (!Number.isFinite(count) || count === 0) return;
    run(() => api(API + '/customers/' + c.id + '/add-licenses',
      { method: 'POST', body: JSON.stringify({ count }) }));
  }

  function setRevoked(c) {
    const revoked = !c.revoked;
    if (revoked && !confirm('Revoke every computer of ' + c.email + '?')) return;
    run(() => api(API + '/customers/' + c.id + '/revoke',
      { method: 'POST', body: JSON.stringify({ revoked }) }));
  }

  function extend(m) {
    run(() => api(API + '/machines/' + m.id + '/extend',
      { method: 'POST', body: JSON.stringify({ quarters: 4 }) }));
  }

  function setQuarter(m) {
    const q = prompt('Paid-through quarter for ' + m.serialNumber
      + ' (e.g. 4Q2027; blank for perpetual tiers):', m.lastPaidQuarter || '');
    if (q === null) return;
    run(() => api(API + '/machines/' + m.id,
      { method: 'PUT', body: JSON.stringify({ lastPaidQuarter: q.trim().toUpperCase() }) }));
  }

  function moveMachine(m) {
    const id = prompt('Move ' + m.serialNumber + ' to customer id:', String(m.customerId));
    if (id === null) return;
    const customerId = parseInt(id, 10);
    if (!Number.isFinite(customerId)) return;
    run(() => api(API + '/machines/' + m.id,
      { method: 'PUT', body: JSON.stringify({ customerId }) }));
  }

  function setMachineRevoked(m) {
    const revoked = !m.revoked;
    if (revoked && !confirm('Revoke ' + m.serialNumber + '?')) return;
    run(() => api(API + '/machines/' + m.id,
      { method: 'PUT', body: JSON.stringify({ revoked }) }));
  }

  function openCustomerDialog(c) {
    editingCustomerId = c ? c.id : null;
    $('customerDialogTitle').textContent = c ? 'Edit customer #' + c.id : 'New customer';
    $('cName').value = c ? c.customerName : '';
    $('cEmail').value = c ? c.email : '';
    $('cPasscode').value = '';
    $('cPasscode').required = !c;
    $('cPasscode').placeholder = c ? 'Leave blank to keep the current passcode' : '';
    $('cType').value = c ? c.licenseType : 'A';
    $('cLicenses').value = c ? c.availableLicenses : 1;
    $('cPhone').value = c ? (c.phoneNumber || '') : '';
    $('cAddress').value = c ? (c.address || '') : '';
    $('cNotes').value = c ? (c.notes || '') : '';
    $('customerError').textContent = '';
    $('customerDialog').showModal();
  }

  async function saveCustomer(event) {
    event.preventDefault();
    const body = {
      customerName: $('cName').value,
      email: $('cEmail').value,
      passcode: $('cPasscode').value || null,
      licenseType: $('cType').value,
      availableLicenses: parseInt($('cLicenses').value, 10) || 0,
      phoneNumber: $('cPhone').value,
      address: $('cAddress').value,
      notes: $('cNotes').value,
    };
    try {
      if (editingCustomerId == null) {
        await api(API + '/customers', { method: 'POST', body: JSON.stringify(body) });
      } else {
        await api(API + '/customers/' + editingCustomerId,
          { method: 'PUT', body: JSON.stringify(body) });
      }
      $('customerDialog').close();
      search();
    } catch (e) {
      $('customerError').textContent = e.message;
    }
  }

  // ------------------------------------------------------------------ wiring

  $('loginForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    $('loginError').textContent = '';
    try {
      const res = await fetch('/api/users/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ emailAddress: $('loginEmail').value,
          password: $('loginPassword').value }),
      });
      const text = await res.text();
      const body = text ? JSON.parse(text) : null;
      if (!res.ok) throw new Error((body && body.message) || 'Sign-in failed.');
      setAuth(body);
      showAdmin();
    } catch (e) {
      $('loginError').textContent = e.message;
    }
  });

  $('searchForm').addEventListener('submit', (event) => { event.preventDefault(); search(); });
  $('newCustomerBtn').addEventListener('click', () => openCustomerDialog(null));
  $('customerForm').addEventListener('submit', saveCustomer);
  $('customerCancel').addEventListener('click', () => $('customerDialog').close());

  if (sessionToken) showAdmin(); else showLogin();
})();
