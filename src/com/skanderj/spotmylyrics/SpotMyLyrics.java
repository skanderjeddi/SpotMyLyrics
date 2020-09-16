package com.skanderj.spotmylyrics;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
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
public final class SpotMyLyrics {
	// The current version
	public static final String SPOT_MY_LYRICS_VERSION = "2.3.7";

	// Personal GitHub link
	public static final String GITHUB_URL = "https://github.com/skanderjeddi";

	// Enable/disable verbose
	public static final boolean VERBOSE = false;

	// The base URL used to fetch lyrics
	public static final String AZLYRICS_URL = "https://www.azlyrics.com/lyrics/%s/%s.html";

	// Get the system OS
	public static final String OS = System.getProperty("os.name").toLowerCase();

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

	// AppleScript code used to fetch the currently playing Spotify song (only on
	// macOS)
	public static final String APPLESCRIPT_CODE = "getCurrentlyPlayingTrack()\n" + "on getCurrentlyPlayingTrack()\n" + "tell application \"Spotify\"\n" + "set isPlaying to player state as string\n" + "set currentArtist to artist of current track as string\n" + "set currentTrack to name of current track as string\n" + "return {currentArtist, currentTrack}\n" + "end tell\n" + "end getCurrentlyPlayingTrack";

	// Aliases file, can be customized
	public static final String ALIASES_FILE = "./aliases.txt";

	// Cache folder for storing and loading local lyrics files
	public static final File CACHE = new File("./cache/");

	// Singleton model
	private static SpotMyLyrics instance;

	public static SpotMyLyrics getInstance() {
		return SpotMyLyrics.instance == null ? SpotMyLyrics.instance = new SpotMyLyrics() : SpotMyLyrics.instance;
	}

	// Aliases for artist names and titles, for specific cases where the regular
	// formatting just doesn't cut it (mainly French songs & artists)
	private final Map<String, String> aliases;

	// Refreshing handler
	private boolean autoRefresh;

	private SpotMyLyrics() {
		this.aliases = new HashMap<>();
		this.autoRefresh = true;
	}

	/**
	 * @return the size of the cache (or how many local copies of lyrics)
	 */
	public int cachedItems() {
		return this.countFiles(SpotMyLyrics.CACHE);
	}

	/**
	 * Recursively counts the number of files in a given directory.
	 *
	 * @return the number of files if @param file is a directory, -1 otherwise
	 */
	private int countFiles(final File file) {
		if (!file.isDirectory()) {
			return -1;
		}
		int counter = 0;
		for (final File child : file.listFiles()) {
			counter += child.isDirectory() ? this.countFiles(child) : 1;
		}
		return counter;
	}

	/**
	 * @return the size in bytes of the cache.
	 */
	public long cacheSize() {
		return this.fileSize(SpotMyLyrics.CACHE);
	}

	/**
	 * @return the size in bytes of the @param file.
	 */
	private long fileSize(final File file) {
		long length = 0;
		if (file.isDirectory()) {
			for (final File subFile : file.listFiles()) {
				length += subFile.isDirectory() ? this.fileSize(subFile) : subFile.length();
			}
		} else {
			return file.length();
		}
		return length;
	}

	public String humanReadableByteCountSI(long bytes) {
		if ((-1000 < bytes) && (bytes < 1000)) {
			return bytes + " B";
		}
		final CharacterIterator characterIterator = new StringCharacterIterator("kMGTPE");
		while ((bytes <= -999_950) || (bytes >= 999_950)) {
			bytes /= 1000;
			characterIterator.next();
		}
		return String.format("%.1f %cB", bytes / 1000.0, characterIterator.current());
	}

