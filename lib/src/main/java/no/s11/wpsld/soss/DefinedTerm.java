package no.s11.wpsld.soss;

import java.util.Objects;
import java.util.Optional;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Literal;

public class DefinedTerm {
	private final IRI id;
	private final String name;
	private final Optional<String> comment;


	public DefinedTerm(IRI id, Optional<Literal> label, Optional<Literal> comment) {
		this.id = Objects.requireNonNull(id);
		this.name = label.map(Literal::getLexicalForm).orElse(id.getIRIString());
		this.comment = comment.map(Literal::getLexicalForm);		
	}
	
	public IRI getID() {
		return id;
	}

	public String getName() {
		return name;
	}
	
	public Optional<String> getComment() {
		return comment;
	}



}