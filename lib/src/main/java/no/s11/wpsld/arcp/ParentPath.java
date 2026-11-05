package no.s11.wpsld.arcp;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

import no.s11.wpsld.util.Value;

public class ParentPath implements Value<Optional<Path>> {

    private ARCPFileSystem filesystem;
    private URI uri;

    public ParentPath(ARCPFileSystem filesystem, URI uri) {
        this.filesystem = filesystem;
        this.uri = uri;
        if (uri.getRawPath() == null) {
            throw new IllegalArgumentException(
                String.format("URI must include a path: %s", uri));
        }
    }

    @Override
    public Optional<Path> get() {
        if (uri.getRawPath().equals("/")) {
            // Root is nameless
            return Optional.empty();
        }
        URI directory = uri.resolve("./");
        if (! directory.equals(uri)) {
            return Optional.of(new ARCPPath(filesystem, directory)); // We were a file
        } else { 
            URI parent = uri.resolve("../");
            return Optional.of(new ARCPPath(filesystem, parent));
        }
    }

}
