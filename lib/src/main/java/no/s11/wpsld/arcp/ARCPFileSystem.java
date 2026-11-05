/*
 * Copyright 2023 The University of Manchester, UK
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
package no.s11.wpsld.arcp;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Set;

public class ARCPFileSystem extends FileSystem {

    private URI root;

    ARCPFileSystem(URI root) {
        this.root = root;
    }

    public URI getRootURI() {
        return this.root;
    }

    @Override
    public FileSystemProvider provider() {
        return new ARCPFileSystemProvider();
    }

    @Override
    public void close() throws IOException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'close'");
    }

    @Override
    public boolean isOpen() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isOpen'");
    }

    @Override
    public boolean isReadOnly() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isReadOnly'");
    }

    @Override
    public String getSeparator() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getSeparator'");
    }

    @Override
    public Iterable<Path> getRootDirectories() {        
        return new RootDirectories(this, this.root);
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        return new FileStores(this, this.root);
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return new SupportedFileAttributeViews();
    }

    @Override
    public Path getPath(String first, String... more) {
        ARCPPath firstPath = new ARCPPath(this, first);
        if (more.length == 0) {
            return firstPath;
        }
        String joinedPath = String.join("/", more);
        return firstPath.resolve(joinedPath);
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        throw new UnsupportedOperationException("Unimplemented method 'getPathMatcher'");
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException("Unimplemented method 'getUserPrincipalLookupService'");
    }

    @Override
    public WatchService newWatchService() throws IOException {
        throw new UnsupportedOperationException("Unimplemented method 'newWatchService'");
    }
    
}
