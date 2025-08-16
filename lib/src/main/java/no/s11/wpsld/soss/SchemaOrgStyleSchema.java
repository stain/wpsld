package no.s11.wpsld.soss;

import java.util.Collections;
import java.util.Set;

public class SchemaOrgStyleSchema {
	private final Set<ClassDef> classes;
	private final Set<PropertyDef> properties;

	SchemaOrgStyleSchema(Set<ClassDef> classes, Set<PropertyDef> properties) {
		this.classes = Collections.unmodifiableSet(classes);
		this.properties = Collections.unmodifiableSet(properties);
	}

	public Set<ClassDef> getClasses() {
		return classes;
	}

	public Set<PropertyDef> getProperties() {
		return properties;
	}

}
