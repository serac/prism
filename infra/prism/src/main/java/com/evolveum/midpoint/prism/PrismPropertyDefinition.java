/*
 * Copyright (c) 2010-2013 Evolveum
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

package com.evolveum.midpoint.prism;

import java.util.Arrays;

import javax.xml.namespace.QName;

import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.prism.path.ItemPath;

/**
 * Property Definition.
 * <p/>
 * Property is a basic unit of information in midPoint. This class provides
 * definition of property type, multiplicity and so on.
 * <p/>
 * Property is a specific characteristic of an object. It may be considered
 * object "attribute" or "field". For example User has fullName property that
 * contains string value of user's full name.
 * <p/>
 * Properties may be single-valued or multi-valued
 * <p/>
 * Properties may contain primitive types or complex types (defined by XSD
 * schema)
 * <p/>
 * Property values are unordered, implementation may change the order of values
 * <p/>
 * Duplicate values of properties should be silently removed by implementations,
 * but clients must be able tolerate presence of duplicate values.
 * <p/>
 * Operations that modify the objects work with the granularity of properties.
 * They add/remove/replace the values of properties, but do not "see" inside the
 * property.
 * <p/>
 * This class represents schema definition for property. See {@link Definition}
 * for more details.
 *
 * @author Radovan Semancik
 */
public class PrismPropertyDefinition<T> extends ItemDefinition {

    private static final long serialVersionUID = 7259761997904371009L;
    private QName valueType;
    private T[] allowedValues;
    private boolean create = true;
    private boolean read = true;
    private boolean update = true;
    private Boolean indexed = null;

    public PrismPropertyDefinition(QName name, QName defaultName, QName typeName, PrismContext prismContext) {
        super(name, defaultName, typeName, prismContext);
    }

    /**
     * Returns allowed values for this property.
     *
     * @return Object array. May be null.
     */
    public T[] getAllowedValues() {
        return allowedValues;
    }

    /**
     * TODO:
     *
     * @return
     */
    public boolean canRead() {
        return read;
    }

    /**
     * TODO:
     *
     * @return
     */
    public boolean canUpdate() {
        return update;
    }

    /**
     *
     */
    public void setReadOnly() {
        create = false;
        read = true;
        update = false;
    }

    /**
     * Returns QName of the property value type.
     * <p/>
     * The returned type is either XSD simple type or complex type. It may not
     * be defined in the same schema (especially if it is standard XSD simple
     * type).
     *
     * @return QName of the property value type
     */
    public QName getValueType() {
        return valueType;
    }

    void setValueType(QName valueType) {
        this.valueType = valueType;
    }

    /**
     * This is XSD annotation that specifies whether a property should 
     * be indexed in the storage. It can only apply to properties. It
     * has following meaning:
     * 
     * true: the property must be indexed. If the storage is not able to
     * index the value, it should indicate an error.
     * 
     * false: the property should not be indexed.
     * 
     * null: data store decides whether to index the property or
     * not.
     */
    public Boolean isIndexed() {
		return indexed;
	}

	public void setIndexed(Boolean indexed) {
		this.indexed = indexed;
	}

	@Override
    public PrismProperty<T> instantiate() {
        return instantiate(getNameOrDefaultName());
    }

    @Override
    public PrismProperty<T> instantiate(QName name) {
        return new PrismProperty<T>(name, this, prismContext);
    }

    @Override
	public PropertyDelta<T> createEmptyDelta(ItemPath path) {
		return new PropertyDelta<T>(path, this);
	}

	@Override
	public PrismPropertyDefinition<T> clone() {
        	PrismPropertyDefinition<T> clone = new PrismPropertyDefinition<T>(getName(), getDefaultName(), getTypeName(), getPrismContext());
        	copyDefinitionData(clone);
        	return clone;
	}

	protected void copyDefinitionData(PrismPropertyDefinition<T> clone) {
		super.copyDefinitionData(clone);
		clone.allowedValues = this.allowedValues;
		clone.valueType = this.valueType;
		clone.create = this.create;
		clone.read = this.read;
		clone.update = this.update;
	}

	public void setRead(boolean read) {
        this.read = read;
    }

    public void setUpdate(boolean update) {
        this.update = update;
    }

    public void setCreate(boolean create) {
        this.create = create;
    }

    public boolean canCreate() {
        return create;
    }

    @Override
	protected void extendToString(StringBuilder sb) {
		super.extendToString(sb);
		sb.append(",");
		if (canRead()) {
			sb.append("R");
		} else {
			sb.append("-");
		}
		if (canCreate()) {
			sb.append("C");
		} else {
			sb.append("-");
		}
		if (canUpdate()) {
			sb.append("U");
		} else {
			sb.append("-");
		}
		if (indexed != null && indexed) {
			sb.append(",I");
		}
	}

	@Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Arrays.hashCode(allowedValues);
        result = prime * result + (create ? 1231 : 1237);
        result = prime * result + (read ? 1231 : 1237);
        result = prime * result + (update ? 1231 : 1237);
        result = prime * result + ((valueType == null) ? 0 : valueType.hashCode());
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
        PrismPropertyDefinition other = (PrismPropertyDefinition) obj;
        if (!Arrays.equals(allowedValues, other.allowedValues))
            return false;
        if (create != other.create)
            return false;
        if (read != other.read)
            return false;
        if (update != other.update)
            return false;
        if (valueType == null) {
            if (other.valueType != null)
                return false;
        } else if (!valueType.equals(other.valueType))
            return false;
        return true;
    }
    
    /**
     * Return a human readable name of this class suitable for logs.
     */
    @Override
    protected String getDebugDumpClassName() {
        return "PPD";
    }

    @Override
    public String getDocClassName() {
        return "property";
    }
}
