/*
 * Copyright (c) 2006-2012 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Bogdan Stefanescu
 *     Wojciech Sulejman
 *     Florent Guillaume
 *     Thierry Delprat
 */
package org.nuxeo.ecm.core.schema;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.schema.types.ComplexType;
import org.nuxeo.ecm.core.schema.types.ComplexTypeImpl;
import org.nuxeo.ecm.core.schema.types.Constraint;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.ListType;
import org.nuxeo.ecm.core.schema.types.ListTypeImpl;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.ecm.core.schema.types.SchemaImpl;
import org.nuxeo.ecm.core.schema.types.SimpleType;
import org.nuxeo.ecm.core.schema.types.SimpleTypeImpl;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.core.schema.types.TypeBindingException;
import org.nuxeo.ecm.core.schema.types.TypeException;
import org.nuxeo.ecm.core.schema.types.constraints.EnumConstraint;
import org.nuxeo.ecm.core.schema.types.constraints.StringLengthConstraint;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.sun.xml.xsom.XSAttributeDecl;
import com.sun.xml.xsom.XSAttributeUse;
import com.sun.xml.xsom.XSComplexType;
import com.sun.xml.xsom.XSContentType;
import com.sun.xml.xsom.XSElementDecl;
import com.sun.xml.xsom.XSFacet;
import com.sun.xml.xsom.XSListSimpleType;
import com.sun.xml.xsom.XSModelGroup;
import com.sun.xml.xsom.XSParticle;
import com.sun.xml.xsom.XSSchema;
import com.sun.xml.xsom.XSSchemaSet;
import com.sun.xml.xsom.XSTerm;
import com.sun.xml.xsom.XSType;
import com.sun.xml.xsom.XmlString;
import com.sun.xml.xsom.impl.RestrictionSimpleTypeImpl;
import com.sun.xml.xsom.parser.XSOMParser;

/**
 * Loader of XSD schemas into Nuxeo Schema objects.
 */
public class XSDLoader {

    private static final Log log = LogFactory.getLog(XSDLoader.class);

    private static final String ANONYMOUS_TYPE_SUFFIX = "#anonymousType";

    private static final String NS_XSD = "http://www.w3.org/2001/XMLSchema";

    protected final SchemaManagerImpl schemaManager;

    public XSDLoader(SchemaManagerImpl schemaManager) {
        this.schemaManager = schemaManager;
    }

    protected Schema getSchema(String name) {
        return schemaManager.getSchemaInternal(name);
    }

    protected void registerSchema(Schema schema) {
        schemaManager.registerSchema(schema);
    }

    protected Type getType(String name) {
        return schemaManager.getType(name);
    }

    protected XSOMParser getParser() {
        XSOMParser parser = new XSOMParser();
        ErrorHandler errorHandler = new SchemaErrorHandler();
        parser.setErrorHandler(errorHandler);
        return parser;
    }

    protected static class SchemaErrorHandler implements ErrorHandler {
        @Override
        public void error(SAXParseException e) throws SAXException {
            log.error("Error: " + e.getMessage());
            throw e;
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            log.error("FatalError: " + e.getMessage());
            throw e;
        }

        @Override
        public void warning(SAXParseException e) throws SAXException {
            log.error("Warning: " + e.getMessage());
        }
    }

    // called by SchemaManagerImpl
    public Schema loadSchema(String name, String prefix, File file,
            boolean override) throws SAXException, IOException, TypeException {
        XSOMParser parser = getParser();
        String systemId = file.toURI().toURL().toExternalForm();
        if (file.getPath().startsWith("\\\\")) { // Windows UNC share
            // work around a bug in Xerces due to
            // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5086147
            // (xsom passes a systemId of the form file://server/share/...
            // but this is not parsed correctly when turned back into
            // a File object inside Xerces)
            systemId = systemId.replace("file://", "file:////");
        }
        parser.parse(systemId);
        return loadSchema(name, prefix, parser.getResult(), override);
    }

    // called by tests
    public Schema loadSchema(String name, String prefix, URL url)
            throws SAXException, TypeException {
        XSOMParser parser = getParser();
        parser.parse(url);
        return loadSchema(name, prefix, parser.getResult(), false);
    }

