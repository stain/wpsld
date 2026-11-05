package no.s11.wpsld.soss;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import no.s11.wpsld.util.Value;

public class ClassDef extends DefinedTerm {

	private final Value<List<ClassDef>> subClassOf;

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

	@Override
	public String toString() {
		return "Class " + getName();
	}

}
