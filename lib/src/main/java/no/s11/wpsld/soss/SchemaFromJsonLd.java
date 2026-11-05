package no.s11.wpsld.soss;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
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
	private static final URI S_SUPERSEDED_BY = URI.create(S + "supersededBy");
	private static final URI S_DATA_TYPE = URI.create(S + "DataType");

	/**
	 * The parsed JSON-LD, only kept while loading. Once every class and
	 * property is created and linked it is dropped, so only the model is
	 * retained (about 2.5 MB rather than 14 MB for schema.org).
	 */
	private JsonLdGraph jsonld;
	private final Map<URI, ClassDef> classes;
	private final Map<URI, PropertyDef> properties;
	private final Map<URI, List<ClassDef>> subclasses;
	private final Map<URI, List<PropertyDef>> domainProperties;
	private final Set<URI> superseded;
	/** Classes typed directly as schema:DataType */
	private final Set<URI> dataTypes;

	public SchemaFromJsonLd() {
		JsonNode tree;
		try (InputStream in = getClass().getResourceAsStream(SCHEMAORG_JSONLD)) {
			if (in == null) {
				throw new IllegalStateException("Can't find resource " + SCHEMAORG_JSONLD);
			}
			tree = new ObjectMapper().readTree(in);
		} catch (IOException e) {
			throw new UncheckedIOException("Can't read " + SCHEMAORG_JSONLD, e);
		}
		jsonld = new JsonLdGraph(tree);
		// Create all classes and properties first; their references to each
		// other are resolved lazily, so the order in the JSON-LD and any
		// circular references don't matter
		this.classes = classes().parallel().collect(Collectors.toUnmodifiableMap(Function.identity(), this::classDef));
		this.properties = properties().parallel()
				.collect(Collectors.toUnmodifiableMap(Function.identity(), this::propertyDef));
		this.superseded = jsonld.graph.keySet().parallelStream()
				.filter(iri -> jsonld.objects(iri, S_SUPERSEDED_BY).findAny().isPresent())
				.collect(Collectors.toUnmodifiableSet());
		this.dataTypes = jsonld.subjects(RDF_TYPE, S_DATA_TYPE).collect(Collectors.toUnmodifiableSet());
		link();
		this.subclasses = reverse(classes.values(), ClassDef::getSubClassOf);
		this.domainProperties = reverse(properties.values(), PropertyDef::getDomainIncludes);
		qualityCheck();
		// Everything is resolved, the JSON is no longer needed
		jsonld = null;
	}

	/**
	 * Resolve all references between classes and properties now, in
	 * parallel, as the schema won't change after loading. (Resolving one
	 * lazy list only looks up other terms, it never waits on another list.)
	 */
	private void link() {
		classes.values().parallelStream().forEach(ClassDef::getSelfAndSuperClasses);
		properties.values().parallelStream().forEach(propertyDef -> {
			propertyDef.getSubPropertyOf();
			propertyDef.getDomainIncludes();
			propertyDef.getRangeIncludes();
		});
	}

	/**
	 * Index terms by the classes they link to, e.g. subclasses by their
	 * superclass, sorted by name.
	 */
	private static <T extends DefinedTerm> Map<URI, List<T>> reverse(Collection<T> terms,
			Function<T, List<ClassDef>> links) {
		return Map.copyOf(terms.parallelStream()
				.flatMap(term -> links.apply(term).stream().map(linked -> Map.entry(linked.getID(), term)))
				.collect(Collectors.groupingByConcurrent(Map.Entry::getKey, Collectors.collectingAndThen(
						Collectors.mapping(Map.Entry::getValue, Collectors.toList()),
						list -> list.stream().sorted(Comparator.comparing(DefinedTerm::getName)).toList()))));
	}

	public Map<URI, ClassDef> getClasses() {
		return classes;
	}

	public Map<URI, PropertyDef> getProperties() {
		return properties;
	}

	/**
	 * Direct subclasses of a class.
	 */
	public Stream<ClassDef> subclasses(ClassDef classDef) {
		return subclasses.getOrDefault(classDef.getID(), List.of()).stream();
	}

	/**
	 * Properties that list the class in their <code>domainIncludes</code>,
	 * not including properties of its superclasses.
	 */
	public Stream<PropertyDef> propertiesOf(ClassDef classDef) {
		return domainProperties.getOrDefault(classDef.getID(), List.of()).stream();
	}

	/**
	 * True if schema.org has retired the term in favour of another
	 * (<code>schema:supersededBy</code>). Such terms are kept as the RO-Crate
	 * context still includes them, but should not be used in new metadata.
	 */
	public boolean isSuperseded(DefinedTerm term) {
		return superseded.contains(term.getID());
	}

	/**
	 * True if the class is a schema.org data type like Text, URL, Date or
	 * Boolean (or a subclass of one), whose values are literals rather than
	 * entities.
	 */
	public boolean isDataType(ClassDef classDef) {
		return classDef.getSelfAndSuperClasses().stream().map(ClassDef::getID).anyMatch(dataTypes::contains);
	}

	private void qualityCheck() {
		classes.forEach(this::qaClass);
		properties.forEach(this::qaProperty);
	}

	private PropertyDef propertyDef(URI iri) {
		Optional<String> label = label(iri).or(() -> name(iri));
		Optional<String> comment = comment(iri).or(() -> description(iri));
		return new PropertyDef(iri, label, comment, () -> superProperties(iri).toList(), () -> domainIncludes(iri).toList(),
				() -> rangeIncludes(iri).toList());
	}

	private ClassDef classDef(URI iri) {
		Optional<String> label = label(iri).or(() -> name(iri));
		Optional<String> comment = comment(iri).or(() -> description(iri));
		return new ClassDef(iri, label, comment, () -> superClasses(iri).toList());
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

	private Optional<String> literal(URI subject, URI property) {
		return jsonld.literal(subject, property);
	}

	private Stream<URI> objects(URI subject, URI property) {
		return jsonld.objects(subject, property);
	}

	private Stream<URI> classes() {
		return jsonld.subjects(RDF_TYPE, RDFS_CLASS);
	}

	public Stream<ClassDef> rootClasses() {
		return classes.values().stream().filter(c -> c.getSubClassOf().isEmpty())
				.sorted(Comparator.comparing(DefinedTerm::getName));
	}

	private Stream<URI> properties() {
		return jsonld.subjects(RDF_TYPE, RDF_PROPERTY);
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
			String props = propertiesOf(classDef)
					.map(PropertyDef::getName)
					.collect(Collectors.joining(", ", "(", ")"));
			System.out.println(indent + "- " +  classDef.getName() + props);
			subs.forEach(c -> printHierarchy(c, indent + "  ", seen));
		}
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
				continue; // Not defined by schema.org, but used structurally in SoSS
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

	/**
	 * The flat <code>@graph</code> of schema.org's JSON-LD, indexed for
	 * lookups while the model is built.
	 */
	private static class JsonLdGraph {
		/** Prefixes from the @context of the schema.org JSON-LD */
		private final Map<String, String> prefixes = new LinkedHashMap<>();
		/** Nodes of the flat @graph, as subject -> property -> values */
		private final Map<URI, Map<URI, List<JsonNode>>> graph;
		/** Reverse links, as property -> object -> subjects, e.g. to find classes */
		private final Map<URI, Map<URI, List<URI>>> inverse;
		/**
		 * Compact IRIs already expanded; the same few thousand terms are
		 * referenced over and over, and parsing URIs is not cheap
		 */
		private final Map<String, URI> expanded = new ConcurrentHashMap<>();

		JsonLdGraph(JsonNode jsonld) {
			// schema.org's own @context is a plain prefix map
			jsonld.get("@context").properties().forEach(e -> prefixes.put(e.getKey(), e.getValue().asText()));
			List<JsonNode> nodes = new ArrayList<>();
			jsonld.get("@graph").forEach(nodes::add);
			this.graph = nodes.parallelStream()
					.collect(Collectors.toUnmodifiableMap(node -> expand(node.get("@id").asText()), this::propertyValues));
			record Link(URI property, URI object, URI subject) {
			}
			this.inverse = graph.entrySet().parallelStream()
					.flatMap(node -> node.getValue().keySet().stream().flatMap(
							property -> objects(node.getKey(), property).map(o -> new Link(property, o, node.getKey()))))
					.collect(Collectors.groupingByConcurrent(Link::property, Collectors.groupingBy(Link::object,
							Collectors.mapping(Link::subject, Collectors.toList()))));
		}

		/**
		 * Expand a compact URI like <code>schema:Thing</code> using the
		 * prefixes of the JSON-LD context.
		 */
		private URI expand(String compact) {
			return expanded.computeIfAbsent(compact, this::expandUncached);
		}

		private URI expandUncached(String compact) {
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

		private Stream<JsonNode> values(URI subject, URI property) {
			return graph.getOrDefault(subject, Map.of()).getOrDefault(property, List.of()).stream();
		}

		Optional<String> literal(URI subject, URI property) {
			// Plain strings, or {"@language": "en", "@value": ".."}
			return values(subject, property)
					.map(v -> v.isTextual() ? v.asText() : v.path("@value").asText(null))
					.filter(v -> v != null).findFirst();
		}

		Stream<URI> objects(URI subject, URI property) {
			// {"@id": ".."}, or plain compact IRIs for @type
			return values(subject, property)
					.map(v -> property.equals(RDF_TYPE) ? v.asText(null) : v.path("@id").asText(null))
					.filter(v -> v != null).map(this::expand);
		}

		Stream<URI> subjects(URI property, URI object) {
			return inverse.getOrDefault(property, Map.of()).getOrDefault(object, List.of()).stream();
		}
	}
}
