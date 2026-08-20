package com.djt.jukeanator_engine.config;

import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import com.djt.jukeanator_engine.domain.backgroundmusic.config.BackgroundMusicProperties;
import com.djt.jukeanator_engine.domain.backgroundmusic.repository.BackgroundMusicRepository;
import com.djt.jukeanator_engine.domain.backgroundmusic.repository.BackgroundMusicRepositoryFileSystemImpl;
import com.djt.jukeanator_engine.domain.backgroundmusic.repository.SmartBackgroundMusicRepository;
import com.djt.jukeanator_engine.domain.backgroundmusic.repository.SmartBackgroundMusicRepositoryFileSystemImpl;
import com.djt.jukeanator_engine.domain.backgroundmusic.service.BackgroundMusicService;
import com.djt.jukeanator_engine.domain.backgroundmusic.service.BackgroundMusicServiceImpl;
import com.djt.jukeanator_engine.domain.backgroundmusic.service.NoOpBackgroundMusicService;
import com.djt.jukeanator_engine.domain.common.model.utils.ObjectMappers;
import com.djt.jukeanator_engine.domain.common.security.JwtUtil;
import com.djt.jukeanator_engine.domain.common.utils.OperatingSystemDetector;
import com.djt.jukeanator_engine.domain.common.utils.OperatingSystemDetector.OSType;
import com.djt.jukeanator_engine.domain.location.service.SlaveCommandGateway;
import com.djt.jukeanator_engine.domain.songlibrary.config.SongLibraryProperties;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryObjectPersistor;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryRepository;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryRepositoryFileSystemImpl;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryRepositoryJpaImpl;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryServiceImpl;
import com.djt.jukeanator_engine.domain.songlibrary.service.utils.CoverArtDownloader;
import com.djt.jukeanator_engine.domain.songlibrary.service.utils.DiscogsClientWrapper;
import com.djt.jukeanator_engine.domain.songlibrary.service.utils.JAudioTaggerClient;
import com.djt.jukeanator_engine.domain.songlibrary.service.utils.MusicBrainzClientWrapper;
import com.djt.jukeanator_engine.domain.songlibrary.service.utils.SongScanner;
import com.djt.jukeanator_engine.domain.songplayer.audio.JukeboxAudioCoordinator;
import com.djt.jukeanator_engine.domain.songplayer.audio.LineInService;
import com.djt.jukeanator_engine.domain.songplayer.audio.MasterVolumeService;
import com.djt.jukeanator_engine.domain.songplayer.audio.lineinput.LineInServiceImpl;
import com.djt.jukeanator_engine.domain.songplayer.audio.linux.LinuxMasterVolumeService;
import com.djt.jukeanator_engine.domain.songplayer.audio.mac.MacMasterVolumeService;
import com.djt.jukeanator_engine.domain.songplayer.audio.windows.WindowsMasterVolumeService;
import com.djt.jukeanator_engine.domain.songplayer.config.SongPlayerProperties;
import com.djt.jukeanator_engine.domain.songplayer.service.SongPlayerService;
import com.djt.jukeanator_engine.domain.songplayer.service.SongPlayerServiceImpl;
import com.djt.jukeanator_engine.domain.songplayer.service.SongPlayerServiceMasterImpl;
import com.djt.jukeanator_engine.domain.songqueue.config.SongQueueProperties;
import com.djt.jukeanator_engine.domain.songqueue.repository.SongQueueRepository;
import com.djt.jukeanator_engine.domain.songqueue.repository.SongQueueRepositoryFileSystemImpl;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueServiceImpl;
import com.djt.jukeanator_engine.domain.user.repository.UserRepository;
import com.djt.jukeanator_engine.domain.user.repository.UserRepositoryFileSystemImpl;
import com.djt.jukeanator_engine.domain.user.repository.UserRepositoryJpaImpl;
import com.djt.jukeanator_engine.domain.user.service.UserService;
import com.djt.jukeanator_engine.domain.user.service.UserServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;

@Configuration
public class AppConfig {

  @Bean
  public ObjectMapper objectMapper() {
    return ObjectMappers.create();
  }

  // Defined here rather than SecurityConfig — SecurityConfig depends (via the optional
  // LocationApiKeyAuthenticationFilter -> LocationService chain) on PasswordEncoder, and a
  // @Configuration class's own @Bean methods can't be invoked until that class itself has been
  // fully constructed, so keeping it there created a circular reference.
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
  
