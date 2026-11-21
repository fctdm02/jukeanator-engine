// Runtime configuration for the Web/Mobile UI, loaded before app.js.
//
// apiBaseUrl: absolute URL of the JukeANator server the UI talks to, with no trailing slash
// (e.g. "https://www.jukeanator.com"). Leave it empty when the server itself serves this UI
// (the browser case); requests then stay same-origin. The native mobile app (Capacitor) build
// replaces this file with one that sets apiBaseUrl to the master's URL, because a bundled copy
// of the UI is loaded from the device (capacitor://localhost or https://localhost), not from
// the server.
window.JUKEANATOR_CONFIG = {
  apiBaseUrl: ''
};
