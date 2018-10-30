package org.cryptomator.frontend.fuse;

import com.google.common.base.CharMatcher;
import jnr.constants.platform.darwin.OpenFlags;
import jnr.ffi.Pointer;
import jnr.ffi.types.gid_t;
import jnr.ffi.types.mode_t;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import jnr.ffi.types.uid_t;
import org.cryptomator.frontend.fuse.locks.DataLock;
import org.cryptomator.frontend.fuse.locks.LockManager;
import org.cryptomator.frontend.fuse.locks.PathLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;
import ru.serce.jnrfuse.struct.Statvfs;
import ru.serce.jnrfuse.struct.Timespec;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/**
 * Read-Only FUSE-NIO-Adapter based on Sergey Tselovalnikov's <a href="https://github.com/SerCeMan/jnr-fuse/blob/0.5.1/src/main/java/ru/serce/jnrfuse/examples/HelloFuse.java">HelloFuse</a>
 */
@PerAdapter
public class CompleteAdapter extends FuseStubFS implements FuseNioAdapter {

	private static final Logger LOG = LoggerFactory.getLogger(CompleteAdapter.class);
	private static final int BLOCKSIZE = 4096;
	protected final Path root;
	protected final FileStore fileStore;
	protected final LockManager lockManager;
	private final ReadWriteDirectoryHandler dirHandler;
	private final FileAttributesUtil attrUtil;
	private final BitMaskEnumUtil bitMaskUtil;
	private final ReadWriteFileHandler fileHandler;

	@Inject
	public CompleteAdapter(@Named("root") Path root, FileStore fileStore, LockManager lockManager, ReadWriteDirectoryHandler dirHandler, ReadWriteFileHandler fileHandler, FileAttributesUtil attrUtil, BitMaskEnumUtil bitMaskUtil) {
		LOG.info("====================== YOU ARE USING THE COMPLETE ADAPTER =======================");
		this.root = root;
		this.fileStore = fileStore;
		this.lockManager = lockManager;
		this.dirHandler = dirHandler;
		this.fileHandler = fileHandler;
		this.attrUtil = attrUtil;
		this.bitMaskUtil = bitMaskUtil;
	}

	protected Path resolvePath(String absolutePath) {
		String relativePath = CharMatcher.is('/').trimLeadingFrom(absolutePath);
		return root.resolve(relativePath);
	}

	@Override
	public synchronized int statfs(String path, Statvfs stbuf) {
		try {
			long total = fileStore.getTotalSpace();
			long avail = fileStore.getUsableSpace();
			long tBlocks = total / BLOCKSIZE;
			long aBlocks = avail / BLOCKSIZE;
			stbuf.f_bsize.set(BLOCKSIZE);
			stbuf.f_frsize.set(BLOCKSIZE);
			stbuf.f_blocks.set(tBlocks);
			stbuf.f_bavail.set(aBlocks);
			stbuf.f_bfree.set(aBlocks);
			return 0;
		} catch (IOException | RuntimeException e) {
			LOG.error("statfs failed.", e);
			return -ErrorCodes.EIO();
		}
	}


