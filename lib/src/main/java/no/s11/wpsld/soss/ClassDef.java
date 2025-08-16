package no.s11.wpsld.soss;

import java.util.List;
import java.util.Optional;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Literal;

public class ClassDef extends DefinedTerm {

	private final Optional<Literal> comment;
	private final List<IRI> subClassOf;
	private final List<IRI> domainIncludes;
	private final List<IRI> rangeIncludes;
	
	public ClassDef(IRI iri, Optional<Literal> label, Optional<Literal> comment, List<IRI> subClassOf,
			List<IRI> domainIncludes, List<IRI> rangeIncludes) {
		super(iri, label.map(Literal::getLexicalForm).orElse(iri.getIRIString()));
		this.comment = comment;
		this.subClassOf = subClassOf;
		this.domainIncludes = domainIncludes;
		this.rangeIncludes = rangeIncludes;
	}
	
	public Optional<Literal> getComment() {
		return comment;
	}

	public List<IRI> getSubClassOf() {
		return subClassOf;
	}

	public List<IRI> getDomainIncludes() {
		return domainIncludes;
	}

	public List<IRI> getRangeIncludes() {
		return rangeIncludes;
	}

}