    protected Schema loadSchema(String name, String prefix,
            XSSchemaSet schemaSet, boolean override) throws SAXException,
            TypeException {
        if (schemaSet == null) {
            return null;
        }
        Collection<XSSchema> schemas = schemaSet.getSchemas();
        XSSchema schema = null;
        String ns = null;
        for (XSSchema s : schemas) {
            ns = s.getTargetNamespace();
            if (ns.length() > 0 && !ns.equals(NS_XSD)) {
                schema = s;
                break;
            }
        }
        if (schema == null) {
            return null;
        }
        Schema ecmSchema = getSchema(name);
        if (ecmSchema != null) {
            // schema already defined
            log.info("Schema " + ns + " is already registered");
            if (!override) {
                log.debug("Schema " + ns + " will not be overridden");
                return ecmSchema;
            }
        }
        ecmSchema = new SchemaImpl(name, new Namespace(ns, prefix));

        // load elements
        Collection<XSElementDecl> elements = schema.getElementDecls().values();
        for (XSElementDecl el : elements) {
            // register the type if not yet registered
            Type ecmType = loadType(ecmSchema, el.getType(), el.getName());
            if (ecmType != null) {
                // add the field to the schema
                createField(ecmSchema, el, ecmType);
            } else {
                log.warn("Failed to load field " + el.getName() + " : "
                        + el.getType());
            }
        }

        // load attributes
        Collection<XSAttributeDecl> attributes = schema.getAttributeDecls().values();
        for (XSAttributeDecl att : attributes) {
            // register the type if not yet registered
            Type ecmType = loadType(ecmSchema, att.getType(), att.getName());
            if (ecmType != null) {
                // add the field to the schema
                createField(ecmSchema, att, ecmType);
            } else {
                log.warn("Failed to load field from attribute " + att.getName()
                        + " : " + att.getType());
            }
        }

        registerSchema(ecmSchema);
        return ecmSchema;
    }

    protected Type loadType(Schema schema, XSType type, String fieldName)
            throws TypeBindingException {
        String name;
        if (type.getName() == null || type.isLocal()) {
            name = getAnonymousTypeName(type, fieldName);
            if (name == null) {
                log.warn("Unable to load type - no name found");
                return null;
            }
        } else {
            name = type.getName();
        }
        // look into global types
        Type ecmType = getType(name);
        if (ecmType != null) {
            return ecmType;
        }
        // look into user types for this schema
        ecmType = schema.getType(name);
        if (ecmType != null) {
            return ecmType;
        }
        // maybe an alias to a primitive type?
        if (type.getTargetNamespace().equals(NS_XSD)) {
            ecmType = XSDTypes.getType(name); // find alias
            if (ecmType == null) {
                log.warn("Cannot use unknown XSD type: " + name);
            }
            return ecmType;
        }
        if (type.isSimpleType()) {
            if (type instanceof XSListSimpleType) {
                ecmType = loadListType(schema, (XSListSimpleType) type);
            } else {
                ecmType = loadSimpleType(schema, type, fieldName);
            }
        } else {
            ecmType = loadComplexType(schema, name, type.asComplexType());
        }
        if (ecmType != null) {
            schema.registerType(ecmType);
        } else {
            log.warn("loadType for " + fieldName + " of " + type
                    + " returns null");
        }
        return ecmType;
    }

    /**
     * @param name the type name (note, the type may have a null name if an
     *            anonymous type)
     * @param type
     * @return
     */
    protected Type loadComplexType(Schema schema, String name, XSType type)
            throws TypeBindingException {
        XSType baseType = type.getBaseType();
        ComplexType superType = null;
        // the anyType is the basetype of itself
        if (baseType.getBaseType() != baseType) { // have a base type
            if (baseType.isComplexType()) {
                superType = (ComplexType) loadType(schema, baseType, name);
            } else {
                log.warn("Complex type has a non complex type super type???");
            }
        }
        XSComplexType xsct = type.asComplexType();
        // try to get the delta content
        XSContentType content = xsct.getExplicitContent();
        // if none get the entire content
        if (content == null) {
            content = xsct.getContentType();
        }
        Type ret = createComplexType(schema, superType, name, content);
        if (ret instanceof ComplexType) {
            // load attributes if any
            loadAttributes(schema, xsct, (ComplexType) ret);
        }

        return ret;
    }

    protected void loadAttributes(Schema schema, XSComplexType xsct,
            ComplexType ct) throws TypeBindingException {
        Collection<? extends XSAttributeUse> attrs = xsct.getAttributeUses();
        for (XSAttributeUse attr : attrs) {
            XSAttributeDecl at = attr.getDecl();
            Type fieldType = loadType(schema, at.getType(), at.getName());
            if (fieldType == null) {
                throw new TypeBindingException("Cannot add type for '"
                        + at.getName() + "'");
            }
            createField(ct, at, fieldType);
        }
    }

