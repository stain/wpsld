package no.s11.wpsld.soss;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
	private Map<IRI, ClassDef> classes;
	private Map<IRI, PropertyDef> properties;

	public SchemaFromJsonLd() {
		System.out.println("Initialising RDF parsers");
		JenaCommonsRDF.fromJena(RDFDataMgr.loadGraph(getClass().getResource("empty.ttl").toExternalForm(), Lang.TURTLE));

		// Note: NQ/Turtle loads much faster than JSON-LD
		// Source: https://schema.org/version/29.2/schemaorg-current-http.ttl
		
		URL url = getClass().getResource("schemaorg-29.2-http.ttl");
		System.out.println("Parsing " + url);
		graph = JenaCommonsRDF.fromJena(RDFDataMgr.loadGraph(url.toExternalForm(), Lang.TURTLE));
		System.out.println("Loaded schema.org types");
		this.classes = classes().collect(Collectors.toUnmodifiableMap(Function.identity(), this::classDef));
		System.out.println("Identified classes");
		this.properties = properties().collect(Collectors.toUnmodifiableMap(Function.identity(), this::propertyDef));
		System.out.println("Identified properties");
		qualityCheck();
		System.out.println("QA complete");

	}

	private void qualityCheck() {
		classes.forEach(this::qaClass);
		properties.forEach(this::qaProperty);
	}

	private PropertyDef propertyDef(IRI iri) {
		Optional<Literal> label = label(iri).or(() -> name(iri));
		Optional<Literal> comment = comment(iri).or(() -> description(iri));
		return new PropertyDef(iri, label, comment, superProperties(iri), domainIncludes(iri), rangeIncludes(iri));
	}

	private ClassDef classDef(IRI iri) {
		Optional<Literal> label = label(iri).or(() -> name(iri));
		Optional<Literal> comment = comment(iri).or(() -> description(iri));
		return new ClassDef(iri, label, comment, superClasses(iri));
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

	private List<IRI> superProperties(IRI iri) {
		return objects(iri, RDFS_SUBPROPERTYOF);
	}

	private Optional<Literal> label(IRI iri) {
		return literal(iri, RDFS_LABEL);
	}

	private Optional<Literal> comment(IRI iri) {
		return literal(iri, RDFS_COMMENT);
	}

	private Optional<Literal> literal(IRI subject, IRI property) {
		return graph.stream(subject, property, null).map(t -> t.getObject()).filter(Literal.class::isInstance)
				.map(Literal.class::cast).findAny();
	}

	private List<IRI> objects(IRI subject, IRI property) {
		return graph.stream(subject, property, null).map(t -> t.getObject()).filter(IRI.class::isInstance)
				.map(IRI.class::cast).toList();
	}

	private Stream<IRI> classes() {
		return graph.stream(null, RDF_TYPE, RDFS_CLASS).map(t -> t.getSubject()).filter(IRI.class::isInstance)
				.map(IRI.class::cast);
	}

	private Stream<IRI> properties() {
		return graph.stream(null, RDF_TYPE, RDF_PROPERTY).map(t -> t.getSubject()).filter(IRI.class::isInstance)
				.map(IRI.class::cast);
	}

	public static void main(String[] args) {
		new SchemaFromJsonLd();
	}

	private void qaClass(IRI iri, ClassDef classDef) {
		if (!iri.equals(classDef.getID())) {
			throw new IllegalStateException("Expected ID " + iri + " in " + classDef);
		}
		classDef.getSubClassOf().forEach(superClass -> {
			if (superClass.equals(RDFS_CLASS)) {
				return; // Not defined by schema.org, but used structurally in SoSS
			}
			if (!classes.containsKey(superClass)) {
				throw new IllegalStateException("Can't find superclass " + superClass + " for " + classDef);
			}
		});
	}

	private void qaProperty(IRI iri, PropertyDef propertydef) {
		if (! iri.equals(propertydef.getID())) { 
			throw new IllegalStateException("Expected ID " + iri + " in " + propertydef);
		}
		for (IRI superClass : propertydef.getSubPropertyOf()) {
			if (superClass.equals(RDF_PROPERTY) || superClass.equals(RDFS_LABEL) || superClass.equals(RDF_TYPE)) {
				return; // Not defined by schema.org, but used structurally in SoSS
			}
			if (! properties.containsKey(superClass)) {
				throw new IllegalStateException("Can't find property " + superClass + " from subPropertyOf in " + propertydef);
			}
		}
		for (IRI domain : propertydef.getDomainIncludes()) { 
			if (! classes.containsKey(domain)) {
				throw new IllegalStateException("Can't find class " + domain + " from domainIncludes in " + propertydef);
			}
			// TODO: Ensure domains are subclasses of Thing (type) and not Datatype
		}
		for (IRI range : propertydef.getRangeIncludes()) { 
			if (! classes.containsKey(range)) {
				throw new IllegalStateException("Can't find class " + range + " from rangeIncludes in " + propertydef);
			}
			// Note: Range may go to both a type and datatype
		}

	}
}