	@Override
	public synchronized int getattr(String path, FileStat stat) {
		try (PathLock pathLock = lockManager.createPathLock(path).forReading();
			 DataLock dataLock = pathLock.lockDataForReading()) {
			Path node = resolvePath(path);
			BasicFileAttributes attrs = Files.readAttributes(node, BasicFileAttributes.class);
			if (attrs.isDirectory()) {
				return dirHandler.getattr(node, attrs, stat);
			} else {
				return fileHandler.getattr(node, attrs, stat);
			}
		} catch (NoSuchFileException e) {
			// see Files.notExists
			return -ErrorCodes.ENOENT();
		} catch (IOException | RuntimeException e) {
			LOG.error("getattr failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public synchronized int readdir(String path, Pointer buf, FuseFillDir filler, @off_t long offset, FuseFileInfo fi) {
		try (PathLock pathLock = lockManager.createPathLock(path).forReading();
			 DataLock dataLock = pathLock.lockDataForReading()) {
			Path node = resolvePath(path);
			return dirHandler.readdir(node, buf, filler, offset, fi);
		} catch (NotDirectoryException e) {
			return -ErrorCodes.ENOENT();
		} catch (IOException | RuntimeException e) {
			LOG.error("readdir failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public synchronized int open(String path, FuseFileInfo fi) {
		try (PathLock pathLock = lockManager.createPathLock(path).forReading();
			 DataLock dataLock = pathLock.lockDataForReading()) {
			Path node = resolvePath(path);
			// TODO do we need to distinguish files vs. dirs? https://github.com/libfuse/libfuse/wiki/Invariants
			if (Files.isDirectory(node)) {
				return -ErrorCodes.EISDIR();
			} else if (Files.exists(node)) {
				return fileHandler.open(node, fi);
			} else {
				return -ErrorCodes.ENOENT();
			}
		} catch (RuntimeException e) {
			LOG.error("open failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public synchronized int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
		try (PathLock pathLock = lockManager.createPathLock(path).forReading();
			 DataLock dataLock = pathLock.lockDataForReading()) {
			Path node = resolvePath(path);
			assert Files.exists(node);
			return fileHandler.read(node, buf, size, offset, fi);
		} catch (RuntimeException e) {
			LOG.error("read failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public synchronized int release(String path, FuseFileInfo fi) {
		try (PathLock pathLock = lockManager.createPathLock(path).forReading();
			 DataLock dataLock = pathLock.lockDataForReading()) {
			Path node = resolvePath(path);
			return fileHandler.release(node, fi);
		} catch (RuntimeException e) {
			LOG.error("release failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public synchronized void destroy(Pointer initResult) {
		try {
			close();
		} catch (IOException | RuntimeException e) {
			LOG.error("destroy failed.", e);
		}
	}

	@Override
	public void close() throws IOException {
		fileHandler.close();
	}

	@Override
	public synchronized int mkdir(String path, @mode_t long mode) {
		Path node = resolvePath(path);
		try (PathLock pathLock = lockManager.createPathLock(path).forWriting();
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			Files.createDirectory(node);
			return 0;
		} catch (FileAlreadyExistsException e) {
			return -ErrorCodes.EEXIST();
		} catch (IOException | RuntimeException e) {
			LOG.error("mkdir failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public synchronized int create(String path, @mode_t long mode, FuseFileInfo fi) {
		try (PathLock pathLock = lockManager.createPathLock(path).forWriting();
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			Set<OpenFlags> flags = bitMaskUtil.bitMaskToSet(OpenFlags.class, fi.flags.longValue());
			Path node = resolvePath(path);
			LOG.trace("createAndOpen {} with openOptions {}", node, flags);
			if (fileStore.supportsFileAttributeView(PosixFileAttributeView.class)) {
				FileAttribute<?> attrs = PosixFilePermissions.asFileAttribute(attrUtil.octalModeToPosixPermissions(mode));
				return fileHandler.createAndOpen(node, fi, attrs);
			} else {
				return fileHandler.createAndOpen(node, fi);
			}
		} catch (RuntimeException e) {
			LOG.error("create failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public synchronized int chown(String path, @uid_t long uid, @gid_t long gid) {
		LOG.trace("Ignoring chown(uid={}, gid={}) call. Files will be served with static uid/gid.", uid, gid);
		return 0;
	}

	@Override
	public synchronized int chmod(String path, @mode_t long mode) {
		try (PathLock pathLock = lockManager.createPathLock(path).forReading();
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			Path node = resolvePath(path);
			Files.setPosixFilePermissions(node, attrUtil.octalModeToPosixPermissions(mode));
			return 0;
		} catch (NoSuchFileException e) {
			return -ErrorCodes.ENOENT();
		} catch (UnsupportedOperationException e) {
			LOG.warn("Setting posix permissions not supported by underlying file system.");
			return -ErrorCodes.ENOSYS();
		} catch (IOException | RuntimeException e) {
			LOG.error("chmod failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public synchronized int unlink(String path) {
		try (PathLock pathLock = lockManager.createPathLock(path).forWriting();
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			Path node = resolvePath(path);
			assert !Files.isDirectory(node);
			LOG.info("Unlinking {}", path);
			return delete(node);
		} catch (RuntimeException e) {
			LOG.error("unlink failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public synchronized int rmdir(String path) {
		try (PathLock pathLock = lockManager.createPathLock(path).forWriting();
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			Path node = resolvePath(path);
			LOG.trace("rmdir {}.", path);
			assert Files.isDirectory(node);
			return delete(node);
		} catch (RuntimeException e) {
			LOG.error("rmdir failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	private int delete(Path node) {
		try {
			// TODO: recursively check for open file handles
			if (Files.isDirectory(node)) {
				deleteAppleDoubleFiles(node);
			}
			Files.delete(node);
			return 0;
		} catch (FileNotFoundException e) {
			return -ErrorCodes.ENOENT();
		} catch (DirectoryNotEmptyException e) {
			return -ErrorCodes.ENOTEMPTY();
		} catch (IOException e) {
			LOG.error("Error deleting file: " + node, e);
			return -ErrorCodes.EIO();
		}
	}

	/**
	 * Specialised method on MacOS due to the usage of the <em>-noappledouble</em> option in the {@link org.cryptomator.frontend.fuse.mount.MacMounter} and the possible existence of AppleDouble or DSStore-Files.
	 *
	 * @param node the directory path for which is checked for such files
	 * @throws IOException if an AppleDouble file cannot be deleted or opening of a directory stream fails
	 *
	 * @see <a href="https://github.com/osxfuse/osxfuse/wiki/Mount-options#noappledouble">OSXFuse Documentation of the <em>-noappledouble</em> option</a>
	 */
	private void deleteAppleDoubleFiles(Path node) throws IOException {
		try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(node, MacUtil::isAppleDoubleOrDStoreName)) {
			for (Path p : directoryStream) {
				Files.delete(p);
			}
		}
	}

	@Override
	public synchronized int rename(String oldpath, String newpath) {
		try (PathLock oldPathLock = lockManager.createPathLock(oldpath).forWriting();
			 DataLock oldDataLock = oldPathLock.lockDataForWriting();
			 PathLock newPathLock = lockManager.createPathLock(newpath).forWriting();
			 DataLock newDataLock = newPathLock.lockDataForWriting()) {
			// TODO: recursively check for open file handles
			Path nodeOld = resolvePath(oldpath);
			Path nodeNew = resolvePath(newpath);
			LOG.info("Renaming {} to {}", oldpath, newpath);
			Files.move(nodeOld, nodeNew, StandardCopyOption.REPLACE_EXISTING);
			return 0;
		} catch (FileNotFoundException e) {
			return -ErrorCodes.ENOENT();
		} catch (DirectoryNotEmptyException e) {
			return -ErrorCodes.ENOTEMPTY();
		} catch (IOException | RuntimeException e) {
			LOG.error("Renaming " + oldpath + " to " + newpath + " failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public synchronized int utimens(String path, Timespec[] timespec) {
		/*
		 * From utimensat(2) man page:
		 * the array times: times[0] specifies the new "last access time" (atime);
		 * times[1] specifies the new "last modification time" (mtime).
		 */
		assert timespec.length == 2;
		try (PathLock pathLock = lockManager.createPathLock(path).forReading();
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			Path node = resolvePath(path);
			return fileHandler.utimens(node, timespec[1], timespec[0]);
		} catch (RuntimeException e) {
			LOG.error("utimens failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public synchronized int write(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
		try (PathLock pathLock = lockManager.createPathLock(path).forReading();
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			Path node = resolvePath(path);
			return fileHandler.write(node, buf, size, offset, fi);
		} catch (RuntimeException e) {
			LOG.error("write failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public synchronized int truncate(String path, @off_t long size) {
		try (PathLock pathLock = lockManager.createPathLock(path).forReading();
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			Path node = resolvePath(path);
			return fileHandler.truncate(node, size);
		} catch (RuntimeException e) {
			LOG.error("truncate failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public synchronized int ftruncate(String path, long size, FuseFileInfo fi) {
		try (PathLock pathLock = lockManager.createPathLock(path).forReading();
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			Path node = resolvePath(path);
			return fileHandler.ftruncate(node, size, fi);
		} catch (RuntimeException e) {
			LOG.error("ftruncate failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public synchronized int flush(String path, FuseFileInfo fi) {
		try {
			Path node = resolvePath(path);
			return fileHandler.flush(node, fi);
		} catch (RuntimeException e) {
			LOG.error("flush failed.", e);
			return -ErrorCodes.EIO();
		}
	}
}
