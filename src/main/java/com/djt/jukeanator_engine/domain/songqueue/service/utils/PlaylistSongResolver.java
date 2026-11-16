package com.djt.jukeanator_engine.domain.songqueue.service.utils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.ArtistFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;

/**
 * Resolves one line of a playlist file to a song in the library. A line is either:
 *
 * <ul>
 * <li>a song file path, matched exactly, or -- when it was written on a machine whose music root
 * differs (e.g. {@code D:\Music} vs {@code C:\Music}) -- by its path relative to the library root,
 * the same way {@code CDStats.TXT} play counts are restored; or</li>
 * <li>free text, such as a line copied from a "top 100" chart ({@code 10 POUR SOME SUGAR ON ME DEF
 * LEPPARD Mercury}). No layout is assumed: every word on the line is searched, and the song whose
 * own title and artist words are best covered by the line wins (see {@link WordSearchIndex}).</li>
 * </ul>
 *
 * Built once per playlist load; the indexes cost one pass over the library.
 */
public final class PlaylistSongResolver {

  /** How a line was resolved. */
  public enum MatchType {
    EXACT_PATH, RELOCATED_PATH, WORD_SEARCH
  }

  /** A resolved line. */
  public record Resolution(SongFileEntity song, MatchType matchType) {
  }

  private static final Pattern ABSOLUTE_PATH = Pattern.compile("^(?:[A-Za-z]:[\\\\/]|\\\\\\\\|/).*");
  private static final Pattern AUDIO_FILE =
      Pattern.compile("(?i).*[\\\\/].*\\.(?:mp3|wma|wav|flac|m4a|aac|ogg|mp4)$");
  private static final Pattern FILE_EXTENSION = Pattern.compile("\\.[A-Za-z0-9]{1,5}$");

  private final RootFolderEntity root;
  private final Map<String, SongFileEntity> songsByNormalizedPath = new HashMap<>();
  private final Map<String, SongFileEntity> songsByRelativePath = new HashMap<>();
  private final WordSearchIndex<SongFileEntity> wordSearchIndex;

  public PlaylistSongResolver(RootFolderEntity root) {

    this.root = root;

    String normalizedRoot = RootFolderEntity.normalizeStatsPath(root.getRootPath());
    List<SearchCandidate<SongFileEntity>> candidates = new ArrayList<>();
    for (SongFileEntity song : root.getSongs()) {

      String normalizedSongPath = RootFolderEntity.normalizeStatsPath(song.getNaturalIdentity());
      songsByNormalizedPath.putIfAbsent(normalizedSongPath, song);

      String relativePath = normalizedSongPath;
      if (!normalizedRoot.isEmpty() && normalizedSongPath.startsWith(normalizedRoot + "/")) {
        relativePath = normalizedSongPath.substring(normalizedRoot.length() + 1);
      }
      songsByRelativePath.putIfAbsent(relativePath, song);

      candidates.add(new SearchCandidate<>(song, songTitle(song), song.getArtistName(),
          artistFolderName(song), song.getNumPlays() == null ? 0 : song.getNumPlays()));
    }
    this.wordSearchIndex = new WordSearchIndex<>(candidates);
  }

  /**
   * Resolves one (trimmed, non-comment) playlist line.
   *
   * @return the song and how it was found, or empty when nothing in the library matches well enough
   */
  public Optional<Resolution> resolve(String line) {

    if (line == null || line.isBlank()) {
      return Optional.empty();
    }
    String trimmed = line.trim();

    if (looksLikePath(trimmed)) {

      try {
        return Optional.of(new Resolution(root.getSongByPath(trimmed), MatchType.EXACT_PATH));
      } catch (EntityDoesNotExistException e) {
        // fall through to the lenient matches
      }

      String normalizedPath = RootFolderEntity.normalizeStatsPath(trimmed);
      SongFileEntity song = songsByNormalizedPath.get(normalizedPath);
      if (song == null) {
        song = RootFolderEntity.findSongByRelativeSuffix(normalizedPath, songsByRelativePath);
      }
      if (song != null) {
        return Optional.of(new Resolution(song, MatchType.RELOCATED_PATH));
      }

      // The file is not in this library under any root; the same song may still be here on
      // another album, so search on the words of the path (folders and file name).
      trimmed = FILE_EXTENSION.matcher(trimmed).replaceFirst("");
    }

    return wordSearchIndex.find(trimmed)
        .map(song -> new Resolution(song, MatchType.WORD_SEARCH));
  }

  static boolean looksLikePath(String line) {
    return ABSOLUTE_PATH.matcher(line).matches() || AUDIO_FILE.matcher(line).matches();
  }

  private static String songTitle(SongFileEntity song) {

    String songName = song.getSongName();
    if (songName != null && !songName.isBlank()) {
      return songName;
    }
    String name = song.getName();
    return name == null ? null : FILE_EXTENSION.matcher(name).replaceFirst("");
  }

