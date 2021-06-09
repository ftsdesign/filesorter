package biz.ftsdesign.filesorter;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class FileSorterEngine {
	private static Logger log = Logger.getLogger(FileSorterEngine.class.getCanonicalName());
	private static final Pattern PATTERN_UUID_FILE = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.\\w+");
	public static final String VAR_FILE_NAME = "F";
	private static final int CMD_TIMEOUT_SECONDS = 10;
	public static final String SAMPLE_CMD = "exiftool -P -overwrite_original -all= $" + VAR_FILE_NAME;
	
	private boolean postProcess = false;
	private String postProcessCommand = null;
	
	private boolean resetTimestamp = false;
	private Instant timestamp = Instant.EPOCH;

	private static final FileFilter SOURCE_FILE_FILTER = new FileFilter() {
		@Override
		public boolean accept(File f) {
			return f != null && f.isFile();
		}
	};
	
	private static final FileFilter DIR_FILTER = new FileFilter() {
		@Override
		public boolean accept(File f) {
			return f != null && f.isDirectory();
		}
	};
	
	private final Collection<File> sourceDirs;
	private final File targetDir;
	private boolean dryRun = true;
	
	public FileSorterEngine(Collection<File> sourceDirs, File targetDir) {
		this.sourceDirs = sourceDirs;
		this.targetDir = targetDir;
	}
	
	public void setPostProcess(boolean postProcess) {
		this.postProcess = postProcess;
	}
	
	public void setPostProcessCommand(String postProcessCommand) {
		this.postProcessCommand = postProcessCommand;
	}
	
	public void setResetTimestamp(boolean resetTimestamp) {
		this.resetTimestamp = resetTimestamp;
	}
	
	public void setTimestamp(Instant timestamp) {
		this.timestamp = timestamp;
	}

	public void process(boolean deleteSource, boolean dryRun) throws IOException {
		long t1 = System.currentTimeMillis();
		this.dryRun  = dryRun;
		if (dryRun) {
			log.info("***** DRY RUN *****");
		}
		validate();
		
		log.info("Processing...");
		log.info("Source dirs: " + sourceDirs);
		log.info("Target dir: " + targetDir.getAbsolutePath());
		log.info("Delete source: " + deleteSource);
		log.info("Set timestamp: " + resetTimestamp + (timestamp != null ? " " + timestamp : ""));
		log.info("Post-process: " + postProcess);

		int filesMoved = 0;
		long bytesWritten = 0;
		log.info("Scanning " + sourceDirs.size() + " source dirs...");
		Collection<File> allFiles = listFilesRecursive(sourceDirs, SOURCE_FILE_FILTER);
		log.info("Source files found: " + allFiles.size());
		for (File sourceFile : allFiles) {
			bytesWritten += sourceFile.length();
			File targetFile = pickUniqueTarget(sourceFile, targetDir, timestamp);
			moveFile(sourceFile, targetFile, deleteSource, timestamp);
			filesMoved++;
		}
		
		if (!dryRun && timestamp != null) {
			// Because it will be modified after we add the files
			// Including targetDir itself
			log.info("Setting timestamp on directories...");
			setTimestampForDirs(targetDir, timestamp);
		}
		
		t1 = System.currentTimeMillis() - t1;
		log.info("Processed " + filesMoved + " files in " + t1 + " ms, " + bytesWritten + " bytes written");
	}
	
	private void setTimestampForDirs(File dir, Instant timestamp) {
		File[] subdirs = dir.listFiles(DIR_FILTER);
		if (subdirs != null) {
			// Depth first
			for (File subdir : subdirs) {
				setTimestampForDirs(subdir, timestamp);
			}
		}
		dir.setLastModified(timestamp.toEpochMilli());
	}

	private Collection<File> listFilesRecursive(Collection<File> dirs, FileFilter fileFilter) {
		final List<File> files = new LinkedList<>();
		for (File dir : dirs) {
			files.addAll(listFilesRecursive(dir, fileFilter));
		}
		return files;
	}
	
	private Collection<File> listFilesRecursive(File dir, FileFilter fileFilter) {
		final List<File> out = new LinkedList<>();
		File[] subdirs = dir.listFiles(DIR_FILTER);
		if (subdirs != null) {
			for (File subdir : subdirs) {
				out.addAll(listFilesRecursive(subdir, fileFilter));
			}
		}
		File[] files = dir.listFiles(fileFilter);
		if (files != null) {
			for (File f : files) {
				out.add(f);
			}
		}
		return out;
	}

	private void postProcess(File file) throws IOException, InterruptedException {
		String[] command = {"bash", "-c", postProcessCommand};
		File workingDir = file.getParentFile();
		ProcessBuilder pb = new ProcessBuilder(command);
		pb.directory(workingDir);
		pb.environment().put(VAR_FILE_NAME, file.getName());
		Process p = pb.start();
		p.waitFor(CMD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		if (p.exitValue() != 0) {
			log.warning("Command terminated with exit code " + p.exitValue());
		}
	}

	private static String extractExt(String name) {
		String ext;
		int i = name.lastIndexOf(".");
		if (i >= 0) {
			ext = name.substring(i);
		} else {
			ext = "";
		}
		return ext.toLowerCase();
	}
	
	private String getNewName(File sourceFile) {
		String ext = extractExt(sourceFile.getName());
		return UUID.randomUUID().toString() + ext;
	}

	private File pickUniqueTarget(File sourceFile, File targetDir, Instant timestamp) throws IOException {
		final int maxAttempts = 10;
		int attempts = 0;
		File targetFile;
		do {
			String newName;
			if (attempts == 0 && isGoodName(sourceFile.getName())) {
				newName = sourceFile.getName();
			} else {
				newName = getNewName(sourceFile);
			}
			File targetSubdir = new File(targetDir, newName.substring(0, 1));
			if (targetSubdir.exists()) {
				if (!targetSubdir.isDirectory()) {
					throw new IOException("Not a directory: " + targetSubdir);
				}
				if (!targetSubdir.canWrite()) {
					throw new IOException("Cannot write to " + targetSubdir);
				}
				targetFile = new File(targetSubdir, newName);
			} else {
				if (dryRun) {
					throw new IOException("Unable to create subdirectory because dryRun=" + dryRun);
				}
				if (targetSubdir.mkdirs()) {
					if (timestamp != null) {
						targetSubdir.setLastModified(timestamp.toEpochMilli());
					}
					targetFile = new File(targetSubdir, newName);
				} else {
					throw new IOException("Failed to create dir " + targetSubdir);
				}
			}
		} while (targetFile.exists() && ++attempts < maxAttempts);

		if (targetFile.exists())
			throw new IOException("Unable to pick a unique name after " + attempts + " attempts");

		return targetFile;
	}
	
	static boolean isGoodName(String fileName) {
		return PATTERN_UUID_FILE.matcher(fileName).matches();
	}

	private void moveFile(File sourceFile, File targetFile, boolean deleteSource, Instant timestamp) throws IOException {
		log.info(sourceFile.getAbsolutePath() + " => " + targetFile.getAbsolutePath());
		if (!dryRun && !sourceFile.isDirectory()) {
			byte[] buffer = new byte[(int) sourceFile.length()];
			try (InputStream in = new FileInputStream(sourceFile); OutputStream out = new FileOutputStream(targetFile)) {
				int bytesRead = in.read(buffer);
				if (bytesRead != buffer.length)
					throw new IOException();
				out.write(buffer);
				out.close();
				in.close();
				if (targetFile.length() != sourceFile.length())
					throw new IOException("File copy problem with " + sourceFile.getName());
				if (postProcess) {
					try {
						postProcess(targetFile);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				if (timestamp != null) {
					targetFile.setLastModified(timestamp.toEpochMilli());
				}
				if (deleteSource) {
					sourceFile.delete();
				}
			} finally {
				
			}
		}
	}
	
	private void validate() throws IOException {
		for (File sourceDir : sourceDirs) {
			if (sourceDir.equals(targetDir)) {
				throw new IOException("Source and target directories must be different: " + sourceDir);
			}
		}
	}
}
