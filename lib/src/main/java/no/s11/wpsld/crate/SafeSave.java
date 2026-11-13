/*
 * Copyright 2026 The University of Manchester, UK
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package no.s11.wpsld.crate;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.UUID;

/**
 * Writing a file so that it is never left half written, e.g. if wpsld or
 * the computer stops while saving, or the disk is full: the new content is
 * written in full to a temporary file beside it first, and only then takes
 * its place.
 * <p>
 * Where the file system has POSIX semantics, the file is unlinked and the
 * temporary file hard-linked as it, then checked to be the same file (the
 * same inode), so that a save of another program at the same moment is
 * noticed rather than lost silently; only then is the temporary file
 * unlinked. Where hard links aren't supported (e.g. FAT), the temporary
 * file is moved over it atomically, which on Windows replaces it. A
 * symbolic link is kept: the file it links to is saved, beside itself, as
 * the temporary file must be on the same file system. Without permission
 * to make files in the folder, the file is written over in place.
 */
public final class SafeSave {
	private SafeSave() {
	}

	/** How the file was written */
	public enum Method {
		/** Unlinked, and the temporary file linked as it */
		LINKED,
		/** The temporary file moved over it */
		MOVED,
		/** Written over in place, as no file could be made beside it */
		IN_PLACE
	}

	/**
	 * Write this content to a file, safely as above.
	 *
	 * @throws IOException if it couldn't be written; the file as it was, or,
	 *                     if another program saved it at the same moment,
	 *                     the temporary file kept, named in the message
	 */
	public static Method write(Path file, byte[] content) throws IOException {
		Path target = Files.isSymbolicLink(file) ? file.toRealPath() : file.toAbsolutePath();
		Path folder = target.getParent();
		Path temp = folder.resolve("." + target.getFileName() + "." + UUID.randomUUID() + ".tmp");
		try {
			Files.write(temp, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		} catch (AccessDeniedException e) {
			// The folder isn't ours to add files to; the file may be
			Files.write(target, content);
			return Method.IN_PLACE;
		}
		try {
			try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
				channel.force(true);
			}
			keepPermissions(target, temp);
			if (link(target, temp)) {
				Files.delete(temp);
				return Method.LINKED;
			}
			try {
				Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
			}
			return Method.MOVED;
		} catch (IOException | RuntimeException e) {
			// Unless kept, as another program's save won
			if (e.getMessage() == null || !e.getMessage().contains(temp.getFileName().toString())) {
				Files.deleteIfExists(temp);
			}
			throw e;
		}
	}

	/**
	 * Unlink the file and link the temporary file as it, if the file system
	 * can: then check it is ours
	 *
	 * @return Whether it was linked; false if hard links aren't supported
	 */
	private static boolean link(Path target, Path temp) throws IOException {
		if (!Files.getFileStore(temp).supportsFileAttributeView(PosixFileAttributeView.class)) {
			return false;
		}
		Files.deleteIfExists(target);
		try {
			Files.createLink(target, temp);
		} catch (UnsupportedOperationException e) {
			return false;
		} catch (FileSystemException e) {
			if (Files.exists(target)) {
				throw new IOException("Another program saved " + target + " at the same time; this save is kept as "
						+ temp.getFileName(), e);
			}
			return false;
		}
		if (!Files.isSameFile(target, temp)) {
			throw new IOException("Another program saved " + target + " at the same time; this save is kept as "
					+ temp.getFileName());
		}
		return true;
	}

	/** The file's permissions, if any, for the temporary file taking its place */
	private static void keepPermissions(Path target, Path temp) {
		try {
			if (Files.exists(target)
					&& Files.getFileStore(target).supportsFileAttributeView(PosixFileAttributeView.class)) {
				Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(target);
				Files.setPosixFilePermissions(temp, permissions);
			}
		} catch (IOException | UnsupportedOperationException e) {
			// As made, then
		}
	}
}
