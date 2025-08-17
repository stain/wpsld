package no.s11.wpsld.soss;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Literal;

public class ClassDef extends DefinedTerm {

	private final List<IRI> subClassOf;
	
	public ClassDef(IRI iri, Optional<Literal> name, Optional<Literal> comment, List<IRI> subClassOf) {
		super(iri, name, comment);
		this.subClassOf = Collections.unmodifiableList(subClassOf);
	}
	
	public List<IRI> getSubClassOf() {
		return subClassOf;
	}

	@Override
	public String toString() {
		return "Class " + getName();
	}

}
