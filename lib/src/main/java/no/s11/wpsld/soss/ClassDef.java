package no.s11.wpsld.soss;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Literal;

public class ClassDef extends DefinedTerm {

	private final Stream<ClassDef> subClassOfFutures;
	private List<ClassDef> subClassOf = null;

	ClassDef(IRI iri) {
		this(iri, Optional.empty(), Optional.empty(), Stream.empty());
	}

	
	public ClassDef(IRI iri, Optional<Literal> name, Optional<Literal> comment, Stream<ClassDef> subClassOfFutures) {
		super(iri, name, comment);
		this.subClassOfFutures = subClassOfFutures;
	}
	
	public List<ClassDef> getSubClassOf() {
		if (subClassOf == null) {
			synchronized (subClassOfFutures) {
				if (subClassOf == null) { 
					// Ensure we only process the stream once
					subClassOf = subClassOfFutures.toList();
				}
			}
		}
		return subClassOf;
	}

	@Override
	public String toString() {
		return "Class " + getName();
	}

}
