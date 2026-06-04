package com.djt.jukeanator_engine.domain.songqueue.event;

public sealed interface SongQueueEvent permits SongAddedToQueueEvent, MultipleSongsAddedToQueueEvent, SongQueueChangedEvent {
}
