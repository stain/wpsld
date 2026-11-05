package no.s11.wpsld.soss;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.function.Supplier;

import no.s11.wpsld.util.Value;

public class ClassDef extends DefinedTerm {

	private final Value<List<ClassDef>> subClassOf;
	private final Value<Set<ClassDef>> selfAndSuperClasses = Value.lazy(this::walkSuperClasses);

	ClassDef(URI iri) {
		this(iri, Optional.empty(), Optional.empty(), List::of);
	}

	
	public ClassDef(URI iri, Optional<String> name, Optional<String> comment, Supplier<List<ClassDef>> subClassOf) {
		super(iri, name, comment);
		this.subClassOf = Value.lazy(subClassOf);
	}
	
	public List<ClassDef> getSubClassOf() {
		return subClassOf.get();
	}

	/**
	 * This class and all its superclasses, breadth first (most specific first).
	 * Each class is included once, so this is safe also if a schema has
	 * cycles in its class hierarchy.
	 */
	public Set<ClassDef> getSelfAndSuperClasses() {
		return selfAndSuperClasses.get();
	}

	private Set<ClassDef> walkSuperClasses() {
		Set<ClassDef> classes = new LinkedHashSet<>();
		Set<URI> seen = new HashSet<>();
		Queue<ClassDef> queue = new ArrayDeque<>(List.of(this));
		while (!queue.isEmpty()) {
			ClassDef classDef = queue.poll();
			if (seen.add(classDef.getID())) {
				classes.add(classDef);
				queue.addAll(classDef.getSubClassOf());
			}
		}
		return Collections.unmodifiableSet(classes);
	}

	@Override
	public String toString() {
		return "Class " + getName();
	}

}
