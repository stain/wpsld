package no.s11.wpsld.soss;

import java.net.URL;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
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

	private static final IRI S = JENA.createIRI("http://schema.org/");
	private static final IRI S_DOMAIN_INCLUDES = JENA.createIRI(S.getIRIString() + "domainIncludes");
	private static final IRI S_RANGE_INCLUDES = JENA.createIRI(S.getIRIString() + "rangeIncludes");
	private static final IRI S_NAME = JENA.createIRI(S.getIRIString() + "name");
	private static final IRI S_DESCRIPTION = JENA.createIRI(S.getIRIString() + "description");

	private final org.apache.commons.rdf.api.Graph graph;
	private final Map<IRI, ClassDef> classes;
	private final Map<IRI, PropertyDef> properties;

	public SchemaFromJsonLd() {
		Lang rdfLang = Lang.TURTLE;
		try {
			JenaCommonsRDF
					.fromJena(RDFDataMgr.loadGraph(getClass().getResource("empty.ttl").toExternalForm(), rdfLang));
		} catch (RuntimeException ex) {
			throw new RuntimeException("Unable to initialise Jena, check dependencies", ex);
		}
		// Above will fail if Jena dependencies for given rdfLang are incomplete.

		// Note: NQ/Turtle loads much faster than JSON-LD
		// Source: https://schema.org/version/29.2/schemaorg-current-http.ttl

		URL url = getClass().getResource("schemaorg-29.2-http.ttl");
		graph = JenaCommonsRDF.fromJena(RDFDataMgr.loadGraph(url.toExternalForm(), rdfLang));
		this.classes = classes().collect(Collectors.toUnmodifiableMap(Function.identity(), this::classDef));
		this.properties = properties().collect(Collectors.toUnmodifiableMap(Function.identity(), this::propertyDef));
		qualityCheck();
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

	private Stream<ClassDef> rangeIncludes(IRI iri) {
		return asClassDef(objects(iri, S_RANGE_INCLUDES));
	}

	private Stream<ClassDef> domainIncludes(IRI iri) {
		return asClassDef(objects(iri, S_DOMAIN_INCLUDES));
	}

	private Stream<ClassDef> superClasses(IRI iri) {
		return asClassDef(objects(iri, RDFS_SUBCLASSOF));
	}

	private Stream<PropertyDef> superProperties(IRI iri) {
		return asPropertyDef(objects(iri, RDFS_SUBPROPERTYOF));
	}

	private Stream<ClassDef> asClassDef(Stream<IRI> iris) {
		return iris.map(this::getClassDef)
				.sorted((a,b) -> a.getName().compareTo(b.getName()));
	}

	private Stream<PropertyDef> asPropertyDef(Stream<IRI> iris) {
		return iris.map(this::getPropertyDef)
				.sorted((a,b) -> a.getName().compareTo(b.getName()));
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

	private Stream<IRI> objects(IRI subject, IRI property) {
		return graph.stream(subject, property, null).map(t -> t.getObject()).filter(IRI.class::isInstance)
				.map(IRI.class::cast);
	}

	private Stream<IRI> subjects(IRI property, IRI object) {
		return graph.stream(null, property, object).map(t -> t.getSubject()).filter(IRI.class::isInstance)
				.map(IRI.class::cast);
	}

	private Stream<IRI> classes() {
		return graph.stream(null, RDF_TYPE, RDFS_CLASS).map(t -> t.getSubject()).filter(IRI.class::isInstance)
				.map(IRI.class::cast);
	}

	public Stream<ClassDef> rootClasses() {
		return asClassDef(classes().filter(Predicate.not(iri -> graph.contains(iri, RDFS_SUBCLASSOF, null))));
	}

	private Stream<IRI> properties() {
		return graph.stream(null, RDF_TYPE, RDF_PROPERTY).map(t -> t.getSubject()).filter(IRI.class::isInstance)
				.map(IRI.class::cast);
	}

	private void printHierarchy(ClassDef classDef, String indent, Set<IRI> seen) {
		Stream<ClassDef> subs = subclasses(classDef);
		if (seen.contains(classDef.getID())) {
			if (subs.findAny().isPresent()) {
				System.out.println(indent + "+ " +  classDef.getName());
			} else {
				System.out.println(indent + "- " +  classDef.getName());				
			}
		} else {
			seen.add(classDef.getID());
			String props = asPropertyDef(subjects(S_DOMAIN_INCLUDES, classDef.getID()))
					.map(PropertyDef::getName)
					.collect(Collectors.joining(", ", "(", ")"));
			System.out.println(indent + "- " +  classDef.getName() + props);
			subs.forEach(c -> printHierarchy(c, indent + "  ", seen));
		}
	}

	private Stream<ClassDef> subclasses(ClassDef classDef) {
		return asClassDef(subjects(RDFS_SUBCLASSOF, classDef.getID()));
	}

	public static void main(String[] args) {
		SchemaFromJsonLd schema = new SchemaFromJsonLd();

		schema.rootClasses().forEach(root -> {
			schema.printHierarchy(root, "", new HashSet<IRI>());
		});

	}

	private void qaClass(IRI iri, ClassDef classDef) {
		if (!iri.equals(classDef.getID())) {
			throw new IllegalStateException("Expected ID " + iri + " in " + classDef);
		}
		classDef.getSubClassOf().forEach(superClassDef -> {
			IRI superClass = superClassDef.getID();
			if (superClass.equals(RDFS_CLASS)) {
				return; // Not defined by schema.org, but used structurally in SoSS
			}
			if (!classes.containsKey(superClass)) {
				throw new IllegalStateException("Can't find superclass " + superClass + " for " + classDef);
				// System.out.println("Can't find superclass " + superClass + " for " +
				// classDef);
			}
		});
	}

	private void qaProperty(IRI iri, PropertyDef propertydef) {
		if (!iri.equals(propertydef.getID())) {
			throw new IllegalStateException("Expected ID " + iri + " in " + propertydef);
		}

		for (PropertyDef propDef : propertydef.getSubPropertyOf()) {
			IRI property = propDef.getID();
			if (property.equals(RDF_PROPERTY) || property.equals(RDFS_LABEL) || property.equals(RDF_TYPE)) {
				return; // Not defined by schema.org, but used structurally in SoSS
			}
			if (!properties.containsKey(property)) {
				System.err.println(
						"Can't find property " + property + " from subPropertyOf in " + propertydef);
			}
		}

		for (ClassDef domainDef : propertydef.getDomainIncludes()) {
			IRI domain = domainDef.getID();
			if (!classes.containsKey(domain)) {
				System.err.println(
						"Can't find class " + domain + " from domainIncludes in " + propertydef);
			}
			// TODO: Ensure domains are subclasses of Thing (type) and not Datatype
		}
		for (ClassDef rangeDef : propertydef.getRangeIncludes()) {
			IRI range = rangeDef.getID();
			if (!classes.containsKey(range)) {
				throw new IllegalStateException("Can't find class " + range + " from rangeIncludes in " + propertydef);
			}
			// Note: Range may go to both a type and datatype
		}
	}

	private ClassDef getClassDef(IRI iri) {
		if (classes == null || !classes.containsKey(iri)) {
			return new ClassDef(iri);
		} else {
			return classes.get(iri);
		}
	}

	private <R> PropertyDef getPropertyDef(IRI iri) {
		if (properties == null || !properties.containsKey(iri)) {
			return new PropertyDef(iri);
		} else {
			return properties.get(iri);
		}
	}
}
