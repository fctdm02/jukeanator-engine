package com.djt.jukeanator_engine.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryObjectPersistor;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryRepository;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryRepositoryFileSystemImpl;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryRepositoryPostgresImpl;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryServiceImpl;
import com.djt.jukeanator_engine.domain.songlibrary.service.config.SongLibraryProperties;
import com.djt.jukeanator_engine.domain.songlibrary.service.utils.CoverArtDownloader;
import com.djt.jukeanator_engine.domain.songlibrary.service.utils.DiscogsClientWrapper;
import com.djt.jukeanator_engine.domain.songlibrary.service.utils.JAudioTaggerClient;
import com.djt.jukeanator_engine.domain.songlibrary.service.utils.MusicBrainzClientWrapper;
import com.djt.jukeanator_engine.domain.songlibrary.service.utils.SongScanner;
import com.djt.jukeanator_engine.domain.songqueue.repository.SongQueueObjectPersistor;
import com.djt.jukeanator_engine.domain.songqueue.repository.SongQueueRepository;
import com.djt.jukeanator_engine.domain.songqueue.repository.SongQueueRepositoryFileSystemImpl;
import com.djt.jukeanator_engine.domain.songqueue.repository.SongQueueRepositoryPostgresImpl;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueServiceImpl;

@Configuration
public class ApplicationConfig {

  @Bean
  public DiscogsClientWrapper discogsClientWrapper(SongLibraryProperties props) {
    return new DiscogsClientWrapper(props.getDiscogs().getConsumerKey(),
        props.getDiscogs().getConsumerSecret());
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
  @ConditionalOnProperty(name = "song-library.repository-type", havingValue = "filesystem",
      matchIfMissing = true // default
  )
  public SongLibraryRepository songLibraryRepositoryFileSystemImpl(SongLibraryProperties props) {
    return new SongLibraryRepositoryFileSystemImpl(props.getRootPath() // basePath = rootPath
    );
  }

  @Bean
  @ConditionalOnProperty(name = "song-library.repository-type", havingValue = "postgres")
  public SongLibraryRepository songLibraryRepositoryPostgresImpl(SongLibraryProperties props) {
    return new SongLibraryRepositoryPostgresImpl();
  }

  @Bean
  public SongScanner songScanner(SongLibraryProperties props,
      DiscogsClientWrapper discogsClientWrapper, MusicBrainzClientWrapper musicBrainzClientWrapper,
      JAudioTaggerClient jAudioTaggerClient, CoverArtDownloader coverArtDownloader) {
    return new SongScanner(discogsClientWrapper, musicBrainzClientWrapper, jAudioTaggerClient,
        coverArtDownloader, props.isRequiresMetadata(), props.isUseGenre(),
        props.isUseTopFolderForGenre());
  }

  @Bean
  public SongLibraryService songLibraryService(SongLibraryProperties props,
      SongLibraryRepository repository, SongScanner songScanner) {
    return new SongLibraryServiceImpl(props.getRootPath(), repository, songScanner);
  }

  @Bean
  @ConditionalOnProperty(name = "song-queue.repository-type", havingValue = "filesystem",
      matchIfMissing = true // default
  )
  public SongQueueRepository songQueueRepositoryFileSystemImpl(SongLibraryProperties props) {
    return new SongQueueRepositoryFileSystemImpl(props.getRootPath() // basePath = rootPath
    );
  }
  
  @Bean
  @ConditionalOnProperty(name = "song-queue.repository-type", havingValue = "postgres")
  public SongQueueRepository songQueueRepositoryPostgresImpl(SongLibraryProperties props) {
    return new SongQueueRepositoryPostgresImpl();
  }

  @Bean
  public SongQueueService songQueueService(SongLibraryService songLibraryService,
      SongQueueRepository repository) {
    return new SongQueueServiceImpl(songLibraryService, repository);
  }
}
