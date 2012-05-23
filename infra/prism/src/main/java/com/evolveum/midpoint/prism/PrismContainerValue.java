/**
 * Copyright (c) 2011 Evolveum
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * Portions Copyrighted 2011 [name of copyright owner]
 */
package com.evolveum.midpoint.prism;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.xml.namespace.QName;

import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.dom.ElementPrismContainerImpl;
import com.evolveum.midpoint.prism.dom.PrismDomProcessor;
import com.evolveum.midpoint.util.DebugDumpable;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.Dumpable;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SystemException;

import org.apache.commons.lang.Validate;
import org.w3c.dom.Element;

/**
 * @author semancik
 *
 */
public class PrismContainerValue<T extends Containerable> extends PrismValue implements Dumpable, DebugDumpable {
	
	// This is list. We need to maintain the order internally to provide consistent
    // output in DOM and other ordering-sensitive representations
    private List<Item<?>> items = new ArrayList<Item<?>>();
    private String id;
    // The elements are set during a schema-less parsing, e.g. during a dumb JAXB parsing of the object
    // We can't do anything smarter, as we don't have definition nor prism context. So we store the raw
    // elements here and process them later (e.g. during applyDefinition).
    private List<Object> rawElements = null;
    
	private T containerable = null;
    
    public PrismContainerValue() {
    	super();
    	// Nothing to do
    }
    
    public PrismContainerValue(SourceType type, Objectable source, PrismContainerable container, String id) {
		super(type, source, container);
		this.id = id;
	}

	/**
     * Returns a set of items that the property container contains. The items may be properties or inner property containers.
     * <p/>
     * The set must not be null. In case there are no properties an empty set is
     * returned.
     * <p/>
     * Returned set is mutable. Live object is returned.
     *
     * @return set of items that the property container contains.
     */
    public List<Item<?>> getItems() {
    	return items;
    }
    
    public Item<?> getNextItem(Item<?> referenceItem) {
    	Iterator<Item<?>> iterator = items.iterator();
    	while (iterator.hasNext()) {
    		Item<?> item = iterator.next();
    		if (item == referenceItem) {
    			if (iterator.hasNext()) {
    				return iterator.next();
    			} else {
    				return null;
    			}
    		}
    	}
    	throw new IllegalArgumentException("Item "+referenceItem+" is not part of "+this);
    }

    public Item<?> getPreviousItem(Item<?> referenceItem) {
    	Item<?> lastItem = null;
    	Iterator<Item<?>> iterator = items.iterator();
    	while (iterator.hasNext()) {
    		Item<?> item = iterator.next();
    		if (item == referenceItem) {
    			return lastItem;
    		}
    		lastItem = item;
    	}
    	throw new IllegalArgumentException("Item "+referenceItem+" is not part of "+this);
    }

    
    /**
     * Returns a set of properties that the property container contains.
     * <p/>
     * The set must not be null. In case there are no properties an empty set is
     * returned.
     * <p/>
     * Returned set is immutable! Any change to it will be ignored.
     *
     * @return set of properties that the property container contains.
     */
    public Set<PrismProperty<?>> getProperties() {
        Set<PrismProperty<?>> properties = new HashSet<PrismProperty<?>>();
        for (Item<?> item : getItems()) {
            if (item instanceof PrismProperty) {
                properties.add((PrismProperty<?>) item);
            }
        }
        return properties;
    }
    
