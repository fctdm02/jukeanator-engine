package com.djt.jukeanator_engine.ui.components;

/**
 * Common paging/letter-navigation contract implemented by both {@link AlbumGridPanel} and
 * {@link LegacyAlbumGridPanel}, so {@link HomePanel} can carry the selected letter/page over when
 * swapping between them (sort-order toggle, Legacy-view toggle) without needing to know which
 * concrete grid type is currently showing.
 */
interface AlbumGridView {

  /** Returns the currently highlighted letter button's key (e.g. "#" or "A"-"Z"). */
  String getSelectedLetter();

  /** How many pages into the selected letter's bucket the visible page is (0 = bucket's first page). */
  int getPageOffsetWithinLetter();

  /** Selects {@code letter} and jumps {@code pageOffset} pages into that bucket. */
  void selectLetterAtPage(String letter, int pageOffset);
}
