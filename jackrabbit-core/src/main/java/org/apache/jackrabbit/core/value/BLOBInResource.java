/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.core.value;

import org.apache.jackrabbit.core.fs.FileSystemException;
import org.apache.jackrabbit.core.fs.FileSystemResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

import javax.jcr.RepositoryException;

/**
 * Represents binary data which is stored in a file system resource.
 */
public class BLOBInResource extends BLOBFileValue {

    /**
     * The default logger
     */
    private static final Logger LOG = LoggerFactory.getLogger(BLOBInResource.class);

    /**
     * the prefix of the string representation of this value
     */
    private static final String PREFIX = "fsResource:";

    /**
     * underlying file system resource
     */
    private final FileSystemResource fsResource;

    /**
     * the file length
     */
    private final long length;

    /**
     * Creates a new instance from a stream.
     *
     * @param in the input stream
     * @throws IOException
     */
    private BLOBInResource(FileSystemResource fsResource) throws IOException {
        try {
            if (!fsResource.exists()) {
                throw new IOException(fsResource.getPath()
                        + ": the specified resource does not exist");
            }
            length = fsResource.length();
        } catch (FileSystemException fse) {
            throw new IOException(fsResource.getPath()
                    + ": Error while creating value: " + fse.toString());
        }
        this.fsResource = fsResource;
    }

    /**
     * Creates a new instance from a file system resource.
     *
     * @param fsResource the resource
     */
    static BLOBInResource getInstance(FileSystemResource fsResource) throws IOException {
        return new BLOBInResource(fsResource);
    }

    /**
     * {@inheritDoc}
     */
    public void delete(boolean pruneEmptyParentDirs) {
        try {
            fsResource.delete(pruneEmptyParentDirs);
        } catch (FileSystemException fse) {
            // ignore
            LOG.warn("Error while deleting BLOBFileValue: " + fse.getMessage());
        }

    }

    /**
     * {@inheritDoc}
     */
    public void discard() {
        // this instance is not backed by temporarily allocated resource/buffer
    }

    /**
     * {@inheritDoc}
     */
    public boolean isImmutable() {
        // delete will modify the state.
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public long getLength() {
        return length;
    }

    /**
     * {@inheritDoc}
     */
    public InputStream getStream() throws RepositoryException {
        try {
            return fsResource.getInputStream();
        } catch (FileSystemException fse) {
            throw new RepositoryException(fsResource.getPath()
                    + ": the specified resource does not exist", fse);
        }
    }

    /**
     * {@inheritDoc}
     */
    public String toString() {
        return PREFIX +  fsResource.toString();
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof BLOBInResource) {
            BLOBInResource other = (BLOBInResource) obj;
            return length == other.length && fsResource.equals(other.fsResource);
        }
        return false;
    }

    /**
     * Returns zero to satisfy the Object equals/hashCode contract.
     * This class is mutable and not meant to be used as a hash key.
     *
     * @return always zero
     * @see Object#hashCode()
     */
    public int hashCode() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isSmall() {
        return false;
    }

}