    public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}
	
	@SuppressWarnings("unchecked")
	public PrismContainerable<T> getParent() {
		Itemable parent = super.getParent();
		if (parent == null) {
			return null;
		}
		if (!(parent instanceof PrismContainerable)) {
			throw new IllegalStateException("Expected that parent of "+PrismContainerValue.class.getName()+" will be "+
					PrismContainerable.class.getName()+", but it is "+parent.getClass().getName());
		}
		return (PrismContainerable<T>)parent;
	}
	
	void setParent(PrismContainerable container) {
		super.setParent(container);
	}
	
	@SuppressWarnings("unchecked")
	public PrismContainer<T> getContainer() {
		Itemable parent = super.getParent();
		if (parent == null) {
			return null;
		}
		if (!(parent instanceof PrismContainer)) {
			throw new IllegalStateException("Expected that parent of "+PrismContainerValue.class.getName()+" will be "+
					PrismContainer.class.getName()+", but it is "+parent.getClass().getName());
		}
		return (PrismContainer<T>)super.getParent();
	}
	
	void setParent(PrismContainer<T> container) {
		super.setParent(container);
	}
	
	public PropertyPath getPath(PropertyPath pathPrefix) {
		Itemable parent = getParent();
		PropertyPath parentPath = PropertyPath.EMPTY_PATH;
		if (parent != null) {
			parentPath = getParent().getPath(pathPrefix);
		}
		if (parentPath == null || parentPath.isEmpty()) {
			return parentPath;
		}
		PropertyPathSegment mySegment = new PropertyPathSegment(getParent().getName(), getId());
		return parentPath.allExceptLast().subPath(mySegment);
	}
	
	// For compatibility with other PrismValue types
	public T getValue() {
		return asContainerable();
	}
	
	public List<Object> getRawElements() {
		if (rawElements == null) {
			rawElements = createElement();
		}
		return rawElements;
	}
	
	private List<Object> createElement() {
		return new ArrayList<Object>();
	}

	public T asContainerable() {
		PrismContainerable parent = getParent();
		if (parent == null) {
			throw new IllegalStateException("Cannot represent container value witout a parent as containerable; value: "+this);
		}
        Class<T> clazz = parent.getCompileTimeClass();
        if (clazz == null) {
            throw new SystemException("Unknown compile time class of container '" + parent.getName() + "'.");
        }
        if (Modifier.isAbstract(clazz.getModifiers())) {
            throw new SystemException("Can't create instance of class '" + clazz.getSimpleName() + "', it's abstract.");
        }
        return asContainerable(clazz);
	}     
        
   public T asContainerable(Class<T> clazz) {
	   if (containerable != null) {
		   return containerable ;
	   }
		try {
            containerable = clazz.newInstance();
            containerable.setupContainerValue(this);
            return (T) containerable;
        } catch (SystemException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SystemException("Couldn't create jaxb object instance of '" + clazz + "': "+ex.getMessage(), ex);
        }
    }

	public Collection<QName> getPropertyNames() {
		Collection<QName> names = new HashSet<QName>();
		for (PrismProperty<?> prop: getProperties()) {
			names.add(prop.getName());
		}
		return names;
	}
    
    /**
     * Adds an item to a property container.
     *
     * @param item item to add.
     * @throws SchemaException 
     * @throws IllegalArgumentException an attempt to add value that already exists
     */
    public void add(Item<?> item) throws SchemaException {
    	if (item.getName() == null) {
    		throw new IllegalArgumentException("Cannot add item without a name to value of container "+getParent());
    	}
        if (findItem(item.getName(), Item.class) != null) {
            throw new IllegalArgumentException("Item " + item.getName() + " is already present in " + this.getClass().getSimpleName());
        }
        item.setParent(this);
        PrismContext prismContext = getPrismContext();
        if (prismContext != null) {
        	item.setPrismContext(prismContext);
        }
        if (getParent() != null && getParent().getDefinition() != null && item.getDefinition() == null) {
        	item.applyDefinition(determineItemDefinition(item.getName(), getParent().getDefinition()), false);
        }
        items.add(item);
    }

    /**
     * Adds an item to a property container. Existing value will be replaced.
     *
     * @param item item to add.
     */
    public void addReplaceExisting(Item<?> item) throws SchemaException {
        Item<?> existingItem = findItem(item.getName(), Item.class);
        if (existingItem != null) {
            items.remove(existingItem);
            existingItem.setParent(null);
        }
        add(item);
    }
    
    public void remove(Item<?> item) {
        Validate.notNull(item, "Item must not be null.");

        Item<?> existingItem = findItem(item.getName(),  Item.class);
        if (existingItem != null) {
            items.remove(existingItem);
            existingItem.setParent(null);
        }
    }
    
    public void removeAll() {
        Iterator<Item<?>> iterator = items.iterator();
        while (iterator.hasNext()) {
            Item<?> item = iterator.next();
            item.setParent(null);
            iterator.remove();
        }
    }

    /**
     * Adds a collection of items to a property container.
     *
     * @param itemsToAdd items to add
     * @throws IllegalArgumentException an attempt to add value that already exists
     */
    public void addAll(Collection<? extends Item<?>> itemsToAdd) throws SchemaException {
        for (Item<?> item : itemsToAdd) {
        	add(item);
        }
    }

    /**
     * Adds a collection of items to a property container. Existing values will be replaced.
     *
     * @param itemsToAdd items to add
     */
    public void addAllReplaceExisting(Collection<? extends Item<?>> itemsToAdd) {
        // Check for conflicts, remove conflicting values
        for (Item<?> item : itemsToAdd) {
            Item<?> existingItem = findItem(item.getName(), Item.class);
            if (existingItem != null) {
                items.remove(existingItem);
            }
        }
        items.addAll(itemsToAdd);
    }
    
	public void replace(Item<?> oldItem, Item<?> newItem) throws SchemaException {
		remove(oldItem);
		add(newItem);
	}

    // Expects that the "self" path segment is already included in the basePath
    void addItemPathsToList(PropertyPath basePath, Collection<PropertyPath> list) {
    	for (Item<?> item: items) {
    		if (item instanceof PrismProperty) {
    			list.add(basePath.subPath(item.getName()));
    		} else if (item instanceof PrismContainer) {
    			((PrismContainer<?>)item).addItemPathsToList(basePath, list);
    		}
    	}
    }
    
    public void clear() {
    	items.clear();
    }

    @SuppressWarnings("unchecked")
	public <X> PrismProperty<X> findProperty(QName propertyQName) {
        return findItem(propertyQName, PrismProperty.class);
    }

    /**
     * Finds a specific property in the container by definition.
     * <p/>
     * Returns null if nothing is found.
     *
     * @param propertyDefinition property definition to find.
     * @return found property or null
     */
    public <X> PrismProperty<X> findProperty(PrismPropertyDefinition propertyDefinition) {
        if (propertyDefinition == null) {
            throw new IllegalArgumentException("No property definition");
        }
        return findProperty(propertyDefinition.getName());
    }
    
    public <X extends Containerable> PrismContainer<X> findContainer(QName containerName) {
    	return findItem(containerName, PrismContainer.class);
    }
    
    public PrismReference findReference(QName elementName) {
    	return findItem(elementName, PrismReference.class);
    }
    
    public PrismReference findReferenceByCompositeObjectElementName(QName elementName) {
    	for (Item item: items) {
    		if (item instanceof PrismReference) {
    			PrismReference ref = (PrismReference)item;
    			PrismReferenceDefinition refDef = ref.getDefinition();
    			if (refDef != null) {
    				if (elementName.equals(refDef.getCompositeObjectElementName())) {
    					return ref;
    				}
    			}
    		}
    	}
    	return null;
    }
    
    public <I extends Item<?>> I findItem(QName itemName, Class<I> type) {
    	try {
			return findCreateItem(itemName, type, null, false);
		} catch (SchemaException e) {
			// This should not happen
			throw new SystemException("Internal Error: "+e.getMessage(),e);
		}
    }
    
    public Item<?> findItem(QName itemName) {
    	try {
			return findCreateItem(itemName, Item.class, null, false);
		} catch (SchemaException e) {
			// This should not happen
			throw new SystemException("Internal Error: "+e.getMessage(),e);
		}
    }
    
    public Item<?> findItem(PropertyPath itemPath) {
    	try {
			return findCreateItem(itemPath, Item.class, null, false);
		} catch (SchemaException e) {
			// This should not happen
			throw new SystemException("Internal Error: "+e.getMessage(),e);
		}
    }
    
    @SuppressWarnings("unchecked")
	<I extends Item<?>> I findCreateItem(QName itemName, Class<I> type, ItemDefinition itemDefinition, boolean create) throws SchemaException {
    	for (Item<?> item : items) {
            if (itemName.equals(item.getName())) {
            	if (type.isAssignableFrom(item.getClass())) {
            		return (I)item;
            	} else {
            		if (create) {
            			throw new IllegalStateException("The " + type.getSimpleName() + " cannot be created because "
        						+ item.getClass().getSimpleName() + " with the same name exists ("+item.getName()+")");
            		} else {
            			return null;
            		}
            	}
            }
        }
    	if (create) {
    		return createSubItem(itemName, type, itemDefinition);
    	} else {
    		return null;
    	}
    }
    
    public <I extends Item<?>> I findItem(ItemDefinition itemDefinition, Class<I> type) {
        if (itemDefinition == null) {
            throw new IllegalArgumentException("No item definition");
        }
        return findItem(itemDefinition.getName(), type);
    }


    // Expects that "self" path is NOT present in propPath
    @SuppressWarnings("unchecked")
	<I extends Item<?>> I findCreateItem(PropertyPath propPath, Class<I> type, ItemDefinition itemDefinition, boolean create) throws SchemaException {
    	PropertyPathSegment first = propPath.first();
    	PropertyPath rest = propPath.rest();
    	for (Item<?> item : items) {
            if (first.getName().equals(item.getName())) {
            	if (rest.isEmpty()) {
            		if (type.isAssignableFrom(item.getClass())) {
            			return (I)item;
            		} else {
            			if (create) {
            				throw new SchemaException("The " + type.getSimpleName() + " cannot be created because "
            						+ item.getClass().getSimpleName() + " with the same name exists ("+item.getName()+")");
            			} else {
            				return null;
            			}
            		}
            	} else {
            		// Go deeper
	            	if (item instanceof PrismContainer) {
	            		return ((PrismContainer<?>)item).findCreateItem(propPath, type, itemDefinition, create);
	            	} else {
            			if (create) {
            				throw new SchemaException("The " + type.getSimpleName() + " cannot be created because "
            						+ item.getClass().getSimpleName() + " with the same name exists ("+item.getName()+")");
            			} else {
            				// Return the item for non-container even if the path is non-empty
            				// FIXME: This is not the best solution but it is needed to be able to look inside properties
            				// such as PolyString
            				if (type.isAssignableFrom(item.getClass())) {
                    			return (I)item;
                    		} else {
                    			return null;
                    		}
            			}
	            	}
            	}        
            }
        }
    	if (create) {
    		if (rest.isEmpty()) {
    			return createSubItem(first.getName(), type, itemDefinition);
    		} else {
	    		// Go deeper
    			PrismContainer<?> subItem = createSubItem(first.getName(), PrismContainer.class, null);
	        	return subItem.findCreateItem(propPath, type, itemDefinition, create);
    		}
    	} else {
    		return null;
    	}
    }
    
    @SuppressWarnings("unchecked")
	private <I extends Item<?>> I createSubItem(QName name, Class<I> type, ItemDefinition itemDefinition) throws SchemaException {
    	// the item with specified name does not exist, create it now
		Item<?> newItem = null;
		
		if (itemDefinition == null && getParent() != null && getParent().getDefinition() != null) {
			itemDefinition = determineItemDefinition(name, getParent().getDefinition());
			if (itemDefinition == null) {
				throw new SchemaException("No definition for item "+name+" in "+getParent());
			}
		}
		
		if (itemDefinition != null) {
			newItem = itemDefinition.instantiate(name);
			if (newItem instanceof PrismObject) {
				throw new IllegalStateException("PrismObject instantiated as a subItem in "+this+" from definition "+itemDefinition);
			}
		} else {
			newItem = Item.createNewDefinitionlessItem(name, type);
			if (newItem instanceof PrismObject) {
				throw new IllegalStateException("PrismObject instantiated as a subItem in "+this+" as definitionless instance of class "+type);
			}
		}
		
		if (type.isAssignableFrom(newItem.getClass())) {
			add(newItem);
			return (I)newItem;
    	} else {
			throw new IllegalStateException("The " + type.getSimpleName() + " cannot be created because the item should be of type "
					+ newItem.getClass().getSimpleName() + " ("+newItem.getName()+")");
    	}
    }

    public <T extends Containerable> PrismContainer<T> findOrCreateContainer(QName containerName) throws SchemaException {
    	return findCreateItem(containerName, PrismContainer.class, null, true);
    }
    
    public PrismReference findOrCreateReference(QName referenceName) throws SchemaException {
    	return findCreateItem(referenceName, PrismReference.class, null, true);
    }
    
    public Item<?> findOrCreateItem(QName containerName) throws SchemaException {
    	return findCreateItem(containerName, Item.class, null, true);
    }
    
    public <X extends Item> X findOrCreateItem(QName containerName, Class<X> type) throws SchemaException {
    	return findCreateItem(containerName, type, null, true);
    }

    public <X> PrismProperty<X> findOrCreateProperty(QName propertyQName) throws SchemaException {
        PrismProperty<X> property = findItem(propertyQName, PrismProperty.class);
        if (property != null) {
            return property;
        }
        return createProperty(propertyQName);
    }
    
    public <X> PrismProperty<X> findOrCreateProperty(PrismPropertyDefinition propertyDef) throws SchemaException {
        PrismProperty<X> property = findItem(propertyDef.getName(), PrismProperty.class);
        if (property != null) {
            return property;
        }
        return createProperty(propertyDef);
    }