  private static String artistFolderName(SongFileEntity song) {

    AlbumFolderEntity album = song.getAlbum();
    if (album == null) {
      return null;
    }
    ArtistFolderEntity artist = album.getParentArtist();
    return artist == null ? null : artist.getName();
  }

  // ───────────────────────────────────────────────────────────────────────────
  // WORD SEARCH
  // ───────────────────────────────────────────────────────────────────────────

  /**
   * One searchable song: its title, its artist (from the song itself) and the artist folder it is
   * filed under, plus its play count for breaking ties.
   */
  public record SearchCandidate<T>(T item, String title, String artist, String artistFolder,
      int numPlays) {
  }

  /**
   * Finds the song whose words are best covered by a free-text line.
   *
   * <p>
   * The line is split into words (lowercased, punctuation and the stop words the/a/an/and/feat
   * dropped) and kept as a multiset, so each occurrence can be used only once. For a candidate:
   *
   * <ul>
   * <li><b>title coverage</b> is the fraction of its distinct title words found on the line; the
   * matched words are then consumed;</li>
   * <li><b>artist coverage</b> is the fraction of its artist words found among the words that are
   * left, so a line such as {@code FEEL LIKE MAKIN' LOVE BAD COMPANY} cannot match the song "Bad
   * Company" by using the artist's words for its title. When the artist is not fully covered, it is
   * also compared with all punctuation and spaces removed, so {@code AC/DC} matches {@code ACDC}.
   * The better of the song artist and the artist folder is used.</li>
   * </ul>
   *
   * A candidate qualifies with title coverage of at least {@value #MIN_TITLE_COVERAGE} and artist
   * coverage of at least {@value #MIN_ARTIST_COVERAGE}, or with title coverage of at least
   * {@value #MIN_TITLE_COVERAGE_STRONG_ARTIST} when the artist coverage is at least
   * {@value #STRONG_ARTIST_COVERAGE} (e.g. "I Want To Be Sedated" for "I Wanna Be Sedated" by the
   * Ramones). Among qualifying candidates the one with the most title words matched wins, then the
   * highest combined score (0.7 title + 0.3 artist), then the most plays -- so a song on both a
   * "best of" and its original album resolves to whichever copy is played more. Words on the line
   * that match nothing (chart ranks, record labels, "EDITED", "LIVE") do not count against a match.
   *
   * <p>
   * The thresholds were calibrated against a TouchTunes top-100 chart and a library of about 8,600
   * songs: every song on the chart that is in the library matched, with no false matches.
   */
  public static final class WordSearchIndex<T> {

    static final double MIN_TITLE_COVERAGE = 0.80;
    static final double MIN_ARTIST_COVERAGE = 0.50;
    static final double MIN_TITLE_COVERAGE_STRONG_ARTIST = 0.75;
    static final double STRONG_ARTIST_COVERAGE = 0.90;
    private static final double TITLE_WEIGHT = 0.7;
    private static final double ARTIST_WEIGHT = 0.3;
    private static final double EPSILON = 1e-9;
    private static final int MIN_SQUASHED_ARTIST_LENGTH = 3;

    private record Prepared<T>(SearchCandidate<T> candidate, List<String> titleWords,
        List<String> artistWords, String squashedArtist, List<String> folderWords,
        String squashedFolder) {
    }

    private record Score(int titleWordsMatched, double titleCoverage, double artistCoverage) {

      double combined() {
        return TITLE_WEIGHT * titleCoverage + ARTIST_WEIGHT * artistCoverage;
      }

      boolean qualifies() {
        return (titleCoverage >= MIN_TITLE_COVERAGE - EPSILON
            && artistCoverage >= MIN_ARTIST_COVERAGE - EPSILON)
            || (titleCoverage >= MIN_TITLE_COVERAGE_STRONG_ARTIST - EPSILON
                && artistCoverage >= STRONG_ARTIST_COVERAGE - EPSILON);
      }
    }

    private final List<Prepared<T>> prepared = new ArrayList<>();

    public WordSearchIndex(Collection<SearchCandidate<T>> candidates) {

      for (SearchCandidate<T> candidate : candidates) {

        List<String> titleWords = distinct(tokenize(stripTitleDecorations(candidate.title())));
        if (titleWords.isEmpty()) {
          continue;
        }
        prepared.add(new Prepared<>(candidate, titleWords,
            distinct(tokenize(stripTitleDecorations(candidate.artist()))),
            squash(candidate.artist()), distinct(tokenize(candidate.artistFolder())),
            squash(candidate.artistFolder())));
      }
    }

