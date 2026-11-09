package com.djt.jukeanator_engine.ui;

import java.util.List;

import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.djt.jukeanator_engine.domain.license.model.LicenseStatus;
import com.djt.jukeanator_engine.domain.license.service.LicenseService;
import org.springframework.stereotype.Component;

import com.djt.jukeanator_engine.config.AppProperties;
import com.djt.jukeanator_engine.domain.email.config.EmailProperties;
import com.djt.jukeanator_engine.domain.financialledger.service.FinancialLedgerService;
import com.djt.jukeanator_engine.domain.location.service.LocationService;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songplayer.service.SongPlayerService;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.domain.user.service.UserService;
import com.djt.jukeanator_engine.domain.useractivity.service.UserActivityService;
import com.djt.jukeanator_engine.ui.components.JukeANatorFrame;
import com.djt.jukeanator_engine.ui.config.JukeANatorUserInterfaceProperties;
import com.djt.jukeanator_engine.ui.event.JukeANatorEventListener;
import com.djt.jukeanator_engine.ui.security.SwingSecurityUtil;

@Component
@ConditionalOnProperty(name = "app.ui-enabled", havingValue = "true")
public class JukeANatorUserInterfaceApplication {

  private static final Logger log = LoggerFactory.getLogger(JukeANatorUserInterfaceApplication.class);

  private final JukeANatorUserInterfaceProperties jukeANatorUserInterfaceProperties;
  private final AppProperties appProperties;
  private final SongLibraryService songLibraryService;
  private final SongQueueService songQueueService;
  private final SongPlayerService songPlayerService;
  private final UserService userService;
  private final LocationService locationService;
  private final FinancialLedgerService financialLedgerService;
  private final UserActivityService userActivityService;
  private final JukeANatorEventListener jukeANatorEventListener;
  private final EmailProperties emailProperties;

  private JukeANatorFrame frame;

  // Absent on instances without licensing (master never runs the UI); payment inputs then stay on.
  private LicenseService licenseService;

  @Autowired(required = false)
  public void setLicenseService(LicenseService licenseService) {
    this.licenseService = licenseService;
  }

  public JukeANatorUserInterfaceApplication(
      JukeANatorUserInterfaceProperties jukeANatorUserInterfaceProperties,
      AppProperties appProperties,
      SongLibraryService songLibraryService,
      SongQueueService songQueueService,
      SongPlayerService songPlayerService,
      UserService userService,
      LocationService locationService,
      FinancialLedgerService financialLedgerService,
      UserActivityService userActivityService,
      JukeANatorEventListener jukeANatorEventListener,
      EmailProperties emailProperties) {

    this.jukeANatorUserInterfaceProperties = jukeANatorUserInterfaceProperties;
    this.appProperties = appProperties;
    this.songLibraryService = songLibraryService;
    this.songQueueService = songQueueService;
    this.songPlayerService = songPlayerService;
    this.userService = userService;
    this.locationService = locationService;
    this.financialLedgerService = financialLedgerService;
    this.userActivityService = userActivityService;
    this.jukeANatorEventListener = jukeANatorEventListener;
    this.emailProperties = emailProperties;
  }

  public void launch() {

    this.frame = new JukeANatorFrame(
        jukeANatorUserInterfaceProperties,
        songLibraryService,
        songQueueService,
        songPlayerService,
        userService,
        locationService,
        financialLedgerService,
        userActivityService,
        appProperties.getDataDir(),
        emailProperties.getFinancialLedgerRecipients());

    this.frame.setLicenseService(licenseService);
    this.jukeANatorEventListener.setFrame(frame);
    this.jukeANatorEventListener.setSongLibraryService(songLibraryService);
    this.jukeANatorEventListener.setSongQueueService(songQueueService);
    this.jukeANatorEventListener.setSongPlayerService(songPlayerService);

    // Show the frame immediately with loading placeholders so the UI is responsive
    // at once. All service calls (which may hit the database) are dispatched to a
    // background thread and pushed back to the EDT only when they complete.
    this.frame.showFullscreen();
    this.frame.setVisible(true);

    // Raise the frame before promptForInitialLibraryScan(), whose modal dialog blocks this method.
    // The dialog raises itself when it opens, and bringToFront() is called again below once the
    // data load completes.
    this.frame.bringToFront();

    if (Boolean.TRUE.equals(songLibraryService.isLibraryLoadFailedAtStartup())) {
      log.info("No song library could be loaded at startup — prompting for initial library scan");
      frame.promptForInitialLibraryScan();
    }

    log.info("JukeANator UI launched: fetching library data in background");

    SwingSecurityUtil.runAsync(this::loadInitialData).exceptionally(t -> {
      // CompletableFuture would otherwise swallow this silently -- and with it the bringToFront()
      // calls below, leaving the UI possibly hidden behind the console with nothing in the log.
      log.error("JukeANator UI: initial data load failed", t);
      // Start playback anyway, so a failed data load does not leave the jukebox silent.
      SwingUtilities.invokeLater(() -> {
        songPlayerService.startPlayback();
        showRegistrationIfNeeded();
      });
      return null;
    });
  }

  /**
   * Legacy RegistrationDialog: shown at startup when unlicensed, when the subscription needs
   * registration details, or when it has a renewal warning / has expired. Called on the EDT once
   * the initial data load has finished, so the full-screen frame is laid out and raised before the
   * card is shown. The card stays up until the operator clicks Try (or Later).
   */
  private void showRegistrationIfNeeded() {
    if (licenseService == null) {
      return;
    }
    LicenseStatus status = licenseService.getStatus();
    if (status.registrationNeeded()
        || (status.licensedTier().isSubscription() && status.message() != null)) {
      log.info("License: {} -- showing the registration screen", status.describe());
      // Closing it goes on to ask for any missing customer / location name.
      frame.showRegistrationCard();
    } else {
      frame.showMissingLicenseDetails();
    }
  }

  private void loadInitialData() {

    Integer          locationId  = songLibraryService.getOwnLocationId();
    List<AlbumDto>   albums      = songLibraryService.getAlbums(locationId);
    int              artistCount = songLibraryService.getArtists(locationId).size();
    var              genres      = songLibraryService.getGenres(locationId);
    var              playing     = songPlayerService.getNowPlayingSong(locationId);
    var              queue       = songQueueService.getQueuedSongs(locationId);

    SwingUtilities.invokeLater(() -> {
      frame.setAlbums(albums, artistCount);
      frame.setGenres(genres);
      frame.setNowPlaying(playing);
      frame.setQueue(queue);

      // Grab native OS foreground and keyboard focus now that the UI is fully initialized
      // with real data, so the UI (or, on first use, the scan dialog) is not left behind the
      // command prompt or Winamp, and hardware inputs (e.g. the bill acceptor's 'a'
      // keystroke) work immediately without the operator having to click the screen first.
      frame.bringToFront();

      // Once more after a short delay, in case another window (e.g. Winamp, or the console
      // finishing its startup output) took the foreground after the data load completed.
      javax.swing.Timer reRaiseTimer = new javax.swing.Timer(3_000, e -> frame.bringToFront());
      reRaiseTimer.setRepeats(false);
      reRaiseTimer.start();

      log.info("JukeANator UI: data load complete");

      // Only now start playing songs, so the media player (e.g. Winamp) is not competing with UI
      // startup for CPU on underpowered machines, where it could stop responding to IPC.
      songPlayerService.startPlayback();

      showRegistrationIfNeeded();
    });
  }
}