    protected SimpleType loadSimpleType(Schema schema, XSType type,
            String fieldName) throws TypeBindingException {
        String name = type.getName();
        if (name == null) {
            // probably a local type
            name = fieldName + ANONYMOUS_TYPE_SUFFIX;
        }
        XSType baseType = type.getBaseType();
        SimpleType superType = null;
        if (baseType != type) {
            // have a base type
            superType = (SimpleType) loadType(schema, baseType, fieldName);
        }
        SimpleTypeImpl simpleType = new SimpleTypeImpl(superType,
                schema.getName(), name);

        // add constraints/restrictions to the simple type
        if (type instanceof RestrictionSimpleTypeImpl) {
            RestrictionSimpleTypeImpl restrictionType = (RestrictionSimpleTypeImpl) type;
            List<Constraint> constraints = new ArrayList<Constraint>();
            XSFacet maxLength = restrictionType.getFacet("maxLength");
            if (maxLength != null) {
                int min = 0; // for now
                int max = Integer.parseInt(maxLength.getValue().toString());
                Constraint constraint = new StringLengthConstraint(min, max);
                constraints.add(constraint);
            }

            List<XSFacet> enumFacets = restrictionType.getFacets("enumeration");
            if (enumFacets != null && enumFacets.size() > 0) {
                List<String> enumValues = new ArrayList<String>();
                for (XSFacet enumFacet : enumFacets) {
                    enumValues.add(enumFacet.getValue().toString());
                }
                Constraint constraint = new EnumConstraint(enumValues);
                constraints.add(constraint);
            }

            simpleType.setConstraints(constraints.toArray(new Constraint[0]));
        }

        return simpleType;
    }

    protected ListType loadListType(Schema schema, XSListSimpleType type) {
        String name = type.getName();
        if (name == null) {
            // probably a local type -> ignore it
            return null;
        }
        XSType xsItemType = type.getItemType();
        Type itemType;
        if (xsItemType.getTargetNamespace().equals(NS_XSD)) {
            itemType = XSDTypes.getType(xsItemType.getName());
        } else {
            // itemType = loadType(schema, type);
            // TODO: type must be already defined - use a dependency manager or
            // something to
            // support types that are not yet defined
            itemType = getType(xsItemType.getName());
        }
        if (itemType == null) {
            log.error("list item type was not defined -> you should define first the item type");
            return null;
        }
        return new ListTypeImpl(schema.getName(), name, itemType);
    }

    protected Type createComplexType(Schema schema, ComplexType superType,
            String name, XSContentType content) throws TypeBindingException {

        ComplexType ct = new ComplexTypeImpl(superType, schema.getName(), name);

        // -------- Workaround - we register now the complex type - to fix
        // recursive references to the same type
        schema.registerType(ct);

        // ------------------------------------------
        XSParticle particle = content.asParticle();
        if (particle == null) {
            // complex type without particle -> may be it contains only
            // attributes -> return it as is
            return ct;
        }
        XSTerm term = particle.getTerm();
        XSModelGroup mg = term.asModelGroup();

        return processModelGroup(schema, superType, name, ct, mg);
    }

    protected Type createFakeComplexType(Schema schema, ComplexType superType,
            String name, XSModelGroup mg) throws TypeBindingException {

        ComplexType ct = new ComplexTypeImpl(superType, schema.getName(), name);
        // -------- Workaround - we register now the complex type - to fix
        // recursive references to the same type
        schema.registerType(ct);

        return processModelGroup(schema, superType, name, ct, mg);
    }

