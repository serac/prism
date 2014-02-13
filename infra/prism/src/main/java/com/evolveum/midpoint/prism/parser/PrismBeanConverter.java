/*
 * Copyright (c) 2014 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.evolveum.midpoint.prism.parser;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map.Entry;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlSchema;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.namespace.QName;

import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Element;

import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.schema.SchemaRegistry;
import com.evolveum.midpoint.prism.xml.XmlTypeConverter;
import com.evolveum.midpoint.prism.xml.XsdTypeMapper;
import com.evolveum.midpoint.prism.xnode.ListXNode;
import com.evolveum.midpoint.prism.xnode.MapXNode;
import com.evolveum.midpoint.prism.xnode.PrimitiveXNode;
import com.evolveum.midpoint.prism.xnode.XNode;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.Handler;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.prism.xml.ns._public.types_2.ItemPathType;
import com.evolveum.prism.xml.ns._public.types_2.RawType;

public class PrismBeanConverter {
	
	public static final String DEFAULT_NAMESPACE_PLACEHOLDER = "##default";
	
	private SchemaRegistry schemaRegistry;

	public PrismBeanConverter(SchemaRegistry schemaRegistry) {
		this.schemaRegistry = schemaRegistry;
	}

	public boolean canConvert(QName typeName) {
		return schemaRegistry.determineCompileTimeClass(typeName) != null; 
	}
	
	public boolean canConvert(Class<?> clazz) {
		return clazz.getAnnotation(XmlType.class) != null;
	}
	
	public <T> T unmarshall(MapXNode xnode, QName typeQName) throws SchemaException {
		Class<T> classType = schemaRegistry.determineCompileTimeClass(typeQName);
		return unmarshall(xnode, classType);
	}
	
	public <T> T unmarshall(MapXNode xnode, Class<T> beanClass) throws SchemaException {
		T bean;
		try {
			bean = beanClass.newInstance();
		} catch (InstantiationException e) {
			throw new SystemException("Cannot instantiate bean of type "+beanClass+": "+e.getMessage(), e);
		} catch (IllegalAccessException e) {
			throw new SystemException("Cannot instantiate bean of type "+beanClass+": "+e.getMessage(), e);
		}
		for (Entry<QName,XNode> entry: xnode.entrySet()) {
			QName key = entry.getKey();
			XNode xsubnode = entry.getValue();
			String propName = key.getLocalPart();
			Field field = findPropertyField(beanClass, propName);
			Method propertyGetter = null;
			if (field == null) {
				propertyGetter = findPropertyGetter(beanClass, propName);
			}
			Method elementMethod = null;
			Object objectFactory = null;
			if (field == null && propertyGetter == null) {
				// We have to try to find a more generic field, such as xsd:any (TODO) or substitution element
				// check for global element definition first
				objectFactory = getObjectFactory(beanClass.getPackage());
				elementMethod = findElementMethodInObjectFactory(objectFactory, propName);
				if (elementMethod == null) {
					throw new SchemaException("No field "+propName+" in class "+beanClass+" (and no element method in object factory too)");
				}
				field = lookupSubstitution(beanClass, elementMethod);
				if (field == null) {
					throw new SchemaException("No field "+propName+" in class "+beanClass+" (and no suitable substitution too)");
				}
			}
			String fieldName;
			if (field != null) {
				fieldName = field.getName();
			} else {
				fieldName = propName;
			}
			
			Method setter = findSetter(beanClass, fieldName);
			Method getter = null;
			boolean wrapInJaxbElement = false;
			Class<?> paramType;
			if (setter == null) {
				// No setter. But if the property is multi-value we need to look
				// for a getter that returns a collection (Collection<Whatever>)
				getter = findGetter(beanClass, fieldName);
				if (getter == null) {
					throw new SchemaException("Cannot find setter or getter for field "+fieldName+" in "+beanClass);
				}
				Class<?> getterReturnType = getter.getReturnType();
				if (!Collection.class.isAssignableFrom(getterReturnType)) {
					throw new SchemaException("Cannot find getter for field "+fieldName+" in "+beanClass+" does not return collection, cannot use it to set value");
				}
				Type genericReturnType = getter.getGenericReturnType();
				Type typeArgument = getTypeArgument(genericReturnType, "for field "+fieldName+" in "+beanClass+", cannot determine collection type");
				if (typeArgument instanceof Class) {
					paramType = (Class<?>) typeArgument;
				} else if (typeArgument instanceof ParameterizedType) {
					ParameterizedType paramTypeArgument = (ParameterizedType)typeArgument;
					Type rawTypeArgument = paramTypeArgument.getRawType();
					if (rawTypeArgument.equals(JAXBElement.class)) {
						// This is the case of Collection<JAXBElement<....>>
						wrapInJaxbElement = true;
						Type innerTypeArgument = getTypeArgument(typeArgument, "for field "+fieldName+" in "+beanClass+", cannot determine collection type (inner type argument)");
						if (innerTypeArgument instanceof Class) {
							// This is the case of Collection<JAXBElement<Whatever>>
							paramType = (Class<?>) innerTypeArgument;
						} else if (innerTypeArgument instanceof WildcardType) {
							// This is the case of Collection<JAXBElement<?>>
							// we need to exctract the specific type from the factory method
							if (elementMethod == null) {
								throw new IllegalArgumentException("Wildcard type in JAXBElement field specification and no facotry method found for field "+fieldName+" in "+beanClass+", cannot determine collection type (inner type argument)");
							}
							Type factoryMethodGenericReturnType = elementMethod.getGenericReturnType();
							Type factoryMethodTypeArgument = getTypeArgument(factoryMethodGenericReturnType, "in factory method "+elementMethod+" return type for field "+fieldName+" in "+beanClass+", cannot determine collection type");
							if (factoryMethodTypeArgument instanceof Class) {
								// This is the case of JAXBElement<Whatever>
								paramType = (Class<?>) factoryMethodTypeArgument;
								if (Object.class.equals(paramType)) {
									throw new IllegalArgumentException("Factory method "+elementMethod+" type argument is Object for field "+
											fieldName+" in "+beanClass+", property "+propName);
								}
							} else {
								throw new IllegalArgumentException("Cannot determine factory method return type, got "+factoryMethodTypeArgument+" - for field "+fieldName+" in "+beanClass+", cannot determine collection type (inner type argument)");
							}
						} else {
							throw new IllegalArgumentException("Ejha! "+innerTypeArgument+" "+innerTypeArgument.getClass()+" from "+getterReturnType+" from "+fieldName+" in "+propName+" "+beanClass);
						}
					} else {
						// The case of Collection<Whatever<Something>>
						if (rawTypeArgument instanceof Class) {
							paramType = (Class<?>) rawTypeArgument;
						} else {
							throw new IllegalArgumentException("EH? Eh!? "+typeArgument+" "+typeArgument.getClass()+" from "+getterReturnType+" from "+fieldName+" in "+propName+" "+beanClass);
						}
					}
				} else {
					throw new IllegalArgumentException("EH? "+typeArgument+" "+typeArgument.getClass()+" from "+getterReturnType+" from "+fieldName+" in "+propName+" "+beanClass);
				}
			} else {
				paramType = setter.getParameterTypes()[0];
			}
			
			if (Element.class.isAssignableFrom(paramType)) {
				// DOM!
				throw new IllegalArgumentException("DOM not supported in field "+fieldName+" in "+beanClass);
			}
			if (Object.class.equals(paramType)) {
				throw new IllegalArgumentException("Object property not supported in field "+fieldName+" in "+beanClass);
			}
						
			String paramNamespace = determineNamespace(paramType);
			
			Object propValue = null;
			Collection<Object> propValues = null;
			if (xsubnode instanceof ListXNode) {
				ListXNode xlist = (ListXNode)xsubnode;
				if (setter != null) {
					propValue = convertSinglePropValue(xsubnode, fieldName, paramType, beanClass, paramNamespace);
				} else {
					// No setter, we have to use collection getter
					propValues = new ArrayList<>(xlist.size());
					for(XNode xsubsubnode: xlist) {
						propValues.add(convertSinglePropValue(xsubsubnode, fieldName, paramType, beanClass, paramNamespace));
					}
				}
			} else {
				propValue = convertSinglePropValue(xsubnode, fieldName, paramType, beanClass, paramNamespace);
			}
			
			if (setter != null) {
				try {
					setter.invoke(bean, wrapInJaxb(propValue, wrapInJaxbElement, objectFactory, elementMethod, propName, beanClass));
				} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
					throw new SystemException("Cannot invoke setter "+setter+" on bean of type "+beanClass+": "+e.getMessage(), e);
				}
			} else if (getter != null) {
				Object getterReturn;
				Collection<Object> col;
				try {
					getterReturn = getter.invoke(bean);
				} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
					throw new SystemException("Cannot invoke getter "+getter+" on bean of type "+beanClass+": "+e.getMessage(), e);
				}
				try {
					col = (Collection<Object>)getterReturn;
				} catch (ClassCastException e) {
					throw new SystemException("Getter "+getter+" on bean of type "+beanClass+" returned "+getterReturn+" instead of collection");
				}
				if (propValue != null) {
					col.add(wrapInJaxb(propValue, wrapInJaxbElement, objectFactory, elementMethod, propName, beanClass));
				} else if (propValues != null) {
					for (Object propVal: propValues) {
						col.add(wrapInJaxb(propVal, wrapInJaxbElement, objectFactory, elementMethod, propName, beanClass));
					}
				} else {
					throw new IllegalStateException("Strange. Multival property "+propName+" in "+beanClass+" produced null values list, parsed from "+xnode);
				}
			} else {
				throw new IllegalStateException("Uh?");
			}
		}
		
		return bean;
	}
	
	private Type getTypeArgument(Type origType, String desc) {
		if (!(origType instanceof ParameterizedType)) {
			throw new IllegalArgumentException("No a parametrized type "+desc);
		}
		ParameterizedType parametrizedType = (ParameterizedType)origType;
		Type[] actualTypeArguments = parametrizedType.getActualTypeArguments();
		if (actualTypeArguments == null || actualTypeArguments.length == 0) {
			throw new IllegalArgumentException("No type arguments for getter "+desc);
		}
		if (actualTypeArguments.length > 1) {
			throw new IllegalArgumentException("Too many type arguments for getter for "+desc);
		}
		return actualTypeArguments[0];
	}
	
	private <T> Object wrapInJaxb(Object propVal, boolean wrapInJaxbElement, Object objectFactory, Method factoryMehtod, String propName, Class beanClass) {
		if (wrapInJaxbElement) {
			if (factoryMehtod == null) {
				throw new IllegalArgumentException("Param type is JAXB element but no factory method found for it, property "+propName+" in "+beanClass);
			}
			try {
				return factoryMehtod.invoke(objectFactory, propVal);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				throw new SystemException("Unable to ivokeke factory method "+factoryMehtod+" on "+objectFactory.getClass()+" for property "+propName+" in "+beanClass);
			}
		} else {
			return propVal;
		}
	}

	private Method findElementMethodInObjectFactory(Object objectFactory, String propName) {
		Class<? extends Object> objectFactoryClass = objectFactory.getClass();
		for (Method method: objectFactoryClass.getDeclaredMethods()) {
			XmlElementDecl xmlElementDecl = method.getAnnotation(XmlElementDecl.class);
			if (xmlElementDecl == null) {
				continue;
			}
			if (propName.equals(xmlElementDecl.name())) {
				return method;
			}
		}
		return null;
	}
	
	private Field lookupSubstitution(Class beanClass, Method elementMethod) {
		XmlElementDecl xmlElementDecl = elementMethod.getAnnotation(XmlElementDecl.class);
		if (xmlElementDecl == null) {
			return null;
		}
		final String substitutionHeadName = xmlElementDecl.substitutionHeadName();
		if (substitutionHeadName == null) {
			return null;
		}
		return findField(beanClass,new Handler<Field>() {
			@Override
			public boolean handle(Field field) {
				XmlElementRef xmlElementRef = field.getAnnotation(XmlElementRef.class);
				if (xmlElementRef == null) {
					return false;
				}
				String name = xmlElementRef.name();
				if (name == null) {
					return false;
				}
				return name.equals(substitutionHeadName);
			}
		});
	}


	private Object getObjectFactory(Package pkg) {
		Class objectFactoryClass;
		try {
			objectFactoryClass = Class.forName(pkg.getName()+".ObjectFactory");
		} catch (ClassNotFoundException e) {
			throw new IllegalArgumentException("Cannot find object factory class in package "+pkg.getName()+": "+e.getMessage(), e);
		}
		try {
			return objectFactoryClass.newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
			throw new IllegalArgumentException("Cannot instantiate object factory class in package "+pkg.getName()+": "+e.getMessage(), e);
		}
	}
	
	private Object convertSinglePropValue(XNode xsubnode, String fieldName, Class paramType, Class classType, String schemaNamespace) throws SchemaException {
		Object propValue;
		if (xsubnode instanceof PrimitiveXNode<?>) {
			propValue = unmarshallPrimitive(((PrimitiveXNode<?>)xsubnode), paramType);
		} else if (xsubnode instanceof MapXNode) {
			propValue = unmarshall((MapXNode)xsubnode, paramType);
		} else if (xsubnode instanceof ListXNode) {
			ListXNode xlist = (ListXNode)xsubnode;
			if (xlist.size() > 1) {
				throw new SchemaException("Cannot set multi-value value to a single valued property "+fieldName+" of "+classType);
			} else {
				if (xlist.isEmpty()) {
					propValue = null;
				} else {
					propValue = xlist.get(0);
				}
			}
		} else {
			throw new IllegalArgumentException("Cannot parse "+xsubnode+" to a bean "+classType);
		}
		return propValue;
	}

	private <T> Field findPropertyField(Class<T> classType, String propName) throws SchemaException {
		Field field = findPropertyFieldExact(classType, propName);
		if (field != null) {
			return field;
		}
		// Fields for some reserved words are prefixed by underscore, so try also this.
		return findPropertyFieldExact(classType, "_"+propName);
	}
	
	private <T> Field findPropertyFieldExact(Class<T> classType, String propName) throws SchemaException {
		for (Field field: classType.getDeclaredFields()) {
			XmlElement xmlElement = field.getAnnotation(XmlElement.class);
			if (xmlElement != null && xmlElement.name() != null && xmlElement.name().equals(propName)) {
				return field;
			}
			XmlAttribute xmlAttribute = field.getAnnotation(XmlAttribute.class);
			if (xmlAttribute != null && xmlAttribute.name() != null && xmlAttribute.name().equals(propName)) {
				return field;
			}
		}
		try {
			return classType.getDeclaredField(propName);
		} catch (NoSuchFieldException e) {
			// nothing found
		}
		Class<? super T> superclass = classType.getSuperclass();
		if (superclass.equals(Object.class)) {
			return null;
		}
		return findPropertyField(superclass, propName);
	}
	
	private Field findField(Class classType, Handler<Field> selector) {
		for (Field field: classType.getDeclaredFields()) {
			if (selector.handle(field)) {
				return field;
			}
		}
		Class superclass = classType.getSuperclass();
		if (superclass.equals(Object.class)) {
			return null;
		}
		return findField(superclass, selector);
	}
	
	private <T> Method findPropertyGetter(Class<T> classType, String propName) throws SchemaException {
		for (Method method: classType.getDeclaredMethods()) {
			XmlElement xmlElement = method.getAnnotation(XmlElement.class);
			if (xmlElement != null && xmlElement.name() != null && xmlElement.name().equals(propName)) {
				return method;
			}
			XmlAttribute xmlAttribute = method.getAnnotation(XmlAttribute.class);
			if (xmlAttribute != null && xmlAttribute.name() != null && xmlAttribute.name().equals(propName)) {
				return method;
			}
		}
		try {
			return classType.getDeclaredMethod(getGetterName(propName));
		} catch (NoSuchMethodException e) {
			// nothing found
		}
		Class<? super T> superclass = classType.getSuperclass();
		if (superclass.equals(Object.class)) {
			return null;
		}
		return findPropertyGetter(superclass, propName);
	}

	public <T> T unmarshallPrimitive(PrimitiveXNode<?> xprim, QName typeQName) throws SchemaException {
		Class<T> classType = schemaRegistry.determineCompileTimeClass(typeQName);
		return unmarshallPrimitive(xprim, classType);
	}
	
	public <T> T unmarshallPrimitive(PrimitiveXNode<?> xprim, Class<T> classType) throws SchemaException {
		if (XmlTypeConverter.canConvert(classType)) {
			// Trivial case, direct conversion
			QName xsdType = XsdTypeMapper.toXsdType(classType);
			T primValue = postConvertUnmarshall(xprim.getParsedValue(xsdType));
			return primValue;
		}
		
		if (RawType.class.isAssignableFrom(classType)) {
			QName typeQName = xprim.getTypeQName();
			if (typeQName == null) {
				typeQName = DOMUtil.XSD_STRING;
			}
			Object parsedValue = xprim.getParsedValue(typeQName);
			RawType rawType = new RawType();
			rawType.setType(typeQName);
			rawType.setValue(parsedValue);
			return (T) rawType;
		}
		
		if (xprim.isEmpty()) {
			// Special case. Just return empty object
			try {
				return classType.newInstance();
			} catch (InstantiationException | IllegalAccessException e) {
				throw new SystemException("Cannot instantiate "+classType+": "+e.getMessage(), e);
			}
		}
		
		if (!classType.isEnum()) {
			throw new SystemException("Cannot convert primitive value to non-enum bean of type "+classType);
		}
		// Assume string, maybe TODO extend later
		String primValue = (String) xprim.getParsedValue(DOMUtil.XSD_STRING);
		if (StringUtils.isBlank(primValue)) {
			return null;
		}
		primValue = StringUtils.trim(primValue);
		
		String javaEnumString = null;
		for (Field field: classType.getDeclaredFields()) {
			XmlEnumValue xmlEnumValue = field.getAnnotation(XmlEnumValue.class);
			if (xmlEnumValue != null && xmlEnumValue.value() != null && xmlEnumValue.value().equals(primValue)) {
				javaEnumString = field.getName();
				break;
			}
		}
		
		if (javaEnumString == null) {
			for (Field field: classType.getDeclaredFields()) {
				if (field.getName().equals(primValue)) {
					javaEnumString = field.getName();
					break;
				}
			}
		}
		
		if (javaEnumString == null) {
			throw new SchemaException("Cannot find enum value for string '"+primValue+"' in "+classType);
		}
		
		T bean = (T) Enum.valueOf((Class<Enum>)classType, javaEnumString);
		
		return bean;
	}
	
	private <T> T postConvertUnmarshall(Object parsedPrimValue) {
		if (parsedPrimValue == null) {
			return null;
		}
		if (parsedPrimValue instanceof ItemPath) {
			return (T) new ItemPathType((ItemPath)parsedPrimValue);
		} else {
			return (T) parsedPrimValue;
		}
	}

	public <T> MapXNode marshall(T bean) {
		if (bean == null) {
			return null;
		}
		
		MapXNode xmap = new MapXNode();
		
		Class<? extends Object> beanClass = bean.getClass();
		XmlType xmlType = beanClass.getAnnotation(XmlType.class);
		if (xmlType == null) {
			throw new IllegalArgumentException("Cannot marshall "+beanClass+" it does not have @XmlType annotation");
		}
		
		String namespace = determineNamespace(beanClass);
		if (namespace == null) {
			throw new IllegalArgumentException("Cannot determine namespace of "+beanClass);
		}
		
		String[] propOrder = xmlType.propOrder();
		for (String fieldName: propOrder) {
			QName elementName = new QName(namespace, fieldName);
			Method getter = findGetter(beanClass, fieldName);
			if (getter == null) {
				throw new IllegalStateException("No getter for field "+fieldName+" in "+beanClass);
			}
			Object getterResult;
			try {
				getterResult = getter.invoke(bean);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				throw new SystemException("Cannot invoke method for field "+fieldName+" in "+beanClass+": "+e.getMessage(), e);
			}
			
			if (getterResult == null) {
				continue;
			}
			
			Field field;
			try {
				field = beanClass.getDeclaredField(fieldName);
			} catch (NoSuchFieldException | SecurityException e) {
				throw new SystemException("Cannot accesss field "+fieldName+" in "+beanClass+": "+e.getMessage(), e);
			}
			
			if (getterResult instanceof Collection<?>) {
				Collection col = (Collection)getterResult;
				if (col.isEmpty()) {
					continue;
				}
				QName fieldTypeName = findFieldTypeName(field, col.iterator().next().getClass(), namespace);
				ListXNode xlist = new ListXNode();
				for (Object element: col) {
					xlist.add(marshallValue(element, fieldTypeName));
				}
				xmap.put(elementName, xlist);
			} else {
				QName fieldTypeName = findFieldTypeName(field, getterResult.getClass(), namespace);
				xmap.put(elementName, marshallValue(getterResult, fieldTypeName));
			}
		}
		
		return xmap;
	}

	private String determineNamespace(Class<? extends Object> beanClass) {
		XmlType xmlType = beanClass.getAnnotation(XmlType.class);
		if (xmlType == null) {
			return null;
		}
		
		String namespace = xmlType.namespace();
		if (namespace == null || DEFAULT_NAMESPACE_PLACEHOLDER.equals(namespace)) {
			XmlSchema xmlSchema = beanClass.getPackage().getAnnotation(XmlSchema.class);
			namespace = xmlSchema.namespace();
		}
		if (StringUtils.isBlank(namespace) || DEFAULT_NAMESPACE_PLACEHOLDER.equals(namespace)) {
			return null;
		}
		
		return namespace;
	}

	private Method findGetter(Class beanClass, String fieldName) {
		String getterName = getGetterName(fieldName);
		Method getter;
		try {
			getter = beanClass.getMethod(getterName);
		} catch (NoSuchMethodException e) {
			return null;
		} catch (SecurityException e) {
			throw new SystemException("Cannot accesss method "+getterName+" in "+beanClass+": "+e.getMessage(), e);
		}
		return getter;
	}

	private <T> XNode marshallValue(T value, QName fieldTypeName) {
		if (value == null) {
			return null;
		}
		if (canConvert(value.getClass())) {
			// This must be a bean
			return marshall(value);
		} else {
			// primitive value
			PrimitiveXNode<T> xprim = new PrimitiveXNode<T>();
			xprim.setValue(value);
			xprim.setTypeQName(fieldTypeName);
			return xprim;
		}
	}

	private String getGetterName(String fieldName) {
		if (fieldName.startsWith("_")) {
			fieldName = fieldName.substring(1);
		}
		return "get"+StringUtils.capitalize(fieldName);
	}
	
	private <T> Method findSetter(Class<T> classType, String fieldName) {
		String setterName = getSetterName(fieldName);
		for(Method method: classType.getMethods()) {
			if (!method.getName().equals(setterName)) {
				continue;
			}
			Class<?>[] parameterTypes = method.getParameterTypes();
			if (parameterTypes.length != 1) {
				continue;
			}
			Class<?> setterType = parameterTypes[0];
			// TODO: check for multiple setters?
			return method;
		}
		return null;
	}
	
	private String getPropertyNameFromGetter(String getterName) {
		if ((getterName.length() > 3) && getterName.startsWith("get") && 
				Character.isUpperCase(getterName.charAt(3))) {
			String propPart = getterName.substring(3);
			return StringUtils.uncapitalize(propPart);
		}
		return getterName;
	}

	private String getSetterName(String fieldName) {
		if (fieldName.startsWith("_")) {
			fieldName = fieldName.substring(1);
		}
		return "set"+StringUtils.capitalize(fieldName);
	}

	private QName findFieldTypeName(Field field, Class fieldType, String schemaNamespace) {
		QName propTypeQname = null;
		XmlSchemaType xmlSchemaType = field.getAnnotation(XmlSchemaType.class);
		if (xmlSchemaType != null) {
			String propTypeLocalPart = xmlSchemaType.name();
			if (propTypeLocalPart != null) {
				String propTypeNamespace = xmlSchemaType.namespace();
				if (propTypeNamespace == null) {
					propTypeNamespace = XMLConstants.W3C_XML_SCHEMA_NS_URI;
				}
				propTypeQname = new QName(propTypeNamespace, propTypeLocalPart);
			}
		}
		if (propTypeQname == null) {
			propTypeQname = XsdTypeMapper.getJavaToXsdMapping(fieldType);
		}
		
		if (propTypeQname == null) {
			XmlType xmlType = (XmlType) fieldType.getAnnotation(XmlType.class);
			if (xmlType != null) {
				String propTypeLocalPart = xmlType.name();
				if (propTypeLocalPart != null) {
					String propTypeNamespace = xmlType.namespace();
					if (propTypeNamespace == null) {
						propTypeNamespace = schemaNamespace;
					}
					propTypeQname = new QName(propTypeNamespace, propTypeLocalPart);
				}
			}	
		}
		
		return propTypeQname;
	}

}
 