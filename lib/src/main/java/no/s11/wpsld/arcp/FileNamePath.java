package no.s11.wpsld.arcp;

import java.nio.file.Path;
import java.util.Optional;

import no.s11.wpsld.util.Value;

public class FileNamePath implements Value<Optional<Path>> {

    private ARCPFileSystem filesystem;
    private String path;

    public FileNamePath(ARCPFileSystem filesystem, String path) {
        this.filesystem = filesystem;
        this.path = path;
    }

    @Override
    public Optional<Path> get() {
        if (path.equals("/")) {
            // Root is nameless
            return Optional.empty();
        }
        String[] split = path.split("/");
        if (split.length < 2) {
            // No directory elements, it's just this path
            return Optional.of(new ARCPPath(filesystem, path));
        }
        if (split[split.length-1].isEmpty()) {
            // path ended in /, our name is split element before
            return Optional.of(new ARCPPath(filesystem, split[split.length-2]));
        } else { 
            // filename is at end of path
            return Optional.of(new ARCPPath(filesystem, split[split.length-1]));
        }

    }

}
