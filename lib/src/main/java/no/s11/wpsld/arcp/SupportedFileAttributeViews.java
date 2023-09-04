package no.s11.wpsld.arcp;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

class SupportedFileAttributeViews extends AbstractSet<String> {

    @Override
    public int size() {
        return 0;
    }

    @Override
    public Iterator<String> iterator() {
        return Collections.emptyIterator();
    }

    @Override
    public boolean add(String e) {
        throw new UnsupportedOperationException("Unimplemented method 'add'");
    }

    @Override
    public boolean addAll(Collection<? extends String> c) {
        throw new UnsupportedOperationException("Unimplemented method 'addAll'");
    }

}
