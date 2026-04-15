package com.djt.jukeanator_engine.domain.songlibrary.service.utils;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class GenreNormalizer {

	private static final Set<String> ID3_GENRES = Set.of("Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk",
			"Grunge", "Hip-Hop", "Jazz", "Metal", "New Age", "Oldies", "Other", "Pop", "R&B", "Rap", "Reggae", "Rock",
			"Techno", "Industrial", "Alternative", "Ska", "Death Metal", "Pranks", "Soundtrack", "Euro-Techno",
			"Ambient", "Trip-Hop", "Vocal", "Jazz+Funk", "Fusion", "Trance", "Classical", "Instrumental", "Acid",
			"House", "Game", "Sound Clip", "Gospel", "Noise", "AlternRock", "Bass", "Soul", "Punk", "Space",
			"Meditative", "Instrumental Pop", "Instrumental Rock", "Ethnic", "Gothic", "Darkwave", "Techno-Industrial",
			"Electronic", "Pop-Folk", "Eurodance", "Dream", "Southern Rock", "Comedy", "Cult", "Gangsta", "Top 40",
			"Christian Rap", "Pop/Funk", "Jungle", "Native American", "Cabaret", "New Wave", "Psychedelic", "Rave",
			"Showtunes", "Trailer", "Lo-Fi", "Tribal", "Acid Punk", "Acid Jazz", "Polka", "Retro", "Musical",
			"Rock & Roll", "Hard Rock");

	// Common mappings from MusicBrainz → ID3
	private static final Map<String, String> NORMALIZATION_MAP = Map.ofEntries(Map.entry("hip hop", "Hip-Hop"),
			Map.entry("hip-hop", "Hip-Hop"), Map.entry("rap", "Rap"), Map.entry("rnb", "R&B"), Map.entry("r&b", "R&B"),
			Map.entry("rhythm and blues", "R&B"), Map.entry("metal", "Metal"), Map.entry("heavy metal", "Metal"),
			Map.entry("death metal", "Death Metal"), Map.entry("black metal", "Metal"),
			Map.entry("thrash metal", "Metal"), Map.entry("rock", "Rock"), Map.entry("hard rock", "Hard Rock"),
			Map.entry("classic rock", "Classic Rock"), Map.entry("alternative rock", "Alternative"),
			Map.entry("alt rock", "Alternative"), Map.entry("grunge", "Grunge"), Map.entry("punk", "Punk"),
			Map.entry("pop", "Pop"), Map.entry("synthpop", "Pop"), Map.entry("dance", "Dance"),
			Map.entry("electronic", "Electronic"), Map.entry("techno", "Techno"), Map.entry("house", "House"),
			Map.entry("trance", "Trance"), Map.entry("jazz", "Jazz"), Map.entry("blues", "Blues"),
			Map.entry("country", "Country"), Map.entry("reggae", "Reggae"), Map.entry("ska", "Ska"),
			Map.entry("classical", "Classical"), Map.entry("soundtrack", "Soundtrack"), Map.entry("ambient", "Ambient"),
			Map.entry("industrial", "Industrial"), Map.entry("gospel", "Gospel"), Map.entry("funk", "Funk"),
			Map.entry("soul", "Soul"));

	public static String normalize(String rawGenre) {
		
		if (rawGenre != null && !rawGenre.isBlank()) {
			
			String cleaned = rawGenre.toLowerCase().replaceAll("[^a-z0-9&+\\-\\s]", "") // remove junk
					.trim();

			// Direct mapping
			if (NORMALIZATION_MAP.containsKey(cleaned)) {
				return NORMALIZATION_MAP.get(cleaned);
			}

			// Try title case match against ID3
			String titleCase = Arrays.stream(cleaned.split("\\s+"))
					.map(word -> word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1))
					.collect(Collectors.joining(" "));

			if (ID3_GENRES.contains(titleCase)) {
				return titleCase;
			}
		}

		// Fallback bucket
		return "Other";
	}
}