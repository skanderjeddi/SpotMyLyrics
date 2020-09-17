package com.skanderj.spotmylyrics;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import com.skanderj.ts4j.Task;
import com.skanderj.ts4j.TaskScheduler;
import com.skanderj.ts4j.TaskType;
import com.skanderj.ts4j.TimeValue;

/**
 * @author Skander J.
 */
public final class SML {
	// The current version
	public static final String SPOT_MY_LYRICS_VERSION = "2.3.7";

	// Personal GitHub link
	public static final String GITHUB_URL = "https://github.com/skanderjeddi";

	// The base URL used to fetch lyrics
	public static final String AZLYRICS_URL = "https://www.azlyrics.com/lyrics/%s/%s.html";

	// Enable/disable verbose
	public static final boolean VERBOSE = false;

	/**
	 * This comment is in all AZLyrics' lyrics pages and is located right before the
	 * lyrics section. To extract the lyrics easily with no real HTML parsing, we
	 * just download the page source and look for this comment; the lyrics will
	 * always be the portion of source code delimited by this and the next </div>
	 * tag.
	 */
	public static final String TARGET_WARNING = "<!-- Usage of azlyrics.com content by any third-party lyrics provider is prohibited by our licensing agreement. Sorry about that. -->";

	// Better for memory
	public static final String EMPTY = new StringBuilder().toString(), SPACE = new StringBuilder(" ").toString();

	// Aliases file, can be customized
	public static final File ALIASES_FILE = new File("./aliases.txt");

	// Cache folder for storing and loading local lyrics files
	public static final File CACHE = new File("./cache/");

	// Singleton model
	private static SML instance;

	public static SML getInstance() {
		return SML.instance == null ? SML.instance = new SML() : SML.instance;
	}

	// Aliases for artist names and titles, for specific cases where the regular
	// formatting just doesn't cut it (mainly French songs & artists)
	private final Map<String, String> aliases;

	// Refreshing handler
	private boolean autoRefresh;

	private SML() {
		this.aliases = new HashMap<>();
		this.autoRefresh = true;
	}

	/**
	 * @return the size of the cache (or how many local copies of lyrics)
	 */
	public int cachedItems() {
		return SMLIO.countFiles(SML.CACHE);
	}

	/**
	 * @return the size in bytes of the cache.
	 */
	public long cacheSize() {
		return SMLIO.fileSize(SML.CACHE);
	}

	/**
	 * Formats an artist name for usage in the AZLyrics base URL. Conditions: - All
	 * lowercase - No spaces - No extra whitespace - No special characters
	 *
	 * @param artist the artist's name
	 * @return the formatted artist name
	 */
	public String formatArtist(final String artist) {
		String compliantArtist = new String(artist);
		if (compliantArtist.startsWith("The ")) {
			compliantArtist = compliantArtist.replace("The ", SML.EMPTY);
		}
		// OLD EXPRESSION: [•èÈÊÂÎêâîïÏÇçôÔ\\?\\-,.()!’'&¿\\:°/]
		return compliantArtist.replaceAll("é", "e").replaceAll("É", "E").replaceAll("à", "a").replaceAll("À", "A").replaceAll("[^a-zA-Z0-9]", SML.EMPTY).toLowerCase().replaceAll(SML.SPACE, SML.EMPTY).strip();
	}

	/**
	 * Formats an song track for usage in the AZLyrics base URL. Conditions: - All
	 * lowercase - No spaces - No extra whitespace - No special characters
	 *
	 * @param track the song's track
	 * @return the formatted song track
	 */
	public String formatTitle(final String track) {
		String compliantTitle = new String(track);
		if (compliantTitle.contains("(")) {
			final int start = compliantTitle.indexOf("(");
			compliantTitle = compliantTitle.substring(0, start);
		}
		return compliantTitle.replaceAll("é", "e").replaceAll("É", "E").replaceAll("à", "a").replaceAll("À", "A").replaceAll("[^a-zA-Z0-9]", SML.EMPTY).toLowerCase().replaceAll(SML.SPACE, SML.EMPTY).strip();
	}

	/**
	 * Replaces HTML tags with their programmatical equivalents (or with nothing).
	 *
	 * @param source the HTML source code
	 * @return the formatted source
	 */
	public String formatSource(final String source) {
		return source.replaceAll("<br>", "\n").replaceAll("<br/>", SML.EMPTY).replaceAll("<i>", SML.EMPTY).replaceAll("</i>", SML.EMPTY).replaceAll("&quot;", "'").replaceAll("&amp;", "&");
	}

	/**
	 * Extracts the lyrics from the source code.
	 *
	 * @param source the source code of the page supplied by the function above
	 * @return the lyrics as a single string
	 */
	public String extractLyrics(final String source) {
		final int warningIndex = source.indexOf(SML.TARGET_WARNING), endDivTagIndex = source.indexOf("</div>", warningIndex);
		if (warningIndex == -1) {
			System.err.println("Couldn't find warning index in supplied source, either wrong page source or IP-blocked");
			return null;
		}
		return source.substring(warningIndex + SML.TARGET_WARNING.length(), endDivTagIndex).strip();
	}