  @Bean
  public DiscogsClientWrapper discogsClientWrapper(SongLibraryProperties songLibraryProperties) {
    
    return new DiscogsClientWrapper(
        songLibraryProperties.getDiscogs().getConsumerKey(),
        songLibraryProperties.getDiscogs().getConsumerSecret());
  }

  @Bean
  public MusicBrainzClientWrapper musicBrainzClientWrapper() {
    return new MusicBrainzClientWrapper();
  }

  @Bean
  public JAudioTaggerClient jAudioTaggerClient() {
    return new JAudioTaggerClient();
  }

  @Bean
  public CoverArtDownloader coverArtDownloader() {
    return new CoverArtDownloader();
  }

  @Bean
  public SongLibraryObjectPersistor songLibraryObjectPersistor() {
    return new SongLibraryObjectPersistor();
  }

  // ── Song library repository ───────────────────────────────────────────────

  @Bean
  @ConditionalOnProperty(name = "song-library.repository-type", havingValue = "filesystem",
      matchIfMissing = true)
  public SongLibraryRepository songLibraryRepositoryFileSystemImpl(AppProperties appProperties) {

    return new SongLibraryRepositoryFileSystemImpl(appProperties.getDataDir());
  }

  @Bean
  @ConditionalOnProperty(name = "song-library.repository-type", havingValue = "jpa")
  public SongLibraryRepository songLibraryRepositoryJpaImpl(EntityManagerFactory entityManagerFactory,
      PlatformTransactionManager transactionManager) {

    return new SongLibraryRepositoryJpaImpl(entityManagerFactory, transactionManager);
  }

  // ── Song scanner ──────────────────────────────────────────────────────────

  @Bean
  public SongScanner songScanner(
      SongLibraryProperties songLibraryProperties,
      DiscogsClientWrapper discogsClientWrapper,
      MusicBrainzClientWrapper musicBrainzClientWrapper,
      JAudioTaggerClient jAudioTaggerClient,
      CoverArtDownloader coverArtDownloader) {
    
    return new SongScanner(
        discogsClientWrapper,
        musicBrainzClientWrapper,
        jAudioTaggerClient,
        coverArtDownloader,
        songLibraryProperties.isRequiresMetadata(),
        songLibraryProperties.isUseGenre(),
        songLibraryProperties.isUseTopFolderForGenre(),
        songLibraryProperties.getAcceptedSongFileExtensions());
  }

  // ── Song queue repository ─────────────────────────────────────────────────

  @Bean
  @ConditionalOnProperty(name = "song-queue.repository-type", havingValue = "filesystem",
      matchIfMissing = true)
  public SongQueueRepository songQueueRepositoryFileSystemImpl(AppProperties appProperties,
      SongLibraryService songLibraryService) {

    return new SongQueueRepositoryFileSystemImpl(appProperties.getDataDir(), songLibraryService);
  }

  // ── User repository ───────────────────────────────────────────────────────

  @Bean
  @ConditionalOnProperty(name = "user.repository-type", havingValue = "filesystem",
      matchIfMissing = true)
  public UserRepository userRepositoryFileSystemImpl(AppProperties appProperties) {

    return new UserRepositoryFileSystemImpl(appProperties.getDataDir());
  }

  @Bean
  @ConditionalOnProperty(name = "user.repository-type", havingValue = "jpa")
  public UserRepository userRepositoryJpaImpl(EntityManagerFactory entityManagerFactory,
      PlatformTransactionManager transactionManager) {

    return new UserRepositoryJpaImpl(entityManagerFactory, transactionManager);
  }

  // ── Background music repositories ───────────────────────────────────────

  @Bean
  public BackgroundMusicRepository backgroundMusicRepository(AppProperties appProperties) {

    return new BackgroundMusicRepositoryFileSystemImpl(appProperties.getDataDir());
  }

  @Bean
  public SmartBackgroundMusicRepository smartBackgroundMusicRepository(
      AppProperties appProperties) {

    return new SmartBackgroundMusicRepositoryFileSystemImpl(appProperties.getDataDir());
  }

  // ── Services ──────────────────────────────────────────────────────────────
  @Bean
  @Primary
  public SongLibraryService songLibraryService(
      AppProperties appProperties,
      SongLibraryProperties songLibraryProperties,
      SongLibraryRepository repository,
      SongScanner songScanner,
      ApplicationEventPublisher eventPublisher) {
    
    return new SongLibraryServiceImpl(
        appProperties,
        repository,
        songScanner,
        songLibraryProperties.getSearchResultSize(),
        eventPublisher);
  }

