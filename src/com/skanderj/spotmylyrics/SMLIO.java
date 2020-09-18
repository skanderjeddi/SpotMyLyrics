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
import java.util.Map;

public final class SMLIO {
	private SMLIO() {
		return;
	}

	/**
	 * Fetches the source code of the lyrics page.
	 *
	 * @param artist the artist's name
	 * @param track  the song's track
	 * @return the source code, or null if an exception occurred
	 */
	public static final String fetchSource(final String url, final Object... args) {
		final String formattedURL = String.format(url, args);
		if (SML.VERBOSE) {
			System.out.printf("Fetching page source for %s\n", formattedURL);
		}
		URL urlObject = null;
		try {
			urlObject = new URL(formattedURL);
		} catch (final MalformedURLException malformedURLException) {
			System.err.println("An exception occurred while building the URL: " + malformedURLException.getMessage());
			return null;
		}
		InputStream urlInputStream = null;
		try {
			urlInputStream = urlObject.openStream();
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
		if (SML.VERBOSE) {
			System.out.printf("\tSuccess!\n");
		}
		return source.toString().strip();
	}

	public static boolean readFileToMap(final File file, final Map<String, String> map, final String separator) {
		if (SML.VERBOSE) {
			System.out.printf("Reading aliases from %s...", file.getAbsolutePath());
		}
		map.clear();
		try {
			final BufferedReader reader = new BufferedReader(new FileReader(file));
			String currentLine = null;
			while ((currentLine = reader.readLine()) != null) {
				if (currentLine.startsWith("#")) {
					continue;
				}
				final String key = currentLine.split(":")[0], value = currentLine.split(separator)[1];
				map.put(key, value);
			}
			reader.close();
		} catch (final IOException exception) {
			System.err.println("An exception occurred while loading aliases: " + exception.getMessage());
			return false;
		}
		if (SML.VERBOSE) {
			System.out.printf("\tSuccess!\n");
		}
		return true;
	}

	/**
	 * Reads the entire content of a file into a single string.
	 *
	 * @param file the file
	 * @return the contents of the file
	 */
	public static String readWhole(final File file) {
		if (SML.VERBOSE) {
			System.out.printf("Reading %s...", file.getAbsolutePath());
		}
		if (file.exists()) {
			final StringBuilder lyrics = new StringBuilder();
			try {
				final BufferedReader reader = new BufferedReader(new FileReader(file));
				String currentLine = null;
				while ((currentLine = reader.readLine()) != null) {
					lyrics.append(currentLine + "\n");
				}
				reader.close();
				if (SML.VERBOSE) {
					System.out.printf("\tSuccess!\n");
				}
				return lyrics.toString().strip();
			} catch (final IOException readingException) {
				System.err.println("An exception occurred while reading from local copy: " + readingException.getMessage());
				return null;
			}
		} else {
			if (SML.VERBOSE) {
				System.out.printf("\tFile doesn't exist!\n");
			}
			return null;
		}
	}

	/**
	 * Stores a string in a local file on the HDD.
	 *
	 * @param file   the target file
	 * @param string the content to save
	 * @return true if success, false otherwise
	 */
	public static boolean saveToFile(final File file, final String string, final boolean override) {
		if (SML.VERBOSE) {
			System.out.printf("Saving %s...", file.getAbsolutePath());
		}
		try {
			if (file.exists() && !override) {
				if (SML.VERBOSE) {
					System.out.println("\tAlready exists!");
				}
				return false;
			}
			file.getParentFile().mkdirs();
			final BufferedWriter writer = new BufferedWriter(new FileWriter(file));
			writer.write(string);
			writer.flush();
			writer.close();
			if (SML.VERBOSE) {
				System.out.printf("\tSuccess!\n");
			}
			return true;
		} catch (final IOException exception) {
			System.err.println("An exception occurred while writing to a file: " + exception.getMessage());
			return false;
		}
	}

	/**
	 * Recursively counts the number of files in a given directory.
	 *
	 * @return the number of files if @param file is a directory, -1 otherwise
	 */
	public static int countFiles(final File file) {
		if (!file.isDirectory()) {
			return -1;
		}
		int counter = 0;
		for (final File child : file.listFiles()) {
			counter += child.isDirectory() ? SMLIO.countFiles(child) : 1;
		}
		return counter;
	}

	/**
	 * @return the size in bytes of the @param file.
	 */
	public static long fileSize(final File file) {
		long length = 0;
		if (file.isDirectory()) {
			for (final File subFile : file.listFiles()) {
				length += subFile.isDirectory() ? SMLIO.fileSize(subFile) : subFile.length();
			}
		} else {
			return file.length();
		}
		return length;
	}

	/**
	 * @return a String converting the raw @param bytes into human readable, SI
	 *         format.
	 */
	public static String humanReadableByteCountSI(long bytes) {
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
	 * Recursively deletes a directory.
	 */
	public static boolean deleteFile(final File file) {
		if (file.isDirectory()) {
			boolean result = true;
			for (final File subFile : file.listFiles()) {
				result = (result && SMLIO.deleteFile(subFile));
			}
			file.delete();
			return result;
		} else {
			return file.delete();
		}
	}
}
