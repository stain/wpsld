package no.s11.wpsld.arcp;

import java.net.URI;
import java.net.URISyntaxException;

import no.s11.wpsld.util.Value;

class URIFromPath implements Value<URI> {

    private URI uri;
    private URI baseURI;

    public URIFromPath(String path, URI baseURI) {
        if (! baseURI.isAbsolute()) {
            throw new IllegalArgumentException("base URI must be absolute");
        }
        this.baseURI = baseURI;
        
        if (path == null) { 
            throw new NullPointerException("Path can't be null");
        }
        try {
            // encode path, e.g. " " to "%20"
            this.uri = new URI(null, null, path, null);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(
                String.format("Invalid path %s", path));
        }
        assert this.uri.getRawPath() != null;
    }

    public URI get() {
        if (! uri.getRawPath().startsWith("/")) {
            return uri;
        } else { 
            return baseURI.resolve(uri);
        }
    }
}
