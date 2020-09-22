package com.skanderj.spotmylyrics;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public final class SMLSYS {
	private SMLSYS() {
		return;
	}

	// Get the system OS
	public static final OS _OS = System.getProperty("os.name").toLowerCase().contains("win") ? OS.WINDOWS : System.getProperty("os.name").toLowerCase().contains("mac os") ? OS.MACOS : System.getProperty("os.name").toLowerCase().contains("linux") ? OS.LINUX : OS.OTHER;

	// AppleScript code used to fetch the currently playing Spotify song (only on
	// macOS)
	public static final String APPLESCRIPT_CODE = "getCurrentlyPlayingTrack()\n" + "on getCurrentlyPlayingTrack()\n" + "tell application \"Spotify\"\n" + "set isPlaying to player state as string\n" + "set currentArtist to artist of current track as string\n" + "set currentTrack to name of current track as string\n" + "return {currentArtist, currentTrack}\n" + "end tell\n" + "end getCurrentlyPlayingTrack";

	/**
	 * Runs a script through the underlying system.
	 *
	 * @param args
	 * @return the process' output
	 * @throws Exception
	 */
	public static String readProcessOutput(final String[] args) throws Exception {
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
	 * @return a (artist, track) pair if found.
	 */
	public static String querySpotify() {
		if (SML.VERBOSE) {
			System.out.printf("Querying Spotify for the current song...\n");
		}
		String answer = SML.EMPTY;
		try {
			String[] scriptArgs;
			switch (SMLSYS._OS) {
			case WINDOWS:
				scriptArgs = new String[] { "python", "./scripts/queryspotify.py", "win" };
				break;
			case MACOS:
				scriptArgs = new String[] { "osascript", "-e", SMLSYS.APPLESCRIPT_CODE };
				break;
			case LINUX:
				scriptArgs = new String[] { "python", "./scripts/queryspotify.py", "linux" };
				break;
			case OTHER:
			default:
				return null;
			}
			answer = SMLSYS.readProcessOutput(scriptArgs);
		} catch (final Exception exception) {
			System.err.println("An error occurred while querying Spotify for the current song: " + exception.getMessage());
			return null;
		}
		if (answer.equals("PAUSED") || answer.equals("NOT RUNNING")) {
			System.err.println("Spotify is either paused or not running, can't identify the current track!");
			return null;
		}
		return answer;
	}

	public enum OS {
		WINDOWS, MACOS, LINUX, OTHER;
	}
}