    /** Returns the best-matching item for the line, or empty when none qualifies. */
    public Optional<T> find(String line) {

      List<String> lineWords = tokenize(line);
      if (lineWords.isEmpty()) {
        return Optional.empty();
      }
      Set<String> lineWordSet = new LinkedHashSet<>(lineWords);

      Prepared<T> best = null;
      Score bestScore = null;
      for (Prepared<T> entry : prepared) {

        // Cheap pre-check before any copying: enough title words must be on the line at all.
        int titleHits = 0;
        for (String word : entry.titleWords()) {
          if (lineWordSet.contains(word)) {
            titleHits++;
          }
        }
        if ((double) titleHits / entry.titleWords().size()
            < MIN_TITLE_COVERAGE_STRONG_ARTIST - EPSILON) {
          continue;
        }

        Score score = score(lineWords, entry);
        if (!score.qualifies()) {
          continue;
        }
        if (best == null || isBetter(score, entry, bestScore, best)) {
          best = entry;
          bestScore = score;
        }
      }
      return best == null ? Optional.empty() : Optional.of(best.candidate().item());
    }

    private static <T> boolean isBetter(Score score, Prepared<T> entry, Score bestScore,
        Prepared<T> best) {

      if (score.titleWordsMatched() != bestScore.titleWordsMatched()) {
        return score.titleWordsMatched() > bestScore.titleWordsMatched();
      }
      double diff = score.combined() - bestScore.combined();
      if (Math.abs(diff) > EPSILON) {
        return diff > 0;
      }
      return entry.candidate().numPlays() > best.candidate().numPlays();
    }

    private static Score score(List<String> lineWords, Prepared<?> entry) {

      // Title first: each matched title word consumes one occurrence on the line.
      List<String> remaining = new ArrayList<>(lineWords);
      int titleMatched = 0;
      for (String word : entry.titleWords()) {
        if (remaining.remove(word)) {
          titleMatched++;
        }
      }
      double titleCoverage = (double) titleMatched / entry.titleWords().size();

      double artistCoverage =
          Math.max(artistCoverage(remaining, entry.artistWords(), entry.squashedArtist()),
              artistCoverage(remaining, entry.folderWords(), entry.squashedFolder()));

      return new Score(titleMatched, titleCoverage, artistCoverage);
    }

    private static double artistCoverage(List<String> remaining, List<String> artistWords,
        String squashedArtist) {

      if (artistWords.isEmpty()) {
        return 0;
      }
      List<String> available = new ArrayList<>(remaining);
      int matched = 0;
      for (String word : artistWords) {
        if (available.remove(word)) {
          matched++;
        }
      }
      double coverage = (double) matched / artistWords.size();
      if (coverage < 1.0 && squashedArtist.length() >= MIN_SQUASHED_ARTIST_LENGTH
          && String.join("", remaining).contains(squashedArtist)) {
        coverage = 1.0;
      }
      return coverage;
    }
  }

  // ───────────────────────────────────────────────────────────────────────────
  // TEXT HELPERS
  // ───────────────────────────────────────────────────────────────────────────

  private static final Set<String> STOP_WORDS =
      Set.of("the", "a", "an", "and", "feat", "ft", "featuring");

  // "(Edited)", "[Clean]", "(Live)", "(Remastered 2009)", "(feat. Sheryl Crow)" ...
  private static final Pattern TITLE_DECORATION = Pattern.compile(
      "(?i)[(\\[](?:edited|clean|live|explicit|remaster[a-z]*(?:\\s+\\d{2,4})?|[^)\\]]*\\b(?:feat|ft)\\b[^)\\]]*)[)\\]]");
  private static final Pattern TRAILING_FEATURING = Pattern.compile("(?i)\\s(?:feat|ft)\\.?\\s.*$");
  private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
  private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

  /** Removes decorations that a library title carries but a playlist line need not. */
  static String stripTitleDecorations(String text) {

    if (text == null) {
      return "";
    }
    String stripped = TITLE_DECORATION.matcher(text).replaceAll(" ");
    return TRAILING_FEATURING.matcher(stripped).replaceFirst("");
  }

  /**
   * Splits text into lowercase words: accents are dropped, {@code &} becomes "and", apostrophes are
   * removed ("Makin'" is "makin"), any other punctuation separates words, and stop words are
   * dropped. Order and repeats are kept.
   */
  static List<String> tokenize(String text) {

    if (text == null || text.isBlank()) {
      return List.of();
    }
    String normalized = DIACRITICS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD))
        .replaceAll("").toLowerCase(Locale.ROOT).replace("&", " and ")
        .replace("'", "").replace("\u2019", "");
    normalized = NON_ALPHANUMERIC.matcher(normalized).replaceAll(" ").trim();
    if (normalized.isEmpty()) {
      return List.of();
    }
    List<String> words = new ArrayList<>();
    for (String word : normalized.split(" ")) {
      if (!word.isEmpty() && !STOP_WORDS.contains(word)) {
        words.add(word);
      }
    }
    return words;
  }

  /** The text with everything but letters and digits removed: "AC/DC" and "AC DC" are "acdc". */
  static String squash(String text) {
    return String.join("", tokenize(text == null ? null : stripTitleDecorations(text)));
  }

  private static List<String> distinct(List<String> words) {
    return new ArrayList<>(new LinkedHashSet<>(words));
  }
}