//    public PrismProperty findOrCreateProperty(PropertyPath parentPath, QName propertyQName, Class<?> valueClass) {
//        PrismContainer container = findOrCreatePropertyContainer(parentPath);
//        if (container == null) {
//            throw new IllegalArgumentException("No container");
//        }
//        return container.findOrCreateProperty(propertyQName, valueClass);
//    }

    public <X extends Containerable> PrismContainer<X> createContainer(QName containerName) throws SchemaException {
        if (getParent().getDefinition() == null) {
            throw new IllegalStateException("No definition of container "+containerName);
        }
        PrismContainerDefinition containerDefinition = getParent().getDefinition().findContainerDefinition(containerName);
        if (containerDefinition == null) {
            throw new IllegalArgumentException("No definition of container '" + containerName + "' in " + getParent().getDefinition());
        }
        PrismContainer<X> container = containerDefinition.instantiate();
        add(container);
        return container;
    }

    public <X> PrismProperty<X> createProperty(QName propertyName) throws SchemaException {
        PrismPropertyDefinition propertyDefinition = null;
        if (getParent() != null && getParent().getDefinition() != null) {
        	propertyDefinition = getParent().getDefinition().findPropertyDefinition(propertyName);
        	if (propertyDefinition == null) {
        		// container has definition, but there is no property definition. This is either runtime schema
        		// or an error
        		if (getParent().getDefinition().isRuntimeSchema) {
        			// TODO: create opportunistic runtime definition
            		//propertyDefinition = new PrismPropertyDefinition(propertyName, propertyName, typeName, container.prismContext);
        		} else {
        			throw new IllegalArgumentException("No definition for property "+propertyName+" in "+getParent());
        		}
        	}
        }
        PrismProperty<X> property = null;
        if (propertyDefinition == null) {
        	// Definitionless
        	property = new PrismProperty<X>(propertyName);
        } else {
        	property = propertyDefinition.instantiate();
        }
        add(property);
        return property;
    }
    
    public <X> PrismProperty<X> createProperty(PrismPropertyDefinition propertyDefinition) throws SchemaException {
    	PrismProperty<X> property = propertyDefinition.instantiate();
    	add(property);
        return property;
    }
    
    @Override
	public void accept(Visitor visitor) {
		super.accept(visitor);
		for (Item<?> item: getItems()) {
			item.accept(visitor);
		}
	}

	@Override
	protected Element createDomElement() {
		return new ElementPrismContainerImpl<T>(this);
	}

	public boolean equivalent(PrismContainerValue<?> other) {
        return equalsRealValue(other);
    }
    
	@Override
	public boolean equalsRealValue(PrismValue thisValue, PrismValue value) {
		if (value instanceof PrismContainerValue && thisValue instanceof PrismContainerValue) {
			return equalsRealValue((PrismContainerValue<T>)thisValue, (PrismContainerValue<T>)value);
		} else {
			return false;
		}
	}
	
	public boolean equalsRealValue(PrismContainerValue<T> thisValue, PrismContainerValue<T> otherValue) {
		// Ignore ID. ID is considered to be meta-data.
		return equalsItems(thisValue, otherValue, true);
	}
	
	@Override
	public boolean representsSameValue(PrismValue other) {
		if (other instanceof PrismContainerValue) {
			return representsSameValue((PrismContainerValue<T>)other);
		} else {
			return false;
		}
	}
	
	public boolean representsSameValue(PrismContainerValue<T> other) {
		if (getParent() != null) {
			PrismContainerDefinition definition = getParent().getDefinition();
			if (definition != null) {
				if (definition.isSingleValue()) {
					// There is only one value, therefore it always represents the same thing
					return true;
				}
			}
		}
		if (other.getParent() != null) {
			PrismContainerDefinition definition = other.getParent().getDefinition();
			if (definition != null) {
				if (definition.isSingleValue()) {
					// There is only one value, therefore it always represents the same thing
					return true;
				}
			}
		}
		if (this.getId() != null && other.getId() != null) {
			return this.getId().equals(other.getId());
		}
		return false;
	}


	@Override
	void diffMatchingRepresentation(PrismValue otherValue, PropertyPath pathPrefix,
			Collection<? extends ItemDelta> deltas, boolean ignoreMetadata) {
		if (otherValue instanceof PrismContainerValue) {
			diffRepresentation((PrismContainerValue)otherValue, pathPrefix, deltas, ignoreMetadata);
		} else {
			throw new IllegalStateException("Comparing incompatible values "+this+" - "+otherValue);
		}		
	}
	
	void diffRepresentation(PrismContainerValue<T> otherValue, PropertyPath pathPrefix,
			Collection<? extends ItemDelta> deltas, boolean ignoreMetadata) {
		PrismContainerValue<T> thisValue = this;
		if (this.rawElements != null || otherValue.rawElements != null) {
			try {
				if (this.rawElements == null) {
					otherValue = parseRawElementsToNewValue(otherValue, thisValue);
				} else if (otherValue.rawElements == null) {
					thisValue = parseRawElementsToNewValue(thisValue, otherValue);
				}
			} catch (SchemaException e) {
				// TODO: Maybe just return false?
				throw new IllegalArgumentException("Error parsing the value of container "+getParent()+" using the 'other' definition "+
						"during a compare: "+e.getMessage(),e);
			}
		} 
		diffItems(thisValue, otherValue, pathPrefix, deltas, ignoreMetadata);
	}
	
	@Override
	public boolean isRaw() {
		return rawElements != null;
	}

	private PrismContainerValue<T> parseRawElementsToNewValue(PrismContainerValue<T> origCVal, PrismContainerValue<T> definitionSource) throws SchemaException {
		List<Object> rawElements = origCVal.rawElements;
		if (definitionSource.getParent() == null || definitionSource.getParent().getDefinition() == null) {
			throw new IllegalArgumentException("Attempt to use container " + origCVal.getParent() + 
			" values in a raw parsing state (raw elements) with parsed value that has no definition");
		}
		PrismContainerDefinition<T> definition = definitionSource.getParent().getDefinition();
		Collection<? extends Item<?>> parsedItems = parseRawElementsToItems(rawElements, definition);
		PrismContainerValue<T> newCVal = new PrismContainerValue<T>();
		newCVal.setParent(origCVal.getParent());
		newCVal.addAll(parsedItems);
		return newCVal;
	}
	
	private Collection<? extends Item<?>> parseRawElementsToItems(List<Object> rawElements, PrismContainerDefinition<T> definition) throws SchemaException {
		PrismDomProcessor domProcessor = definition.getPrismContext().getPrismDomProcessor();
		Collection<? extends Item<?>> parsedItems = domProcessor.parseContainerItems(definition, rawElements);
		return parsedItems;
	}
	
	boolean equalsItems(PrismContainerValue<T> other, boolean ignoreMetadata) {
		return equalsItems(this, other, ignoreMetadata);
	}
	
	boolean equalsItems(PrismContainerValue<T> thisValue, PrismContainerValue<T> other, boolean ignoreMetadata) {
		Collection<? extends ItemDelta<?>> deltas = new ArrayList<ItemDelta<?>>();
		// The EMPTY_PATH is a lie. We don't really care if the returned deltas have correct path or not
		// we only care whether some deltas are returned or not.
		diffItems(thisValue, other, PropertyPath.EMPTY_PATH, deltas, ignoreMetadata);
		return deltas.isEmpty();
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void diffItems(PrismContainerValue<T> thisValue, PrismContainerValue<T> other, PropertyPath pathPrefix,
			Collection<? extends ItemDelta> deltas, boolean ignoreMetadata) {
		
		for (Item<?> thisItem: thisValue.getItems()) {
			Item otherItem = other.findItem(thisItem.getName());
			// The "delete" delta will also result from the following diff
			thisItem.diffInternal(otherItem, pathPrefix, deltas, ignoreMetadata);
		}
		
		for (Item otherItem: other.getItems()) {
			Item thisItem = thisValue.findItem(otherItem.getName());
			if (thisItem == null) {
				// Other has an item that we don't have, this must be an add
				ItemDelta itemDelta = otherItem.createDelta(otherItem.getPath(pathPrefix));
				itemDelta.addValuesToAdd(otherItem.getClonedValues());
				if (!itemDelta.isEmpty()) {
					((Collection)deltas).add(itemDelta);
				}
			}
		}
	}
	
	
	
//	@Override
//	public void applyDefinition(ItemDefinition definition) throws SchemaException {
//		applyDefinition(definition, true);
//	}
	
	@Override
	public void applyDefinition(ItemDefinition definition, boolean force) throws SchemaException {
		if (!(definition instanceof PrismContainerDefinition)) {
    		throw new IllegalArgumentException("Cannot apply "+definition+" to container " + this);
    	}
		applyDefinition((PrismContainerDefinition)definition, force);
	}

	public void applyDefinition(PrismContainerDefinition definition, boolean force) throws SchemaException {
		if (rawElements != null) {
			// There are DOM/JAXB elements that needs to be parsed while the schema is being applied
			Collection<? extends Item<?>> parsedItems = parseRawElementsToItems(rawElements, definition);
			addAll(parsedItems);
			rawElements = null;
		}
		for (Item<?> item: items) {
			if (item.getDefinition() != null && !force) {
				// Item has a definition already, no need to apply it
				continue;
			}
			ItemDefinition itemDefinition = determineItemDefinition(item.getName(), definition); 
			if (itemDefinition == null && item.getDefinition() != null && item.getDefinition().isDynamic()) {
				// We will not apply the null definition here. The item has a dynamic definition that we don't
				// want to destroy as it cannot be reconstructed later.
			} else {
				item.applyDefinition(itemDefinition);
			}
		}
	}

	/**
	 * This method can both return null and throws exception. It returns null in case there is no definition
	 * but it is OK (e.g. runtime schema). It throws exception if there is no definition and it is not OK.
	 */
	private ItemDefinition determineItemDefinition(QName itemName, PrismContainerDefinition<T> containerDefinition) throws SchemaException {
		ItemDefinition itemDefinition = containerDefinition.findItemDefinition(itemName);
		if (itemDefinition == null) {
			if (containerDefinition.isRuntimeSchema()) {
				// If we have prism context, try to locate global definition. But even if that is not
				// found it is still OK. This is runtime container. We tolerate quite a lot here.
				PrismContext prismContext = getPrismContext();
				if (prismContext != null) {
					itemDefinition = prismContext.getSchemaRegistry().resolveGlobalItemDefinition(itemName);
				}
			} else {
				throw new SchemaException("No definition for item " + itemName + " in " + getParent());
			}
		}
		return itemDefinition;
	}

	public void revive(PrismContext prismContext) {
		for (Item<?> item: items) {
			item.revive(prismContext);
		}
	}
    
    public boolean isEmpty() {
        return items.isEmpty();
    }
    
    @Override
	public void checkConsistenceInternal(Item<?> rootItem, PropertyPath parentPath) {
		PropertyPath myPath = getPath(parentPath);
		if (items == null && rawElements == null) {
			throw new IllegalStateException("Neither items nor raw elements specified in container value "+this+" ("+myPath+" in "+rootItem+")");
		}
		if (items != null && rawElements != null) {
			throw new IllegalStateException("Both items and raw elements specified in container value "+this+" ("+myPath+" in "+rootItem+")");
		}
		if (items != null) {
			for (Item<?> item: items) {
				if (item == null) {
					throw new IllegalStateException("Null item in container value "+this+" ("+myPath+" in "+rootItem+")");
				}
				if (item.getParent() == null) {
					throw new IllegalStateException("No parent for item "+item+" in container value "+this+" ("+myPath+" in "+rootItem+")");
				}
				if (item.getParent() != this) {
					throw new IllegalStateException("Wrong parent for item "+item+" in container value "+this+" ("+myPath+" in "+rootItem+"), " +
							"bad parent: "+ item.getParent());
				}
				item.checkConsistenceInternal(rootItem, myPath);
			}
		}
	}
    
    public void assertDefinitions(String sourceDescription) throws SchemaException {
    	assertDefinitions(false, sourceDescription);
    }
    
	public void assertDefinitions(boolean tolerateRaw, String sourceDescription) throws SchemaException {
		for (Item<?> item: getItems()) {
			item.assertDefinitions(tolerateRaw, "value("+getId()+") in "+sourceDescription);
		}
	}

	public PrismContainerValue<T> clone() {
    	PrismContainerValue<T> clone = new PrismContainerValue<T>(getType(), getSource(), getParent(), getId());
    	copyValues(clone);
        return clone;
    }
    
	protected void copyValues(PrismContainerValue<T> clone) {
		super.copyValues(clone);
		clone.id = this.id;
        for (Item<?> item: this.items) {
        	Item<?> clonedItem = item.clone();
        	clonedItem.setParent(clone);
        	clone.items.add(clonedItem);
        }
        // TODO: deep clonning?
        clone.rawElements = this.rawElements;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((items == null) ? 0 : MiscUtil.unorderedCollectionHashcode(items));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		PrismContainerValue<?> other = (PrismContainerValue<?>) obj;
		return equals(this, other);
	}
	
	public boolean equals(PrismValue thisValue, PrismValue otherValue) {
		if (thisValue instanceof PrismContainerValue && otherValue instanceof PrismContainerValue) {
			return equals((PrismContainerValue)thisValue, (PrismContainerValue)otherValue);
		}
		return false;
	}
	
	public boolean equals(PrismContainerValue<T> thisValue, PrismContainerValue<T> otherValue) {
		if (thisValue.id == null) {
			if (otherValue.id != null)
				return false;
		} else if (!thisValue.id.equals(otherValue.id))
			return false;
		if (thisValue.items == null) {
			if (otherValue.items != null)
				return false;
		} else if (!this.equalsItems(thisValue, (PrismContainerValue<T>) otherValue, false)) {
			return false;
		}
		return true;
	}

	@Override
    public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("PCV(");
		sb.append(getId());
		if (rawElements != null) {
			sb.append(", ");
			sb.append(rawElements.size());
			sb.append(" raw elements");
		}
		sb.append("):");
        sb.append(getItems());
        return sb.toString();
    }

    @Override
    public String dump() {
        return debugDump();
    }

    @Override
    public String debugDump() {
    	return debugDump(0);
    }
    
    @Override
    public String debugDump(int indent) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            sb.append(INDENT_STRING);
        }
        sb.append("PCV").append(": ").append(DebugUtil.prettyPrint(getId()));
        Iterator<Item<?>> i = getItems().iterator();
        if (i.hasNext()) {
            sb.append("\n");
        }
        while (i.hasNext()) {
        	Item<?> item = i.next();
            sb.append(item.debugDump(indent + 1));
            if (i.hasNext()) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

}
