/*******************************************************************************
 * Copyright (c) 2015, 2016 Sebastian Stenzel and others.
 * This file is licensed under the terms of the MIT license.
 * See the LICENSE.txt file for more info.
 *
 * Contributors:
 *     Sebastian Stenzel - initial API and implementation
 *******************************************************************************/
package org.cryptomator.filesystem.inmem;

import java.io.FileNotFoundException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.FileAlreadyExistsException;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.cryptomator.filesystem.File;
import org.cryptomator.filesystem.ReadableFile;
import org.cryptomator.filesystem.WritableFile;

class InMemoryFile extends InMemoryNode implements File {

	/** 1000kb */
	static final int INITIAL_SIZE = 100 * 1024;

	/** 140% */
	static final double GROWTH_RATE = 1.4;

	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	private volatile ByteBuffer content = ByteBuffer.allocate(INITIAL_SIZE);

	public InMemoryFile(InMemoryFolder parent, String name, Instant lastModified, Instant creationTime) {
		super(parent, name, lastModified, creationTime);
		content.flip();
	}

	@Override
	public ReadableFile openReadable() {
		if (!exists()) {
			throw new UncheckedIOException(new FileNotFoundException(this.name() + " does not exist"));
		}
		final ReadLock readLock = lock.readLock();
		readLock.lock();
		return new InMemoryReadableFile(this::getContent, readLock);
	}

	@Override
	public WritableFile openWritable() {
		final WriteLock writeLock = lock.writeLock();
		writeLock.lock();
		final InMemoryFolder parent = parent().get();
		parent.existingChildren.compute(this.name(), (k, v) -> {
			if (v != null && v != this) {
				// other file or folder with same name already exists.
				throw new UncheckedIOException(new FileAlreadyExistsException(k));
			} else {
				if (v == null) {
					this.creationTime = Instant.now();
				}
				this.lastModified = Instant.now();
				return this;
			}
		});
		return new InMemoryWritableFile(this::setLastModified, this::setCreationTime, this::getContent, this::setContent, this::delete, writeLock);
	}

	private void setLastModified(Instant lastModified) {
		this.lastModified = lastModified;
	}

	private ByteBuffer getContent() {
		return content;
	}

	private void setContent(ByteBuffer content) {
		this.content = content;
	}

	private void delete(Void param) {
		final InMemoryFolder parent = parent().get();
		parent.existingChildren.computeIfPresent(this.name(), (k, v) -> {
			// returning null removes the entry.
			return null;
		});
		assert!this.exists();
	}

	@Override
	public String toString() {
		return parent.toString() + name;
	}

	@Override
	public int compareTo(File o) {
		return toString().compareTo(o.toString());
	}

}
