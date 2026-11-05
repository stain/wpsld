package no.s11.wpsld.soss;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import no.s11.wpsld.util.Value;

public class PropertyDef extends DefinedTerm {

	private final Value<List<ClassDef>> domainIncludes;
	private final Value<List<ClassDef>> rangeIncludes;
	private final Value<List<PropertyDef>> subPropertyOf;

	PropertyDef(URI id) { 
		this(id, Optional.empty(), Optional.empty(), List::of, List::of, List::of);
	}
	
	PropertyDef(URI id, Optional<String> name, Optional<String> comment,  Supplier<List<PropertyDef>> subPropertyOf, 
			Supplier<List<ClassDef>> domainIncludes,  Supplier<List<ClassDef>> rangeIncludes) {
		super(id, name, comment);
		this.subPropertyOf = Value.lazy(subPropertyOf);
		this.domainIncludes = Value.lazy(domainIncludes);
		this.rangeIncludes = Value.lazy(rangeIncludes);
	}

	public List<ClassDef> getDomainIncludes() {
		return domainIncludes.get();
	}

	public List<ClassDef> getRangeIncludes() {
		return rangeIncludes.get();
	}

	public List<PropertyDef> getSubPropertyOf() {
		return subPropertyOf.get();
	}
	
	@Override
	public String toString() {
		return "Property " + getName();
	}
	
	

}
