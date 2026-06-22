package com.djt.jukeanator_engine.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.djt.jukeanator_engine.domain.common.security.JwtUtil;
import com.djt.jukeanator_engine.domain.songlibrary.config.SongLibraryProperties;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryObjectPersistor;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryRepository;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryRepositoryFileSystemImpl;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryRepositoryPostgresImpl;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryServiceImpl;
import com.djt.jukeanator_engine.domain.songlibrary.service.utils.CoverArtDownloader;
import com.djt.jukeanator_engine.domain.songlibrary.service.utils.DiscogsClientWrapper;
import com.djt.jukeanator_engine.domain.songlibrary.service.utils.JAudioTaggerClient;
import com.djt.jukeanator_engine.domain.songlibrary.service.utils.MusicBrainzClientWrapper;
import com.djt.jukeanator_engine.domain.songlibrary.service.utils.SongScanner;
import com.djt.jukeanator_engine.domain.songplayer.config.SongPlayerProperties;
import com.djt.jukeanator_engine.domain.songplayer.service.SongPlayerService;
import com.djt.jukeanator_engine.domain.songplayer.service.SongPlayerServiceImpl;
import com.djt.jukeanator_engine.domain.songqueue.config.SongQueueProperties;
import com.djt.jukeanator_engine.domain.songqueue.repository.SongQueueObjectPersistor;
import com.djt.jukeanator_engine.domain.songqueue.repository.SongQueueRepository;
import com.djt.jukeanator_engine.domain.songqueue.repository.SongQueueRepositoryFileSystemImpl;
import com.djt.jukeanator_engine.domain.songqueue.repository.SongQueueRepositoryPostgresImpl;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueServiceImpl;
import com.djt.jukeanator_engine.domain.user.repository.UserRepository;
import com.djt.jukeanator_engine.domain.user.repository.UserRepositoryFileSystemImpl;
import com.djt.jukeanator_engine.domain.user.repository.UserRepositoryPostgresImpl;
import com.djt.jukeanator_engine.domain.user.repository.UserRootObjectPersistor;
import com.djt.jukeanator_engine.domain.user.service.UserService;
import com.djt.jukeanator_engine.domain.user.service.UserServiceImpl;

@Configuration
public class ApplicationConfig {

  // ── Utility / helper beans ────────────────────────────────────────────────

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

  @Bean
  public SongQueueObjectPersistor songQueueObjectPersistor() {
    return new SongQueueObjectPersistor();
  }

  @Bean
  public UserRootObjectPersistor userRootObjectPersistor() {
    return new UserRootObjectPersistor();
  }

  // ── Song library repository ───────────────────────────────────────────────

  @Bean
  @ConditionalOnProperty(name = "song-library.repository-type", havingValue = "filesystem",
      matchIfMissing = true)
  public SongLibraryRepository songLibraryRepositoryFileSystemImpl(AppProperties appProperties) {
    
    return new SongLibraryRepositoryFileSystemImpl(appProperties.getEffectiveRootPath());
  }

  @Bean
  @ConditionalOnProperty(name = "song-library.repository-type", havingValue = "postgres")
  public SongLibraryRepository songLibraryRepositoryPostgresImpl() {
    
    return new SongLibraryRepositoryPostgresImpl();
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
  public SongQueueRepository songQueueRepositoryFileSystemImpl(AppProperties appProperties) {
    
    return new SongQueueRepositoryFileSystemImpl(appProperties.getEffectiveRootPath());
  }

  @Bean
  @ConditionalOnProperty(name = "song-queue.repository-type", havingValue = "postgres")
  public SongQueueRepository songQueueRepositoryPostgresImpl() {
    
    return new SongQueueRepositoryPostgresImpl();
  }

  // ── User repository ───────────────────────────────────────────────────────

  @Bean
  @ConditionalOnProperty(name = "user.repository-type", havingValue = "filesystem",
      matchIfMissing = true)
  public UserRepository userRepositoryFileSystemImpl(AppProperties appProperties) {
    
    return new UserRepositoryFileSystemImpl(appProperties.getEffectiveRootPath());
  }

  @Bean
  @ConditionalOnProperty(name = "user.repository-type", havingValue = "postgres")
  public UserRepository userRepositoryPostgresImpl() {
    
    return new UserRepositoryPostgresImpl();
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
        appProperties.getEffectiveRootPath(),
        repository,
        songScanner,
        songLibraryProperties.getSearchResultSize(),
        eventPublisher);
  }

  @Bean
  @Primary
  public SongQueueService songQueueService(
      AppProperties appProperties,
      SongQueueProperties songQueueProperties,
      SongLibraryRepository songLibraryRepository,
      SongQueueRepository songQueueRepository,
      ApplicationEventPublisher eventPublisher) {
    
    return new SongQueueServiceImpl(
        appProperties.getEffectiveRootPath(),
        songQueueProperties,
        songLibraryRepository,
        songQueueRepository,
        eventPublisher);
  }

  @Bean
  @Primary
  public SongPlayerService songPlayerService(
      SongPlayerProperties songPlayerProperties,
      SongQueueService songQueueService,
      ApplicationEventPublisher eventPublisher) {
    
    return new SongPlayerServiceImpl(
        songPlayerProperties,
        songQueueService,
        eventPublisher);
  }

  @Bean
  @Primary
  public UserService userService(
      AppProperties appProperties,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtUtil jwtUtil) {
    
    return new UserServiceImpl(
        appProperties.getEffectiveRootPath(),
        userRepository,
        passwordEncoder,
        jwtUtil);
  }
}