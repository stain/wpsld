package no.s11.wpsld.soss;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Literal;

public class PropertyDef extends DefinedTerm {

	private final List<IRI> domainIncludes;
	private final List<IRI> rangeIncludes;
	private final List<IRI> subPropertyOf;

	public PropertyDef(IRI id, Optional<Literal> name, Optional<Literal> comment,  List<IRI> subPropertyOf, List<IRI> domainIncludes,  List<IRI> rangeIncludes) {
		super(id, name, comment);
		this.subPropertyOf = Collections.unmodifiableList(subPropertyOf);
		this.domainIncludes = Collections.unmodifiableList(domainIncludes);
		this.rangeIncludes = Collections.unmodifiableList(rangeIncludes);
	}

	public List<IRI> getDomainIncludes() {
		return domainIncludes;
	}

	public List<IRI> getRangeIncludes() {
		return rangeIncludes;
	}

	public List<IRI> getSubPropertyOf() {
		return subPropertyOf;
	}
	
	@Override
	public String toString() {
		return "Property " + getName();
	}
	
	

}
