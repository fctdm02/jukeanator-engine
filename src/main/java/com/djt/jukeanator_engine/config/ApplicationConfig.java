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
import com.djt.jukeanator_engine.domain.user.config.UserProperties;
import com.djt.jukeanator_engine.domain.user.repository.UserRepository;
import com.djt.jukeanator_engine.domain.user.repository.UserRepositoryFileSystemImpl;
import com.djt.jukeanator_engine.domain.user.repository.UserRepositoryPostgresImpl;
import com.djt.jukeanator_engine.domain.user.repository.UserRootObjectPersistor;
import com.djt.jukeanator_engine.domain.user.service.UserService;
import com.djt.jukeanator_engine.domain.user.service.UserServiceImpl;

@Configuration
public class ApplicationConfig {

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
  
  @Bean
  @ConditionalOnProperty(name = "song-library.repository-type", havingValue = "filesystem",
      matchIfMissing = true // default
  )
  public SongLibraryRepository songLibraryRepositoryFileSystemImpl(SongLibraryProperties songLibraryProperties) {
    
    return new SongLibraryRepositoryFileSystemImpl(songLibraryProperties.getRootPath() // basePath = rootPath
    );
  }

  @Bean
  @ConditionalOnProperty(name = "song-library.repository-type", havingValue = "postgres")
  public SongLibraryRepository songLibraryRepositoryPostgresImpl(SongLibraryProperties songLibraryProperties) {
    
    return new SongLibraryRepositoryPostgresImpl();
  }

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

  @Bean
  @ConditionalOnProperty(name = "song-queue.repository-type", havingValue = "filesystem",
      matchIfMissing = true // default
  )
  public SongQueueRepository songQueueRepositoryFileSystemImpl(SongQueueProperties songQueueProperties) {
    
    return new SongQueueRepositoryFileSystemImpl(songQueueProperties.getRootPath() // basePath = rootPath
    );
  }
  
  @Bean
  @ConditionalOnProperty(name = "song-queue.repository-type", havingValue = "postgres")
  public SongQueueRepository songQueueRepositoryPostgresImpl(SongQueueProperties songQueueProperties) {
    
    return new SongQueueRepositoryPostgresImpl();
  }
  
  @Bean
  @ConditionalOnProperty(name = "user.repository-type", havingValue = "filesystem",
      matchIfMissing = true // default
  )
  public UserRepository userRepositoryFileSystemImpl(UserProperties userProperties) {
    
    return new UserRepositoryFileSystemImpl(userProperties.getRootPath() // basePath = rootPath
    );
  }
  
  @Bean
  @ConditionalOnProperty(name = "user.repository-type", havingValue = "postgres")
  public UserRepository userRepositoryPostgresImpl(UserProperties userProperties) {
    
    return new UserRepositoryPostgresImpl();
  }
  
  @Bean
  @Primary
  public SongLibraryService songLibraryService(
      SongLibraryProperties songLibraryProperties,
      SongLibraryRepository repository, 
      SongScanner songScanner,
      ApplicationEventPublisher eventPublisher) {
    
    return new SongLibraryServiceImpl(
        songLibraryProperties.getRootPath(), 
        repository, 
        songScanner,
        songLibraryProperties.getSearchResultSize(),
        eventPublisher);
  }
  
  @Bean
  @Primary
  public SongQueueService songQueueService(
      SongQueueProperties songQueueProperties,
      SongLibraryRepository songLibraryRepository,
      SongQueueRepository songQueueRepository,
      ApplicationEventPublisher eventPublisher) {
    
    return new SongQueueServiceImpl(
        songQueueProperties, 
        songLibraryRepository, 
        songQueueRepository,
        eventPublisher);
  }
  
  @Bean
  @Primary
  public SongPlayerService songPlayerService(
      SongPlayerProperties songPlayerProperties,
      SongLibraryProperties songLibraryProperties,
      SongLibraryRepository songLibraryRepository,
      SongQueueService songQueueService,
      ApplicationEventPublisher eventPublisher) {
    
    return new SongPlayerServiceImpl(
        songPlayerProperties.getPlayerType(),
        songQueueService,
        eventPublisher);
  }
  
  @Bean
  @Primary
  public UserService userService(
      UserProperties userProperties,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder, 
      JwtUtil jwtUtil) {
    
    return new UserServiceImpl(
        userProperties.getRootPath(), 
        userRepository,
        passwordEncoder, 
        jwtUtil);
  }  
}
