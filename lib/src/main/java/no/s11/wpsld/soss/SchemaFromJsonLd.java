package no.s11.wpsld.soss;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SchemaFromJsonLd {
	/**
	 * Pinned schema.org release, as used by the RO-Crate 1.3 JSON-LD context,
	 * with http://schema.org/ IRIs like that context. Later JSON-LD releases
	 * are not guaranteed to keep the same JSON structure.
	 * <p>
	 * Source: https://raw.githubusercontent.com/schemaorg/schemaorg/main/data/releases/30.0/schemaorg-all-http.jsonld
	 */
	public static final String SCHEMAORG_VERSION = "30.0";
	public static final String SCHEMAORG_JSONLD = "schemaorg-30.0-all-http.jsonld";
	public static final String SCHEMAORG_JSONLD_SHA256 = "6ec3618c91764067d309d112ab4d0b39681361c5bee3c337c926c94f0bd632bc";

	private static final URI RDF = URI.create("http://www.w3.org/1999/02/22-rdf-syntax-ns#");
	private static final URI RDF_PROPERTY = URI.create(RDF + "Property");
	private static final URI RDF_TYPE = URI.create(RDF + "type");

	private static final URI RDFS = URI.create("http://www.w3.org/2000/01/rdf-schema#");
	private static final URI RDFS_CLASS = URI.create(RDFS + "Class");
	private static final URI RDFS_SUBCLASSOF = URI.create(RDFS + "subClassOf");
	private static final URI RDFS_SUBPROPERTYOF = URI.create(RDFS + "subPropertyOf");
	private static final URI RDFS_LABEL = URI.create(RDFS + "label");
	private static final URI RDFS_COMMENT = URI.create(RDFS + "comment");

	private static final URI S = URI.create("http://schema.org/");
	private static final URI S_DOMAIN_INCLUDES = URI.create(S + "domainIncludes");
	private static final URI S_RANGE_INCLUDES = URI.create(S + "rangeIncludes");
	private static final URI S_NAME = URI.create(S + "name");
	private static final URI S_DESCRIPTION = URI.create(S + "description");

	/** Prefixes from the @context of the schema.org JSON-LD */
	private final Map<String, String> prefixes = new LinkedHashMap<>();
	/** Nodes of the flat @graph, as subject -> property -> values */
	private final Map<URI, Map<URI, List<JsonNode>>> graph;
	private final Map<URI, ClassDef> classes;
	private final Map<URI, PropertyDef> properties;

	public SchemaFromJsonLd() {
		JsonNode jsonld;
		try (InputStream in = getClass().getResourceAsStream(SCHEMAORG_JSONLD)) {
			if (in == null) {
				throw new IllegalStateException("Can't find resource " + SCHEMAORG_JSONLD);
			}
			jsonld = new ObjectMapper().readTree(in);
		} catch (IOException e) {
			throw new UncheckedIOException("Can't read " + SCHEMAORG_JSONLD, e);
		}
		// schema.org's own @context is a plain prefix map
		jsonld.get("@context").properties().forEach(e -> prefixes.put(e.getKey(), e.getValue().asText()));
		this.graph = StreamSupport.stream(jsonld.get("@graph").spliterator(), false)
				.collect(Collectors.toUnmodifiableMap(node -> expand(node.get("@id").asText()), this::propertyValues));
		this.classes = classes().collect(Collectors.toUnmodifiableMap(Function.identity(), this::classDef));
		this.properties = properties().collect(Collectors.toUnmodifiableMap(Function.identity(), this::propertyDef));
		qualityCheck();
	}

	/**
	 * Expand a compact URI like <code>schema:Thing</code> using the
	 * prefixes of the JSON-LD context.
	 */
	private URI expand(String compact) {
		int colon = compact.indexOf(':');
		if (colon > 0 && prefixes.containsKey(compact.substring(0, colon))) {
			return URI.create(prefixes.get(compact.substring(0, colon)) + compact.substring(colon + 1));
		}
		return URI.create(compact);
	}

	private Map<URI, List<JsonNode>> propertyValues(JsonNode node) {
		Map<URI, List<JsonNode>> values = new LinkedHashMap<>();
		node.properties().forEach(e -> {
			if (e.getKey().equals("@id")) {
				return;
			}
			URI property = e.getKey().equals("@type") ? RDF_TYPE : expand(e.getKey());
			JsonNode value = e.getValue();
			values.put(property, value.isArray() ? StreamSupport.stream(value.spliterator(), false).toList()
					: List.of(value));
		});
		return values;
	}

	public Map<URI, ClassDef> getClasses() {
		return classes;
	}

	public Map<URI, PropertyDef> getProperties() {
		return properties;
	}

	private void qualityCheck() {
		classes.forEach(this::qaClass);
		properties.forEach(this::qaProperty);
	}

	private PropertyDef propertyDef(URI iri) {
		Optional<String> label = label(iri).or(() -> name(iri));
		Optional<String> comment = comment(iri).or(() -> description(iri));
		return new PropertyDef(iri, label, comment, superProperties(iri), domainIncludes(iri), rangeIncludes(iri));
	}

	private ClassDef classDef(URI iri) {
		Optional<String> label = label(iri).or(() -> name(iri));
		Optional<String> comment = comment(iri).or(() -> description(iri));
		return new ClassDef(iri, label, comment, superClasses(iri));
	}

	private Optional<String> name(URI iri) {
		return literal(iri, S_NAME);
	}

	private Optional<String> description(URI iri) {
		return literal(iri, S_DESCRIPTION);
	}

	private Stream<ClassDef> rangeIncludes(URI iri) {
		return asClassDef(objects(iri, S_RANGE_INCLUDES));
	}

	private Stream<ClassDef> domainIncludes(URI iri) {
		return asClassDef(objects(iri, S_DOMAIN_INCLUDES));
	}

	private Stream<ClassDef> superClasses(URI iri) {
		return asClassDef(objects(iri, RDFS_SUBCLASSOF));
	}

	private Stream<PropertyDef> superProperties(URI iri) {
		return asPropertyDef(objects(iri, RDFS_SUBPROPERTYOF));
	}

	private Stream<ClassDef> asClassDef(Stream<URI> iris) {
		return iris.map(this::getClassDef)
				.sorted((a,b) -> a.getName().compareTo(b.getName()));
	}

	private Stream<PropertyDef> asPropertyDef(Stream<URI> iris) {
		return iris.map(this::getPropertyDef)
				.sorted((a,b) -> a.getName().compareTo(b.getName()));
	}

	private Optional<String> label(URI iri) {
		return literal(iri, RDFS_LABEL);
	}

	private Optional<String> comment(URI iri) {
		return literal(iri, RDFS_COMMENT);
	}

	private Stream<JsonNode> values(URI subject, URI property) {
		return graph.getOrDefault(subject, Map.of()).getOrDefault(property, List.of()).stream();
	}

	private Optional<String> literal(URI subject, URI property) {
		// Plain strings, or {"@language": "en", "@value": ".."}
		return values(subject, property)
				.map(v -> v.isTextual() ? v.asText() : v.path("@value").asText(null))
				.filter(v -> v != null).findFirst();
	}

	private Stream<URI> objects(URI subject, URI property) {
		// {"@id": ".."}, or plain compact IRIs for @type
		return values(subject, property)
				.map(v -> property.equals(RDF_TYPE) ? v.asText(null) : v.path("@id").asText(null))
				.filter(v -> v != null).map(this::expand);
	}

	private Stream<URI> subjects(URI property, URI object) {
		return graph.keySet().stream().filter(subject -> objects(subject, property).anyMatch(object::equals));
	}

	private Stream<URI> classes() {
		return subjects(RDF_TYPE, RDFS_CLASS);
	}

	public Stream<ClassDef> rootClasses() {
		return asClassDef(classes().filter(Predicate.not(iri -> objects(iri, RDFS_SUBCLASSOF).findAny().isPresent())));
	}

	private Stream<URI> properties() {
		return subjects(RDF_TYPE, RDF_PROPERTY);
	}

	private void printHierarchy(ClassDef classDef, String indent, Set<URI> seen) {
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
			schema.printHierarchy(root, "", new HashSet<URI>());
		});

	}

	private void qaClass(URI iri, ClassDef classDef) {
		if (!iri.equals(classDef.getID())) {
			throw new IllegalStateException("Expected ID " + iri + " in " + classDef);
		}
		classDef.getSubClassOf().forEach(superClassDef -> {
			URI superClass = superClassDef.getID();
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

	private void qaProperty(URI iri, PropertyDef propertydef) {
		if (!iri.equals(propertydef.getID())) {
			throw new IllegalStateException("Expected ID " + iri + " in " + propertydef);
		}

		for (PropertyDef propDef : propertydef.getSubPropertyOf()) {
			URI property = propDef.getID();
			if (property.equals(RDF_PROPERTY) || property.equals(RDFS_LABEL) || property.equals(RDF_TYPE)) {
				return; // Not defined by schema.org, but used structurally in SoSS
			}
			if (!properties.containsKey(property)) {
				System.err.println(
						"Can't find property " + property + " from subPropertyOf in " + propertydef);
			}
		}

		for (ClassDef domainDef : propertydef.getDomainIncludes()) {
			URI domain = domainDef.getID();
			if (!classes.containsKey(domain)) {
				System.err.println(
						"Can't find class " + domain + " from domainIncludes in " + propertydef);
			}
			// TODO: Ensure domains are subclasses of Thing (type) and not Datatype
		}
		for (ClassDef rangeDef : propertydef.getRangeIncludes()) {
			URI range = rangeDef.getID();
			if (!classes.containsKey(range)) {
				throw new IllegalStateException("Can't find class " + range + " from rangeIncludes in " + propertydef);
			}
			// Note: Range may go to both a type and datatype
		}
	}

	private ClassDef getClassDef(URI iri) {
		if (classes == null || !classes.containsKey(iri)) {
			return new ClassDef(iri);
		} else {
			return classes.get(iri);
		}
	}

	private <R> PropertyDef getPropertyDef(URI iri) {
		if (properties == null || !properties.containsKey(iri)) {
			return new PropertyDef(iri);
		} else {
			return properties.get(iri);
		}
	}
}
