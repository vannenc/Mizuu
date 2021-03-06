package com.miz.filesources;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import android.content.Context;
import android.database.Cursor;

import com.miz.abstractclasses.MovieFileSource;
import com.miz.db.DbAdapter;
import com.miz.functions.DbMovie;
import com.miz.functions.FileSource;
import com.miz.functions.MizLib;
import com.miz.mizuu.MizuuApplication;

public class FileMovie extends MovieFileSource<File> {

	private HashMap<String, String> existingMovies = new HashMap<String, String>();
	private File tempFile;

	public FileMovie(Context context, FileSource fileSource, boolean ignoreRemovedFiles, boolean subFolderSearch, boolean clearLibrary, boolean disableEthernetWiFiCheck) {
		super(context, fileSource, ignoreRemovedFiles, subFolderSearch, clearLibrary, disableEthernetWiFiCheck);
	}

	@Override
	public void removeUnidentifiedFiles() {
		DbAdapter db = MizuuApplication.getMovieAdapter();
		List<DbMovie> dbMovies = getDbMovies();

		File temp;
		int count = dbMovies.size();
		for (int i = 0; i < count; i++) {
			if (!dbMovies.get(i).isNetworkFile() && !dbMovies.get(i).isUpnpFile()) {
				temp = new File(dbMovies.get(i).getFilepath());
				if (temp.exists() && dbMovies.get(i).isUnidentified())
					db.deleteMovie(dbMovies.get(i).getRowId());
			}
		}
	}

	@Override
	public void removeUnavailableFiles() {
		DbAdapter db = MizuuApplication.getMovieAdapter();
		List<DbMovie> dbMovies = getDbMovies(), deletedMovies = new ArrayList<DbMovie>();

		boolean deleted;
		File temp;
		int count = dbMovies.size();
		for (int i = 0; i < count; i++) {
			if (!dbMovies.get(i).isNetworkFile()) {
				temp = new File(dbMovies.get(i).getFilepath());
				if (!temp.exists()) {
					deleted = db.deleteMovie(dbMovies.get(i).getRowId());
					if (deleted)
						deletedMovies.add(dbMovies.get(i));
				}
			}
		}

		count = deletedMovies.size();
		for (int i = 0; i < count; i++) {
			if (!db.movieExists(deletedMovies.get(i).getTmdbId())) {
				MizLib.deleteFile(new File(deletedMovies.get(i).getThumbnail()));
				MizLib.deleteFile(new File(deletedMovies.get(i).getBackdrop()));
			}
		}

		// Clean up
		deletedMovies.clear();
	}

	@Override
	public List<String> searchFolder() {

		DbAdapter dbHelper = MizuuApplication.getMovieAdapter();
		Cursor cursor = dbHelper.fetchAllMovies(DbAdapter.KEY_TITLE + " ASC", ignoreRemovedFiles(), true); // Query database to return all movies to a cursor

		try {
			while (cursor.moveToNext()) {// Add all movies in cursor to ArrayList of all existing movies
				existingMovies.put(cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_FILEPATH)), "");
			}
		} catch (Exception e) {
		} finally {
			cursor.close(); // Close cursor
		}

		LinkedHashSet<String> results = new LinkedHashSet<String>();

		// Do a recursive search in the file source folder
		recursiveSearch(getFolder(), results);

		List<String> list = new ArrayList<String>();

		Iterator<String> it = results.iterator();
		while (it.hasNext())
			list.add(it.next());

		return list;
	}

	@Override
	public void recursiveSearch(File folder, LinkedHashSet<String> results) {
		try {
			if (searchSubFolders()) {
				if (folder.isDirectory()) {
					// Check if this is a DVD folder
					if (folder.getName().equalsIgnoreCase("video_ts")) {
						File[] children = folder.listFiles();
						for (int i = 0; i < children.length; i++) {
							if (children[i].getName().equalsIgnoreCase("video_ts.ifo"))
								addToResults(children[i], results);
						}
					} // Check if this is a Blu-ray folder
					else if (folder.getName().equalsIgnoreCase("bdmv")) {
						File[] children = folder.listFiles();
						for (int i = 0; i < children.length; i++) {
							if (children[i].getName().equalsIgnoreCase("stream")) {
								File[] m2tsVideoFiles = children[i].listFiles();

								if (m2tsVideoFiles.length > 0) {
									File largestFile = m2tsVideoFiles[0];

									for (int j = 0; j < m2tsVideoFiles.length; j++)
										if (largestFile.length() < m2tsVideoFiles[j].length())
											largestFile = m2tsVideoFiles[j];

									addToResults(largestFile, results);
								}
							}
						}
					} else {
						String[] childs = folder.list();
						for (int i = 0; i < childs.length; i++) {
							tempFile = new File(folder.getAbsolutePath(), childs[i]);
							recursiveSearch(tempFile, results);
						}
					}
				} else {
					addToResults(folder, results);
				}
			} else {
				File[] children = folder.listFiles();
				for (int i = 0; i < children.length; i++)
					addToResults(children[i], results);
			}
		} catch (Exception e) {}
	}

	@Override
	public void addToResults(File file, LinkedHashSet<String> results) {
		if (supportsNfo() && MizLib.isNfoFile(file.getAbsolutePath())) {
			try {
				addNfoFile(MizLib.removeExtension(file.getAbsolutePath()), new FileInputStream(file));
			} catch (FileNotFoundException ignored) {}
		} else if (MizLib.checkFileTypes(file.getAbsolutePath())) {
			if (file.length() < getFileSizeLimit() && !file.getName().equalsIgnoreCase("video_ts.ifo"))
				return;

			if (!clearLibrary())
				if (existingMovies.get(file.getAbsolutePath()) != null) return;

			String tempFileName = file.getName().substring(0, file.getName().lastIndexOf("."));
			if (tempFileName.toLowerCase(Locale.ENGLISH).matches(".*part[2-9]|cd[2-9]")) return;

			//Add the file if it reaches this point
			results.add(file.getAbsolutePath());
		}
	}

	@Override
	public File getRootFolder() {
		return new File(getFileSource().getFilepath());
	}

	@Override
	public String toString() {
		return getRootFolder().getAbsolutePath();
	}
}