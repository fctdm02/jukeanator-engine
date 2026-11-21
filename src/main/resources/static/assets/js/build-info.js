// Shows the build time (Detroit time) in the static website's footer, so it is clear which build
// is deployed. Maven filters this file (see the resources section of pom.xml): the token below
// becomes the build's UTC timestamp. Unfiltered (e.g. run from source), nothing is shown.
(function () {
  var built = new Date('@build.timestamp@');
  var target = document.getElementById('build-time');
  if (!target || isNaN(built.getTime())) {
    return;
  }
  target.textContent = ' · Built ' + built.toLocaleString('en-US', {
    timeZone: 'America/Detroit', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: 'numeric', minute: '2-digit', timeZoneName: 'short'
  });
})();
