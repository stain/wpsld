package no.s11.wpsld.soss;

import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Literal;
import org.apache.jena.commonsrdf.JenaCommonsRDF;
import org.apache.jena.commonsrdf.JenaRDF;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

public class SchemaFromJsonLd {
	private static final JenaRDF JENA = new JenaRDF();
	private static final IRI RDF = JENA.createIRI("http://www.w3.org/1999/02/22-rdf-syntax-ns#");
	private static final IRI RDF_PROPERTY = JENA.createIRI(RDF.getIRIString() + "Property");	
	private static final IRI RDF_TYPE = JENA.createIRI(RDF.getIRIString() + "type");	

	private static final IRI RDFS = JENA.createIRI("http://www.w3.org/2000/01/rdf-schema#");
	private static final IRI RDFS_CLASS = JENA.createIRI(RDFS.getIRIString() + "Class");
	private static final IRI RDFS_SUBCLASSOF = JENA.createIRI(RDFS.getIRIString() + "subClassOf");
	private static final IRI RDFS_SUBPROPERTYOF = JENA.createIRI(RDFS.getIRIString() + "subPropertyOf");
	private static final IRI RDFS_LABEL = JENA.createIRI(RDFS.getIRIString() + "label");
	private static final IRI RDFS_COMMENT = JENA.createIRI(RDFS.getIRIString() + "comment");

	private static final IRI S = JENA.createIRI("http://www.schema.org/");
	private static final IRI S_DOMAIN_INCLUDES = JENA.createIRI(S.getIRIString() + "domainIncludes");
	private static final IRI S_RANGE_INCLUDES = JENA.createIRI(S.getIRIString() + "rangeIncludes");
	private static final IRI S_NAME = JENA.createIRI(S.getIRIString() + "name");
	private static final IRI S_DESCRIPTION = JENA.createIRI(S.getIRIString() + "description");
	
	private org.apache.commons.rdf.api.Graph graph;

	
	public SchemaFromJsonLd() {
		// Note: NQ/Turtle loads much faster than JSON-LD
		// Source: https://schema.org/version/29.2/schemaorg-current-http.nq
		
		URL url = getClass().getResource("schemaorg-29.2-http.ttl");
		System.out.println(url);
		graph = JenaCommonsRDF.fromJena(RDFDataMgr.loadGraph(url.toExternalForm(), Lang.TURTLE));
		System.out.println("Loaded schema.org types");
		for (IRI iri : classes()) {
			
			Optional<Literal> label = label(iri)
						.or(() -> name(iri));
			Optional<Literal> comment = comment(iri)
					.or(() -> description(iri));
			List<IRI> subClassOf = superClasses(iri);
			List<IRI> domainIncludes = domainIncludes(iri);
			List<IRI> rangeIncludes = rangeIncludes(iri);
			ClassDef classDef = new ClassDef(iri, label, comment, subClassOf, domainIncludes, rangeIncludes);
			
		}
		System.out.println("Identified classes");
	}

	private Optional<Literal> name(IRI iri) {
		return literal(iri, S_NAME);
	}

	private Optional<Literal> description(IRI iri) {
		return literal(iri, S_DESCRIPTION);
	}
	
	private List<IRI> rangeIncludes(IRI iri) {
		return objects(iri, S_RANGE_INCLUDES);
	}

	private List<IRI> domainIncludes(IRI iri) {
		return objects(iri, S_DOMAIN_INCLUDES);		
	}

	private List<IRI> superClasses(IRI iri) {
		return objects(iri, RDFS_SUBCLASSOF);
	}

	private Optional<Literal> label(IRI iri) {
		return literal(iri, RDFS_LABEL);
	}

	private Optional<Literal> comment(IRI iri) {
		return literal(iri, RDFS_COMMENT);
	}

	private Optional<Literal> literal(IRI subject, IRI property) {
		return graph.stream(subject, property, null)
				.map(t -> t.getObject())
				.filter(Literal.class::isInstance)
				.map(Literal.class::cast)
				.findAny();
	}

	private List<IRI> objects(IRI subject, IRI property) {
		return graph.stream(subject, property, null)
				.map(t -> t.getObject())
				.filter(IRI.class::isInstance)
				.map(IRI.class::cast)
				.toList();
	}

	
	private List<IRI> classes() {
		List<IRI> classes = graph.stream(null, RDF_TYPE, RDFS_CLASS)
				.map(t -> t.getSubject())
				.filter(IRI.class::isInstance)
				.map(IRI.class::cast)
				.toList();
		return classes;
	}

	public static void main(String[] args) {
		new SchemaFromJsonLd();
	}
}
