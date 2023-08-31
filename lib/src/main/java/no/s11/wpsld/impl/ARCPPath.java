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
package no.s11.wpsld.impl;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

import no.s11.wpsld.WPSLDPath;

/**
 * Path using the arcp:// URI scheme from a base directory.
 * <p>
 * This path functions as an alias of another path that 
 * is promoted into a root directory. This means that relative
 * paths like ".." and symbolic links can't resolve
 * outside the given root directory. 
 */
public final class ARCPPath implements Path,WPSLDPath {
    private ARCPFileSystem filesystem;
    private String path;
    private URI uri;

    ARCPPath(ARCPFileSystem fs) {
        this.filesystem = fs;
        this.path = "/";
        this.uri = fs.getRootDirectories().iterator().next().toUri();
    }

    ARCPPath(ARCPFileSystem fs, URI uri) {
        this.filesystem = fs;
        this.uri = uri;
        this.path = uri.getPath();
    }


    ARCPPath(ARCPFileSystem fs, String path) {
        this.filesystem = fs;
        this.path = path;
        // FIXME: Check relative/absolute
        this.uri = new URI(null, path);
    }

    public ARCPPath getPath() { 
        return this;
    }

    @Override
    public FileSystem getFileSystem() {
       return filesystem;
    }

    @Override
    public boolean isAbsolute() {
        return this.path.startsWith("/");
    }

    @Override
    public Path getRoot() {
        return new ARCPPath(filesystem);
    }

    @Override
    public Path getFileName() {
        URI filename = uri.resolve("./").relativize(uri);
        return new ARCPPath(filesystem, filename);
    }

    @Override
    public Path getParent() {
        URI directory = uri.resolve("./");
        if (! directory.equals(uri)) {
            return new ARCPPath(filesystem, directory); // We were a file
        } else { 
            URI parent = uri.resolve("../");
            return new ARCPPath(filesystem, parent);
        }
    }

    @Override
    public int getNameCount() {
        if (this.path.equals("/")) {
            return 0;
        }
        String[] split = this.path.split("/");
        if (isAbsolute()) {
            return split.length - 1;
        } else { 
            return split.length;
        }
    }

    @Override
    public Path getName(int index) {
        String[] split = this.path.split("/");
        if (isAbsolute()) {
            // There's nothing before first /
            index++;
        }
        if (index < 0 || index > split.length-1) {
            throw new IllegalArgumentException("Invalid path index");
        }
        return new ARCPPath(filesystem, split[index]);
    }

    @Override
    public Path subpath(int beginIndex, int endIndex) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'subpath'");
    }

    @Override
    public boolean startsWith(Path other) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'startsWith'");
    }

    @Override
    public boolean endsWith(Path other) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'endsWith'");
    }

    @Override
    public Path normalize() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'normalize'");
    }

    @Override
    public Path resolve(Path other) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'resolve'");
    }

    @Override
    public Path relativize(Path other) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'relativize'");
    }

    @Override
    public URI toUri() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'toUri'");
    }

    @Override
    public Path toAbsolutePath() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'toAbsolutePath'");
    }

    @Override
    public Path toRealPath(LinkOption... options) throws IOException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'toRealPath'");
    }

    @Override
    public WatchKey register(WatchService watcher, Kind<?>[] events, Modifier... modifiers) throws IOException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'register'");
    }

    @Override
    public int compareTo(Path other) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'compareTo'");
    }
    
}