	/**
	 * Formats the script's output pair (artist, track)
	 * 
	 * @param answer
	 * @return { formatted artist name, formatted track name, stripped artist name, stripped track name }
	 */
	public String[] formatAnswer(String answer) {
		final String originalAnswer = new StringBuilder(answer).toString();
		// If we have aliases setup
		for (final String alias : this.aliases.keySet()) {
			if (answer.contains(alias)) {
				answer = answer.replace(alias, this.aliases.get(alias)).strip();
			}
		}
		// We don't need the (feat...) part or the (- ...) part or the (with...) part
		if (answer.contains("(feat") || answer.contains("(with") || answer.contains(" - ")) {
			int startIndex = answer.indexOf("(feat");
			if (startIndex != -1) {
				final int endIndex = answer.indexOf(")", startIndex);
				answer = answer.substring(0, startIndex).concat(answer.substring(endIndex + 1, answer.length())).strip();
			}
			startIndex = answer.indexOf("(with");
			if (startIndex != -1) {
				final int endIndex = answer.indexOf(")", startIndex);
				answer = answer.substring(0, startIndex).concat(answer.substring(endIndex + 1, answer.length())).strip();
			}
			startIndex = answer.indexOf(" - ");
			if (startIndex != -1) {
				answer = answer.substring(0, startIndex).strip();
			}
		}
		String artist = originalAnswer.substring(0, originalAnswer.indexOf(",")).strip();
		final String track = originalAnswer.substring(originalAnswer.indexOf(",") + 1, originalAnswer.length()).strip();
		final String strippedArtist = answer.substring(0, answer.indexOf(",")).strip(), strippedTitle = answer.substring(answer.indexOf(",") + 1, answer.length()).strip();
		// Used to fix a glitch
		{
			final int lastCommaIndex = artist.lastIndexOf(",");
			artist = artist.substring(0, lastCommaIndex == -1 ? artist.length() : lastCommaIndex == (artist.length() - 1) ? lastCommaIndex : artist.length());
		}
		if (SML.VERBOSE) {
			System.out.printf("Found %s - %s\n", artist, track);
		}
		return new String[] { artist.replaceAll("[\\('\\)]", SML.EMPTY), track.replaceAll("[\\('\\)]", SML.EMPTY), strippedArtist.replaceAll("[\\('\\)]", SML.EMPTY), strippedTitle.replaceAll("[\\('\\)]", SML.EMPTY) };
	}

	/**
	 * Constructs a file object pointing to the appropiate lyrics file.
	 *
	 * @param artist the artist's name
	 * @param track  the song's track
	 * @return a File object pointing to the lyrics file theoretical location
	 */
	public File getLocalCopy(final String artist, final String track) {
		final String compliantArtist = artist.toLowerCase().replaceAll(SML.SPACE, "_").replaceAll("[\\.\\?\\(']", SML.EMPTY), compliantTitle = track.toLowerCase().replaceAll(SML.SPACE, "_").replaceAll("[\\.\\?\\(']", SML.EMPTY);
		return new File(SML.CACHE, String.format("/%s/%s.txt", compliantArtist, compliantTitle));
	}

	/**
	 * Test function for printing lyrics directly to the terminal.
	 *
	 * @param artist the artist's name
	 * @param track  the song's track
	 */
	public void printLyrics(final String[] spotifyAnswer) {
		final String artist = spotifyAnswer[0], track = spotifyAnswer[1];
		final String compliantArtist = spotifyAnswer[2], compliantTitle = spotifyAnswer[3];
		String lyrics;
		if ((lyrics = SMLIO.readWhole(this.getLocalCopy(artist, track))) == null) {
			String source = SMLIO.fetchSource(SML.AZLYRICS_URL, this.formatArtist(compliantArtist), this.formatTitle(track));
			if (source != null) {
				source = this.formatSource(source);
				lyrics = this.extractLyrics(source);
			}
		}
		if (SMLSYS.OS == SMLSYS.SMLSYSOS.MACOS) {
			System.out.println("\033]0;" + artist + " - " + track + "\007");
			if (lyrics != null) {
				System.out.println((char) (27) + "[2J" + artist + " - " + track + "\n\n" + lyrics + "\n");
				SMLIO.saveToFile(this.getLocalCopy(compliantArtist, compliantTitle), lyrics, false);
			} else {
				System.out.println((char) (27) + "[2J" + artist + " - " + track + "\n\n" + "No lyrics found." + "\n");
			}
		} else {
			if (lyrics != null) {
				System.out.println("\n" + artist + " - " + track + "\n\n" + lyrics + "\n");
				SMLIO.saveToFile(this.getLocalCopy(compliantArtist, compliantTitle), lyrics, false);
			} else {
				System.out.println("\n" + artist + " - " + track + "\n\n" + "No lyrics found." + "\n");
			}
		}
	}

