package no.s11.wpsld.soss;

import java.util.Objects;
import java.util.Optional;

import java.net.URI;

public class DefinedTerm {
	private final URI id;
	private final String name;
	private final Optional<String> comment;


	public DefinedTerm(URI id, Optional<String> label, Optional<String> comment) {
		this.id = Objects.requireNonNull(id);
		this.name = label.orElse(id.toString());
		this.comment = comment;		
	}
	
	public URI getID() {
		return id;
	}

	public String getName() {
		return name;
	}
	
	public Optional<String> getComment() {
		return comment;
	}

	@Override
	public String toString() {
		return "DefinedTerm " + id;
	}

}