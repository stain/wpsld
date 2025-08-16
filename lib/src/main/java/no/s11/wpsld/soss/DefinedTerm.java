package no.s11.wpsld.soss;

import org.apache.commons.rdf.api.IRI;

public class DefinedTerm {
	private final IRI id;
	private final String name;

	public DefinedTerm(IRI id, String name) {
		this.id = id;
		this.name = name;
	}
	
	public IRI getID() {
		return id;
	}

	public String getName() {
		return name;
	}

}