	/**
	 * Very basic vim-style commands handling.
	 *
	 * @param scanner a system.in reader
	 */
	public void handleInput(final Scanner scanner) {
		while (true) {
			final String line = scanner.nextLine();
			switch (line) {
			case ":auto":
			case ":a":
				if (this.autoRefresh) {
					this.autoRefresh = false;
					this.disableAutoRefreshing();
					System.out.println("Auto refreshing disabled!");
				} else {
					this.autoRefresh = true;
					this.enableAutoRefreshing();
					System.out.println("Auto refreshing enabled!");
				}
				break;
			case ":help":
			case ":h":
				System.out.printf("SpotMyLyrics v.%s - By Skander J. (%s)\n", SML.SPOT_MY_LYRICS_VERSION, SML.GITHUB_URL);
				System.out.printf("\t:help (:h)\t\tPrints credits & commands list\n");
				System.out.printf("\t:auto (:a)\t\tToggles auto-refreshing\n");
				System.out.printf("\t:refresh (:r)\t\tFetches the lyrics to the current song\n");
				System.out.printf("\t:aliases (:as)\t\tReloades the aliases file\n");
				System.out.printf("\t:emptycache (:ec)\tDeletes all local copies\n");
				System.out.printf("\t:quit (:q)\t\tQuits the app\n");
				break;
			case ":refresh":
			case ":r":
				final String[] spotifyAnswer = SML.this.formatAnswer(SMLSYS.querySpotify());
				if (spotifyAnswer != null) {
					this.printLyrics(spotifyAnswer);
				}
				break;
			case ":aliases":
			case ":as":
				if (SMLIO.readFileToMap(SML.ALIASES_FILE, this.aliases, ":")) {
					System.out.println("Successfully reloaded the aliases file!");
				}
				break;
			case ":emptycache":
			case ":ec":
				if (this.clearCache()) {
					System.out.println("Successfully cleared the cache!");
				}
				break;
			case ":quit":
			case ":q":
				TaskScheduler.cancelTask("SpotifyQuery", false);
				scanner.close();
				System.exit(0);
				break;
			default:
				System.out.println("Unknown command, use :help or :h for a list of commands");
				break;
			}
		}
	}

	/**
	 * Uses my TS4J scheduling API for fetching the current song every 500ms when
	 * enabled and printing lyrics accordingly.
	 */
	public void enableAutoRefreshing() {
		TaskScheduler.scheduleTask("SpotifyQuery", new Task(new TimeValue(0, TimeUnit.MILLISECONDS), new TimeValue(500, TimeUnit.MILLISECONDS)) {
			private String[] previousSpotifyAnswer;

			@Override
			public void execute() {
				if (this.previousSpotifyAnswer == null) {
					final String[] spotifyAnswer = SML.this.formatAnswer(SMLSYS.querySpotify());
					if (spotifyAnswer != null) {
						SML.this.printLyrics(spotifyAnswer);
						this.previousSpotifyAnswer = spotifyAnswer;
					}
				} else {
					final String[] spotifyAnswer = SML.this.formatAnswer(SMLSYS.querySpotify());
					if (spotifyAnswer != null) {
						if (spotifyAnswer[2].equals(this.previousSpotifyAnswer[2]) && spotifyAnswer[3].equals(this.previousSpotifyAnswer[3])) {
							return;
						} else {
							SML.this.printLyrics(spotifyAnswer);
							this.previousSpotifyAnswer = spotifyAnswer;
						}
					}
				}
			}

			@Override
			public TaskType type() {
				return TaskType.FIXED_RATE;
			}
		});
	}

	/**
	 * Cancels the auto updating task.
	 */
	public void disableAutoRefreshing() {
		TaskScheduler.cancelTask("SpotifyQuery", false);
	}

	/**
	 * Deletes all the files in the cache.
	 */
	public boolean clearCache() {
		return SMLIO.deleteFile(SML.CACHE);
	}

	/**
	 * Starts the app.
	 */
	public void run() {
		System.out.printf("SpotMyLyrics v.%s - By Skander J. (%s)\nThanks for using my software!\n", SML.SPOT_MY_LYRICS_VERSION, SML.GITHUB_URL);
		System.out.printf("(Cache size: %s bytes for %d items)\n", SMLIO.humanReadableByteCountSI(this.cacheSize()), this.cachedItems());
		final Scanner scanner = new Scanner(System.in);
		System.out.print("Press [ENTER] key to continue...");
		scanner.nextLine();
		if (SMLIO.readFileToMap(SML.ALIASES_FILE, this.aliases, ":")) {
			this.enableAutoRefreshing();
			this.handleInput(scanner);
		}
	}

	public static void main(final String[] args) {
		SML.getInstance().run();
	}
}