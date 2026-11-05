package no.s11.wpsld.arcp;

import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;

class RootDirectories implements Iterable<Path> {

    private Path rootPath;

    RootDirectories(ARCPFileSystem arcpFileSystem, URI root) {
        this.rootPath = new ARCPPath(arcpFileSystem, root);
    }

    @Override
    public Iterator<Path> iterator() {
        return Collections.singleton(rootPath).iterator();
    }

}
