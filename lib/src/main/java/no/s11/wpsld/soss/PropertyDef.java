package no.s11.wpsld.soss;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Literal;

public class PropertyDef extends DefinedTerm {

	private List<ClassDef> domainIncludes;
	private List<ClassDef> rangeIncludes;
	private List<PropertyDef> subPropertyOf;
	private final Stream<PropertyDef> subPropertyOfFutures;
	private final Stream<ClassDef> domainIncludesFutures;
	private final Stream<ClassDef> rangeIncludesFutures;

	PropertyDef(IRI id) { 
		this(id, Optional.empty(), Optional.empty(), Stream.empty(), Stream.empty(), Stream.empty());
	}
	
	PropertyDef(IRI id, Optional<Literal> name, Optional<Literal> comment,  Stream<PropertyDef> subPropertyOf, 
			Stream<ClassDef> domainIncludes,  Stream<ClassDef> rangeIncludes) {
		super(id, name, comment);
		this.subPropertyOfFutures = subPropertyOf;
		this.domainIncludesFutures = domainIncludes;
		this.rangeIncludesFutures = rangeIncludes;
	}

	public List<ClassDef> getDomainIncludes() {
		if (domainIncludes == null) {
			synchronized (domainIncludesFutures) {
				if (domainIncludes == null) { 
					// Ensure we only process the stream once
					domainIncludes = domainIncludesFutures.toList();
				}
			}
		}
		return domainIncludes;
	}

	public List<ClassDef> getRangeIncludes() {
		if (rangeIncludes == null) {
			synchronized (rangeIncludesFutures) {
				if (rangeIncludes == null) { 
					// Ensure we only process the stream once
					rangeIncludes = rangeIncludesFutures.toList();
				}
			}
		}
		return rangeIncludes;
	}

	public List<PropertyDef> getSubPropertyOf() {
		if (subPropertyOf == null) {
			synchronized (subPropertyOfFutures) {
				if (subPropertyOf == null) { 
					// Ensure we only process the stream once
					subPropertyOf = subPropertyOfFutures.toList();
				}
			}
		}
		return subPropertyOf;
	}
	
	@Override
	public String toString() {
		return "Property " + getName();
	}
	
	

}