  @Bean
  @Primary
  @Conditional(NotMasterModeCondition.class)
  public BackgroundMusicService backgroundMusicService(
      AppProperties appProperties,
      BackgroundMusicProperties backgroundMusicProperties,
      SongLibraryService songLibraryService,
      BackgroundMusicRepository backgroundMusicRepository,
      SmartBackgroundMusicRepository smartBackgroundMusicRepository) {

    return new BackgroundMusicServiceImpl(
        appProperties.getDataDir(),
        backgroundMusicProperties,
        songLibraryService,
        backgroundMusicRepository,
        smartBackgroundMusicRepository);
  }

  // Master-only stand-in for BackgroundMusicService, mutually exclusive with the real
  // NotMasterModeCondition-gated bean above -- exactly one BackgroundMusicService bean always
  // exists, so songQueueService below has no ambiguity to resolve.
  @Bean
  @ConditionalOnProperty(name = "app.mode", havingValue = "master")
  public BackgroundMusicService noOpBackgroundMusicService() {
    return new NoOpBackgroundMusicService();
  }

  // Unconditional: master now also constructs this bean, so its locationId-aware methods can
  // branch to forward a request over SlaveCommandGateway (present only on master; absent
  // elsewhere, hence Optional) instead of the NotMasterModeCondition-gated split this used to be.
  @Bean
  public SongQueueService songQueueService(
      SongQueueProperties songQueueProperties,
      SongLibraryService songLibraryService,
      BackgroundMusicService backgroundMusicService,
      SongQueueRepository songQueueRepository,
      ApplicationEventPublisher eventPublisher,
      Optional<SlaveCommandGateway> slaveCommandGateway) {

    return new SongQueueServiceImpl(
        songQueueProperties,
        songLibraryService,
        backgroundMusicService,
        songQueueRepository,
        eventPublisher,
        slaveCommandGateway);
  }

  // Real, hardware-backed implementation -- never constructed on master (its constructor spins up
  // a VLC/Winamp process and a continuously-running watchdog thread, which must never happen on a
  // headless host with no audio hardware/software installed). Bean name pinned to "songPlayerService"
  // on both this and the master-only bean below so SongPlayerController's @Qualifier resolves
  // either one; the mutually-exclusive conditionals guarantee only one is ever actually registered.
  @Bean(name = "songPlayerService")
  @Primary
  @Conditional(NotMasterModeCondition.class)
  public SongPlayerService songPlayerServiceImpl(
      SongPlayerProperties songPlayerProperties,
      SongQueueService songQueueService,
      MasterVolumeService masterVolumeService,
      LineInService lineInService,
      ApplicationEventPublisher eventPublisher) {

    return new SongPlayerServiceImpl(
        songPlayerProperties,
        songQueueService,
        masterVolumeService,
        lineInService,
        eventPublisher);
  }

  @Bean(name = "songPlayerService")
  @ConditionalOnProperty(name = "app.mode", havingValue = "master")
  public SongPlayerService songPlayerServiceMasterImpl(SlaveCommandGateway slaveCommandGateway) {
    return new SongPlayerServiceMasterImpl(slaveCommandGateway);
  }

  @Bean
  @Primary
  public UserService userService(
      AppProperties appProperties,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtUtil jwtUtil,
      org.springframework.context.ApplicationEventPublisher eventPublisher,
      SongLibraryService songLibraryService) {

    return new UserServiceImpl(
        userRepository,
        passwordEncoder,
        jwtUtil,
        eventPublisher,
        songLibraryService,
        appProperties.isSlave());
  }
 
  @Bean
  @Primary
  @Conditional(NotMasterModeCondition.class)
  public MasterVolumeService masterVolumeService() {
    
    OSType osType = OperatingSystemDetector.getOperatingSystem();
    if (osType == OSType.WINDOWS) {
      return new WindowsMasterVolumeService();
    }
    
    if (osType == OSType.MACOS) {
      return new MacMasterVolumeService();
    } 
    
    return new LinuxMasterVolumeService();
  }

  @Bean
  @Primary
  @Conditional(NotMasterModeCondition.class)
  public LineInService lineInService(SongPlayerProperties songPlayerProperties) {
    
    return new LineInServiceImpl(
        songPlayerProperties.isEnableLineInOnSilence(),
        songPlayerProperties.getPreferredMixerName(), 
        songPlayerProperties.getLineInVolume());
  }
  
  @Bean
  @Primary
  @Conditional(NotMasterModeCondition.class)
  public JukeboxAudioCoordinator jukeboxAudioCoordinator(
      LineInService lineInService,
      SongQueueService songQueueService,
      SongPlayerService songPlayerService) {

    return new JukeboxAudioCoordinator(lineInService, songQueueService, songPlayerService);
  }
}