    protected Type processModelGroup(Schema schema, ComplexType superType,
            String name, ComplexType ct, XSModelGroup mg)
            throws TypeBindingException {
        if (mg == null) {
            // TODO don't know how to handle this for now
            throw new TypeBindingException("unsupported complex type");
        }
        XSParticle[] group = mg.getChildren();
        if (group.length == 1 && superType == null && group[0].isRepeated()) {
            // a list
            return createListType(schema, name, group[0]);
        }
        for (XSParticle child : group) {
            XSTerm term = child.getTerm();
            XSElementDecl element = term.asElementDecl();
            int maxOccur = child.getMaxOccurs();

            if (element == null) {
                // assume this is a xs:choice group
                // (did not find any other way to detect !
                //
                // => make an aggregation of xs:choice subfields
                if (maxOccur < 0 || maxOccur > 1) {
                    // means this is a list
                    //
                    // first create a fake complex type
                    Type fakeType = createFakeComplexType(schema, superType,
                            name + "#anonymousListItem", term.asModelGroup());
                    // wrap it as a list
                    ListType listType = createListType(schema, name
                            + "#anonymousListType", fakeType, 0, maxOccur);
                    // add the listfield to the current CT
                    String fieldName = ct.getName() + "#anonymousList";
                    ct.addField(fieldName, listType, null, 0);
                } else {
                    processModelGroup(schema, superType, name, ct,
                            term.asModelGroup());
                }
            } else {
                if (maxOccur < 0 || maxOccur > 1) {
                    Type fieldType = loadType(schema, element.getType(),
                            element.getName());
                    ListType listType = createListType(schema,
                            element.getName() + "#anonymousListType",
                            fieldType, 0, maxOccur);
                    // add the listfield to the current CT
                    String fieldName = element.getName();
                    ct.addField(fieldName, listType, null, 0);
                } else {
                    loadComplexTypeElement(schema, ct, element);
                }
            }
        }
        return ct;
    }

    protected ListType createListType(Schema schema, String name,
            XSParticle particle) throws TypeBindingException {
        XSElementDecl element = particle.getTerm().asElementDecl();
        if (element == null) {
            log.warn("Ignoring " + name + " unsupported list type");
            return null;
        }
        XmlString dv = element.getDefaultValue();
        String defValue = null;
        if (dv != null) {
            defValue = dv.value;
        }
        Type type = loadType(schema, element.getType(), element.getName());
        if (type == null) {
            log.warn("Unable to find type for " + element.getName());
        }
        return new ListTypeImpl(schema.getName(), name, type,
                element.getName(), defValue, particle.getMinOccurs(),
                particle.getMaxOccurs());
    }

    protected static ListType createListType(Schema schema, String name,
            Type itemType, int min, int max) throws TypeBindingException {
        String elementName = name + "#item";
        return new ListTypeImpl(schema.getName(), name, itemType, elementName,
                null, min, max);
    }

    protected void loadComplexTypeElement(Schema schema, ComplexType type,
            XSElementDecl element) throws TypeBindingException {
        XSType elementType = element.getType();

        Type fieldType = loadType(schema, elementType, element.getName());
        if (fieldType != null) {
            createField(type, element, fieldType);
        }
    }

    protected static Field createField(ComplexType type, XSElementDecl element,
            Type fieldType) {
        String elementName = element.getName();
        XmlString dv = element.getDefaultValue();
        String defValue = null;
        if (dv != null) {
            defValue = dv.value;
        }
        int flags = 0;
        if (defValue == null) {
            dv = element.getFixedValue();
            if (dv != null) {
                defValue = dv.value;
                flags |= Field.CONSTANT;
            }
        }

        if (element.isNillable()) {
            flags |= Field.NILLABLE;
        }

        Field field = type.addField(elementName, fieldType, defValue, flags);

        // set the max field length from the constraints
        if (fieldType instanceof SimpleTypeImpl) {
            for (Constraint constraint : ((SimpleTypeImpl) fieldType).getConstraints()) {
                if (constraint instanceof StringLengthConstraint) {
                    StringLengthConstraint slc = (StringLengthConstraint) constraint;
                    field.setMaxLength(slc.getMax());
                }
            }
        }

        return field;
    }

    protected static Field createField(ComplexType type,
            XSAttributeDecl element, Type fieldType) {
        String elementName = element.getName();
        XmlString dv = element.getDefaultValue();
        String defValue = null;
        if (dv != null) {
            defValue = dv.value;
        }
        int flags = 0;
        if (defValue == null) {
            dv = element.getFixedValue();
            if (dv != null) {
                defValue = dv.value;
                flags |= Field.CONSTANT;
            }
        }
        return type.addField(elementName, fieldType, defValue, flags);
    }

    protected static String getAnonymousTypeName(XSType type, String fieldName) {
        if (type.isComplexType()) {
            XSElementDecl container = type.asComplexType().getScope();
            String elName = container.getName();
            return elName + ANONYMOUS_TYPE_SUFFIX;
        } else {
            return fieldName + ANONYMOUS_TYPE_SUFFIX;
        }
    }

}
