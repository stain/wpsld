package no.s11.wpsld.arcp;

import java.net.URI;
import java.nio.file.FileStore;
import java.util.Iterator;

class FileStores implements Iterable<FileStore> {

    private ARCPFileSystem arcpFileSystem;
    private URI root;

    FileStores(ARCPFileSystem arcpFileSystem, URI root) {
        this.arcpFileSystem = arcpFileSystem;
        this.root = root;
    }

    @Override
    public Iterator<FileStore> iterator() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'iterator'");
    }

}