	/**
	 * Reads the aliases from the aliases file.
	 *
	 * @return true if success, false otherwise
	 */
	public boolean readAliases(final String aliasesFilePath) {
		if (SpotMyLyrics.VERBOSE) {
			System.out.printf("Reading aliases from %s...", aliasesFilePath);
		}
		this.aliases.clear();
		try {
			final BufferedReader reader = new BufferedReader(new FileReader(new File(aliasesFilePath)));
			String currentLine = null;
			while ((currentLine = reader.readLine()) != null) {
				if (currentLine.startsWith("#")) {
					continue;
				}
				final String key = currentLine.split(":")[0], value = currentLine.split(":")[1];
				this.aliases.put(key, value);
			}
			reader.close();
		} catch (final IOException exception) {
			System.err.println("An exception occurred while loading aliases: " + exception.getMessage());
			return false;
		}
		if (SpotMyLyrics.VERBOSE) {
			System.out.printf("\tSuccess!\n");
		}
		return true;
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
			compliantArtist = compliantArtist.replace("The ", SpotMyLyrics.EMPTY);
		}
		// OLD EXPRESSION: [•èÈÊÂÎêâîïÏÇçôÔ\\?\\-,.()!’'&¿\\:°/]
		return compliantArtist.replaceAll("é", "e").replaceAll("É", "E").replaceAll("à", "a").replaceAll("À", "A").replaceAll("[^a-zA-Z0-9]", SpotMyLyrics.EMPTY).toLowerCase().replaceAll(SpotMyLyrics.SPACE, SpotMyLyrics.EMPTY).strip();
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
		return compliantTitle.replaceAll("é", "e").replaceAll("É", "E").replaceAll("à", "a").replaceAll("À", "A").replaceAll("[^a-zA-Z0-9]", SpotMyLyrics.EMPTY).toLowerCase().replaceAll(SpotMyLyrics.SPACE, SpotMyLyrics.EMPTY).strip();
	}

	/**
	 * Fetches the source code of the lyrics page.
	 *
	 * @param artist the artist's name
	 * @param track  the song's track
	 * @return the source code, or null if an exception occurred
	 */
	public String fetchSource(final String artist, final String track) {
		if (SpotMyLyrics.VERBOSE) {
			System.out.printf("Fetching page source for %s - %s...", artist, track);
		}
		final String compliantArtist = this.formatArtist(artist), compliantTitle = this.formatTitle(track);
		URL azlyricsPageURL = null;
		try {
			azlyricsPageURL = new URL(String.format(SpotMyLyrics.AZLYRICS_URL, compliantArtist, compliantTitle));
		} catch (final MalformedURLException malformedURLException) {
			System.err.println("An exception occurred while building the URL: " + malformedURLException.getMessage());
			return null;
		}
		InputStream urlInputStream = null;
		try {
			urlInputStream = azlyricsPageURL.openStream();
		} catch (final IOException ioException) {
			System.err.println("An exception occurred while opening the input stream from the URL: " + ioException.getMessage());
			return null;
		}
		final BufferedReader reader = new BufferedReader(new InputStreamReader(urlInputStream));
		final StringBuilder source = new StringBuilder();
		String currentLine = null;
		try {
			while ((currentLine = reader.readLine()) != null) {
				source.append(currentLine);
			}
		} catch (final IOException ioException) {
			System.err.println("An exception occurred while reading from the URL's input stream: " + ioException.getMessage());
			return null;
		} finally {
			try {
				urlInputStream.close();
				reader.close();
			} catch (final IOException ioException) {
				// Nothing to do here
			}
		}
		if (SpotMyLyrics.VERBOSE) {
			System.out.printf("\tSuccess!\n");
		}
		return source.toString().strip();
	}

	/**
	 * Replaces HTML tags with their programmatical equivalents (or with nothing).
	 *
	 * @param source the HTML source code
	 * @return the formatted source
	 */
	public String formatSource(final String source) {
		return source.replaceAll("<br>", "\n").replaceAll("<br/>", SpotMyLyrics.EMPTY).replaceAll("<i>", SpotMyLyrics.EMPTY).replaceAll("</i>", SpotMyLyrics.EMPTY).replaceAll("&quot;", "'").replaceAll("&amp;", "&");
	}

	/**
	 * Extracts the lyrics from the source code.
	 *
	 * @param source the source code of the page supplied by the function above
	 * @return the lyrics as a single string
	 */
	public String extractLyrics(final String source) {
		final int warningIndex = source.indexOf(SpotMyLyrics.TARGET_WARNING), endDivTagIndex = source.indexOf("</div>", warningIndex);
		if (warningIndex == -1) {
			System.err.println("Couldn't find warning index in supplied source, either wrong page source or IP-blocked");
			return null;
		}
		return source.substring(warningIndex + SpotMyLyrics.TARGET_WARNING.length(), endDivTagIndex).strip();
	}

	public String[] querySpotify() {
		if (SpotMyLyrics.OS.contains("win")) {
			return this.querySpotiftWINDOWS();
		} else if (SpotMyLyrics.OS.contains("mac os")) {
			return this.querySpotifyMACOS();
		} else if (SpotMyLyrics.OS.contains("linux")) {
			return this.querySpotifyLINUX();
		} else {
			return null;
		}
	}

	public String readProcessOutput(final String[] args) throws Exception {
		String output = new String();
		final ProcessBuilder processBuilder = new ProcessBuilder(args);
		// Mandatory
		processBuilder.redirectErrorStream(true);
		final Process process = processBuilder.start();
		final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
		String line;
		while ((line = reader.readLine()) != null) {
			output = line;
		}
		process.waitFor();
		reader.close();
		return output;
	}

	/**
	 * Gets the currently playing song on Spotify. Relies on a Python script
	 * included in /scripts/.
	 *
	 * @return (artist, track, strippedArtist, strippedTitle)
	 */
	public String[] querySpotiftWINDOWS() {
		if (SpotMyLyrics.VERBOSE) {
			System.out.printf("Querying Spotify for the current song...\n");
		}
		String answer = SpotMyLyrics.EMPTY;
		try {
			final String[] pythonScriptArgs = { "python", "./scripts/getspotifyinfowin.py" };
			answer = this.readProcessOutput(pythonScriptArgs);
		} catch (final Exception exception) {
			System.err.println("An error occurred while querying Spotify for the current song: " + exception.getMessage());
			return null;
		}
		if (answer.equals("PAUSED") || answer.equals("NOT RUNNING")) {
			System.err.println("Spotify is either paused or not running, can't identify the current track!");
			return null;
		}
		return this.formatAnswer(answer);
	}

	/**
	 * Gets the currently playing song on Spotify. Relies on AppleScript.
	 *
	 * @return (artist, track, strippedArtist, strippedTitle)
	 */
	public String[] querySpotifyMACOS() {
		if (SpotMyLyrics.VERBOSE) {
			System.out.printf("Querying Spotify for the current song...\n");
		}
		String answer = SpotMyLyrics.EMPTY;
		try {
			final String[] appleScriptArgs = { "osascript", "-e", SpotMyLyrics.APPLESCRIPT_CODE };
			answer = this.readProcessOutput(appleScriptArgs);
		} catch (final Exception exception) {
			System.err.println("An error occurred while querying Spotify for the current song: " + exception.getMessage());
			return null;
		}
		return this.formatAnswer(answer);
	}

	/**
	 * Gets the currently playing song on Spotify. Relies on a Python script
	 * included in /scripts/.
	 *
	 * @return (artist, track, strippedArtist, strippedTitle)
	 */
	public String[] querySpotifyLINUX() {
		if (SpotMyLyrics.VERBOSE) {
			System.out.printf("Querying Spotify for the current song...\n");
		}
		String answer = SpotMyLyrics.EMPTY;
		try {
			final String[] pythonScriptArgs = { "python", "./scripts/getspotifyinfolinux.py" };
			answer = this.readProcessOutput(pythonScriptArgs);
		} catch (final Exception exception) {
			System.err.println("An error occurred while querying Spotify for the current song: " + exception.getMessage());
			return null;
		}
		if (answer.equals("PAUSED") || answer.equals("NOT RUNNING")) {
			System.err.println("Spotify is either paused or not running, can't identify the current track!");
			return null;
		}
		return this.formatAnswer(answer);
	}

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
		if (SpotMyLyrics.VERBOSE) {
			System.out.printf("Found %s - %s\n", artist, track);
		}
		return new String[] { artist.replaceAll("[\\('\\)]", SpotMyLyrics.EMPTY), track.replaceAll("[\\('\\)]", SpotMyLyrics.EMPTY), strippedArtist.replaceAll("[\\('\\)]", SpotMyLyrics.EMPTY), strippedTitle.replaceAll("[\\('\\)]", SpotMyLyrics.EMPTY) };
	}

	/**
	 * Constructs a file object pointing to the appropiate lyrics file.
	 *
	 * @param artist the artist's name
	 * @param track  the song's track
	 * @return a File object pointing to the lyrics file theoretical location
	 */
	public File getLocalCopy(final String artist, final String track) {
		final String compliantArtist = artist.toLowerCase().replaceAll(SpotMyLyrics.SPACE, "_").replaceAll("[\\.\\?\\(']", SpotMyLyrics.EMPTY), compliantTitle = track.toLowerCase().replaceAll(SpotMyLyrics.SPACE, "_").replaceAll("[\\.\\?\\(']", SpotMyLyrics.EMPTY);
		return new File(SpotMyLyrics.CACHE, String.format("/%s/%s.txt", compliantArtist, compliantTitle));
	}

	/**
	 * Reads the lyrics from a local copy if one exists.
	 *
	 * @param artist the artist's name
	 * @param track  the song's track
	 * @return the lyrics from a local copy
	 */
	public String readFromLocalCopy(final String artist, final String track) {
		if (SpotMyLyrics.VERBOSE) {
			System.out.printf("Reading local copy for %s - %s...", artist, track);
		}
		final File lyricsFile = this.getLocalCopy(artist, track);
		if (lyricsFile.exists()) {
			final StringBuilder lyrics = new StringBuilder();
			try {
				final BufferedReader reader = new BufferedReader(new FileReader(lyricsFile));
				String currentLine = null;
				while ((currentLine = reader.readLine()) != null) {
					lyrics.append(currentLine + "\n");
				}
				reader.close();
				if (SpotMyLyrics.VERBOSE) {
					System.out.printf("\tSuccess!\n");
				}
				return lyrics.toString().strip();
			} catch (final IOException readingException) {
				System.err.println("An exception occurred while reading from local copy: " + readingException.getMessage());
				return null;
			}
		} else {
			if (SpotMyLyrics.VERBOSE) {
				System.out.printf("\tLocal copy doesn't exist for %s - %s\n", artist, track);
			}
			return null;
		}
	}

	/**
	 * Stores the lyrics in a local copy to avoid online fetching in the future.
	 *
	 * @param artist the artist's name
	 * @param track  the song's track
	 * @param lyrics the lyrics to save
	 * @return true if success, false otherwise
	 */
	public boolean saveToLocalCopy(final String artist, final String track, final String lyrics, final boolean override) {
		if (SpotMyLyrics.VERBOSE) {
			System.out.printf("Saving a local copy for %s - %s...", artist, track);
		}
		try {
			final File localCopy = this.getLocalCopy(artist, track);
			if (localCopy.exists() && !override) {
				if (SpotMyLyrics.VERBOSE) {
					System.out.println("\tAlready exists!");
				}
				return false;
			}
			localCopy.getParentFile().mkdirs();
			final BufferedWriter writer = new BufferedWriter(new FileWriter(localCopy));
			writer.write(lyrics);
			writer.flush();
			writer.close();
			if (SpotMyLyrics.VERBOSE) {
				System.out.printf("\tSuccess!\n");
			}
			return true;
		} catch (final IOException exception) {
			System.err.println("An exception occurred while writing a local copy: " + exception.getMessage());
			return false;
		}
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
		if ((lyrics = this.readFromLocalCopy(compliantArtist, compliantTitle)) == null) {
			String source = this.fetchSource(compliantArtist, compliantTitle);
			if (source != null) {
				source = this.formatSource(source);
				lyrics = this.extractLyrics(source);
			}
		}
		if (SpotMyLyrics.OS.contains("mac")) {
			System.out.println("\033]0;" + artist + " - " + track + "\007");
			if (lyrics != null) {
				System.out.println((char) (27) + "[2J" + artist + " - " + track + "\n\n" + lyrics + "\n");
				this.saveToLocalCopy(compliantArtist, compliantTitle, lyrics, false);
			} else {
				System.out.println((char) (27) + "[2J" + artist + " - " + track + "\n\n" + "No lyrics found." + "\n");
			}
		} else {
			if (lyrics != null) {
				System.out.println("\n" + artist + " - " + track + "\n\n" + lyrics + "\n");
				this.saveToLocalCopy(compliantArtist, compliantTitle, lyrics, false);
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
				System.out.printf("SpotMyLyrics v.%s - By Skander J. (%s)\n", SpotMyLyrics.SPOT_MY_LYRICS_VERSION, SpotMyLyrics.GITHUB_URL);
				System.out.printf("\t:help (:h)\t\tPrints credits & commands list\n");
				System.out.printf("\t:auto (:a)\t\tToggles auto-refreshing\n");
				System.out.printf("\t:refresh (:r)\t\tFetches the lyrics to the current song\n");
				System.out.printf("\t:aliases (:as)\t\tReloades the aliases file\n");
				System.out.printf("\t:emptycache (:ec)\tDeletes all local copies\n");
				System.out.printf("\t:quit (:q)\t\tQuits the app\n");
				break;
			case ":refresh":
			case ":r":
				final String[] spotifyAnswer = this.querySpotify();
				if (spotifyAnswer != null) {
					this.printLyrics(spotifyAnswer);
				}
				break;
			case ":aliases":
			case ":as":
				if (this.readAliases(SpotMyLyrics.ALIASES_FILE)) {
					System.out.println("Successfully reloaded the aliases file!");
				}
				break;
			case ":emptycache":
			case ":ec":
				System.out.println("Coming soon!...");
				// TODO
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
					final String[] spotifyAnswer = SpotMyLyrics.this.querySpotify();
					if (spotifyAnswer != null) {
						SpotMyLyrics.this.printLyrics(spotifyAnswer);
						this.previousSpotifyAnswer = spotifyAnswer;
					}
				} else {
					final String[] spotifyAnswer = SpotMyLyrics.this.querySpotify();
					if (spotifyAnswer != null) {
						if (spotifyAnswer[2].equals(this.previousSpotifyAnswer[2]) && spotifyAnswer[3].equals(this.previousSpotifyAnswer[3])) {
							return;
						} else {
							SpotMyLyrics.this.printLyrics(spotifyAnswer);
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
	 * Starts the app.
	 */
	public void run() {
		System.out.printf("SpotMyLyrics v.%s - By Skander J. (%s)\nThanks for using my software!\n", SpotMyLyrics.SPOT_MY_LYRICS_VERSION, SpotMyLyrics.GITHUB_URL);
		System.out.printf("(Cache size: %s bytes for %d items)\n", this.humanReadableByteCountSI(this.cacheSize()), this.cachedItems());
		final Scanner scanner = new Scanner(System.in);
		System.out.print("Press [ENTER] key to continue...");
		scanner.nextLine();
		if (this.readAliases(SpotMyLyrics.ALIASES_FILE)) {
			this.enableAutoRefreshing();
			this.handleInput(scanner);
		}
	}

	public static void main(final String[] args) {
		SpotMyLyrics.getInstance().run();
	}
}