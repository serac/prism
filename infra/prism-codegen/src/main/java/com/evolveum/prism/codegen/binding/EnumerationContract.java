package com.evolveum.prism.codegen.binding;

import java.util.Collection;
import java.util.Optional;

import javax.xml.namespace.QName;

import com.evolveum.midpoint.prism.EnumerationTypeDefinition;
import com.evolveum.midpoint.prism.EnumerationTypeDefinition.ValueDefinition;

public class EnumerationContract extends Contract {

    private String packageName;
    private EnumerationTypeDefinition typeDefinition;

    public EnumerationContract(EnumerationTypeDefinition typeDefinition, String packageName) {
        this.typeDefinition = typeDefinition;
        this.packageName = packageName;
    }

    @Override
    public String fullyQualifiedName() {
        return packageName + "." + typeDefinition.getTypeName().getLocalPart();
    }

    public Collection<ValueDefinition> values() {
        return typeDefinition.getValues();
    }

    public QName getQName() {
        return typeDefinition.getTypeName();
    }

    public Optional<String> getDocumentation() {
        return Optional.ofNullable(typeDefinition.getDocumentation());
    }

}
