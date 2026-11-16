package com.djt.jukeanator_engine.domain.songqueue.service.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.djt.jukeanator_engine.domain.songqueue.service.utils.PlaylistSongResolver.SearchCandidate;
import com.djt.jukeanator_engine.domain.songqueue.service.utils.PlaylistSongResolver.WordSearchIndex;

/**
 * Unit tests for the free-text word search used to resolve "raw" playlist lines (e.g. a chart copied
 * from the internet) to library songs. Items are plain strings standing in for songs.
 */
class PlaylistSongResolverTest {

  private static final WordSearchIndex<String> INDEX = new WordSearchIndex<>(List.of(
      new SearchCandidate<>("leppard-best-of", "Pour Some Sugar On Me", "Def Leppard",
          "Def Leppard", 6),
      new SearchCandidate<>("leppard-hysteria", "Pour Some Sugar On Me", "Def Leppard",
          "Def Leppard", 6472),
      new SearchCandidate<>("acdc-thunderstruck-compilation", "Thunderstruck", "AC DC",
          "Compilations", 1631),
      new SearchCandidate<>("acdc-thunderstruck", "Thunderstruck", "ACDC", "Acdc", 6842),
      new SearchCandidate<>("acdc-back-in-black", "Back In Black", "ACDC", "Acdc", 4594),
      new SearchCandidate<>("badco-bad-company", "Bad Company", "Bad Company", "Bad Company",
          6771),
      new SearchCandidate<>("badco-feel-like", "Feel Like Makin' Love", "Bad Company",
          "Bad Company", 2795),
      new SearchCandidate<>("ramones-sedated", "I Wanna Be Sedated", "Ramones", "Ramones", 7781),
      new SearchCandidate<>("kidrock-picture", "Picture (feat Sheryl Crow)", "Kid Rock",
          "Kid Rock", 1966),
      new SearchCandidate<>("stones-beast", "Beast Of Burden", "The Rolling Stones",
          "Rolling Stones", 5537),
      new SearchCandidate<>("collective-soul-simple", "Simple", "Collective Soul",
          "Collective Soul", 100),
      new SearchCandidate<>("matchbox-3am", "3 AM", "Matchbox 20", "Matchbox 20", 3882)));

  private static Optional<String> find(String line) {
    return INDEX.find(line);
  }

  @Test
  void lineWithRankAndLabelMatchesOnTitleAndArtistWords() {
    assertEquals(Optional.of("leppard-hysteria"),
        find("10 \tPOUR SOME SUGAR ON ME \tDEF LEPPARD \tMercury"));
  }

  @Test
  void wordOrderAndSeparatorsDoNotMatter() {
    assertEquals(Optional.of("leppard-hysteria"), find("Def Leppard - Pour Some Sugar On Me"));
    assertEquals(Optional.of("leppard-hysteria"), find("#10. pour some sugar on me, def leppard"));
    assertEquals(Optional.of("stones-beast"), find("BEAST OF BURDEN  ROLLING STONES, THE"));
  }

  @Test
  void mostPlayedCopyWinsWhenMatchesAreEqual() {
    assertEquals(Optional.of("acdc-thunderstruck"), find("61 THUNDERSTRUCK AC/DC ATCO"));
  }

  @Test
  void artistPunctuationIsIgnored() {
    assertEquals(Optional.of("acdc-back-in-black"), find("BACK IN BLACK \tAC/DC \tATCO"));
  }

  @Test
  void titleWordsCannotAlsoBeUsedAsArtistWords() {
    assertEquals(Optional.of("badco-feel-like"),
        find("75 FEEL LIKE MAKIN' LOVE \tBAD COMPANY \tElektra"));
    assertEquals(Optional.of("badco-bad-company"), find("53 BAD COMPANY \tBAD COMPANY \tElektra"));
  }

  @Test
  void strongArtistMatchAllowsASlightlyDifferentTitle() {
    assertEquals(Optional.of("ramones-sedated"), find("94 I WANT TO BE SEDATED RAMONES"));
  }

  @Test
  void decorationsOnEitherSideAreIgnored() {
    assertEquals(Optional.of("kidrock-picture"),
        find("1 PICTURE (FEAT. SHERYL CROW) KID ROCK (EDITED) Atlantic"));
    assertEquals(Optional.of("matchbox-3am"), find("52 3 AM MATCHBOX TWENTY Atlantic"));
  }

  @Test
  void belowThresholdIsRejected() {
    // "Simple" by Collective Soul shares the title word but not the artist.
    assertFalse(find("12 SIMPLE MAN LYNYRD SKYNYRD MCA").isPresent());
    // Song not in the library at all.
    assertFalse(find("23 ONE METALLICA Elektra").isPresent());
    assertFalse(find("").isPresent());
  }

  @Test
  void tokenizeLowercasesDropsPunctuationAndStopWords() {
    assertEquals(List.of("sweet", "child", "o", "mine", "guns", "n", "roses"),
        PlaylistSongResolver.tokenize("SWEET CHILD O' MINE  GUNS N' ROSES"));
    assertEquals(List.of("makin", "love"), PlaylistSongResolver.tokenize("Makin' & Love"));
    assertEquals("acdc", PlaylistSongResolver.squash("AC/DC"));
  }

  @Test
  void pathDetection() {
    assertTrue(PlaylistSongResolver.looksLikePath("C:\\Music\\Rock\\Audioslave\\Cochise.mp3"));
    assertTrue(PlaylistSongResolver.looksLikePath("/home/user/Music/Rock/song.flac"));
    assertTrue(PlaylistSongResolver.looksLikePath("Rock\\Audioslave\\Cochise.mp3"));
    assertFalse(PlaylistSongResolver.looksLikePath("10 POUR SOME SUGAR ON ME DEF LEPPARD"));
    assertFalse(PlaylistSongResolver.looksLikePath("AC/DC - Back In Black"));
  }
}
