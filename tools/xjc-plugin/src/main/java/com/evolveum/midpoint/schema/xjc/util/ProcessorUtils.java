/*
 * Copyright (c) 2012 Evolveum
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
 *
 * Portions Copyrighted 2012 [name of copyright owner]
 */

package com.evolveum.midpoint.schema.xjc.util;

import com.evolveum.midpoint.schema.xjc.PrefixMapper;
import com.sun.codemodel.*;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.model.CClassInfo;
import com.sun.tools.xjc.model.CPropertyInfo;
import com.sun.tools.xjc.model.nav.NClass;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;
import com.sun.tools.xjc.reader.xmlschema.bindinfo.BIDeclaration;
import com.sun.tools.xjc.reader.xmlschema.bindinfo.BindInfo;
import com.sun.xml.xsom.XSAnnotation;
import com.sun.xml.xsom.XSComponent;
import com.sun.xml.xsom.XSParticle;

import javax.xml.namespace.QName;
import java.lang.reflect.Field;
import java.util.*;

/**
 * @author lazyman
 */
public final class ProcessorUtils {

    private ProcessorUtils() {
    }

    public static String fieldFPrefixUnderscoredUpperCase(String fieldName) {
        return "F_" + fieldUnderscoredUpperCase(fieldName);
    }

    public static String fieldPrefixedUnderscoredUpperCase(String fieldName, QName qname) {
        String prefix = PrefixMapper.getPrefix(qname.getNamespaceURI());

        return prefix + fieldUnderscoredUpperCase(fieldName);
    }

    public static String fieldUnderscoredUpperCase(String fieldName) {
        return fieldName.replaceAll(
                String.format("%s|%s|%s",
                        "(?<=[A-Z])(?=[A-Z][a-z])",
                        "(?<=[^A-Z])(?=[A-Z])",
                        "(?<=[A-Za-z])(?=[^A-Za-z])"
                ),
                "_"
        ).toUpperCase();
    }

    public static ClassOutline findClassOutline(Outline outline, QName type) {
        Set<Map.Entry<NClass, CClassInfo>> set = outline.getModel().beans().entrySet();
        for (Map.Entry<NClass, CClassInfo> entry : set) {
            ClassOutline classOutline = outline.getClazz(entry.getValue());
            QName qname = entry.getValue().getTypeName();
            if (!type.equals(qname)) {
                continue;
            }

            return classOutline;
        }

        throw new IllegalStateException("Object type class defined by qname '" + type + "' outline was not found.");
    }

    public static JFieldVar createPSFField(Outline outline, JDefinedClass definedClass, String fieldName,
            QName reference) {
        JClass clazz = (JClass) outline.getModel().codeModel._ref(QName.class);

        JInvocation invocation = (JInvocation) JExpr._new(clazz);
        invocation.arg(reference.getNamespaceURI());
        invocation.arg(reference.getLocalPart());

        int psf = JMod.PUBLIC | JMod.STATIC | JMod.FINAL;
        return definedClass.field(psf, QName.class, fieldName, invocation);
    }

    public static String getGetterMethod(ClassOutline classOutline, JFieldVar field) {
        CPropertyInfo prop = classOutline.target.getProperty(field.name());
        JType type = field.type();
        Options options = classOutline.parent().getModel().options;
        JCodeModel codeModel = classOutline.parent().getCodeModel();

        if (options.enableIntrospection) {
            return ((type.isPrimitive() && type.boxify().getPrimitiveType() == codeModel.BOOLEAN) ?
                    "is" : "get") + prop.getName(true);
        } else {
            return (type.boxify().getPrimitiveType() == codeModel.BOOLEAN ? "is" : "get") + prop.getName(true);
        }
    }

    public static String getSetterMethod(ClassOutline classOutline, JFieldVar field) {
        CPropertyInfo prop = classOutline.target.getProperty(field.name());

        return "set" + prop.getName(true);
    }

    public static JMethod recreateMethod(JMethod method, JDefinedClass definedClass) {
        Iterator<JMethod> methods = definedClass.methods().iterator();
        while (methods.hasNext()) {
            if (method.equals(methods.next())) {
                methods.remove();
                break;
            }
        }

        JMods mods = method.mods();
        JMethod newMethod = definedClass.method(mods.getValue(), method.type(), method.name());
        JVar[] params = method.listParams();
        if (params == null) {
            return newMethod;
        }

        for (JVar param : params) {
            newMethod.param(param.type(), param.name());
        }

        return newMethod;
    }

    public static void copyAnnotations(JAnnotatable to, JAnnotatable... froms) {
        List<JAnnotationUse> annotations = new ArrayList<JAnnotationUse>();
        for (JAnnotatable from : froms) {
            List<JAnnotationUse> existingAnnotations = (List<JAnnotationUse>) getAnnotations(from);
            if (existingAnnotations != null && !existingAnnotations.isEmpty()) {
                annotations.addAll(existingAnnotations);
            }
        }
        if (annotations.isEmpty()) {
            return;
        }

        System.out.println("Copying " + annotations.size() + " annotations.");

        //let's try dirty copy (it's only inside class)
        try {
            Class clazz = to.getClass();
            Field annotationsField = getField(clazz, "annotations");
            if (annotationsField == null) {
                throw new IllegalStateException("Couldn't find annotations field in " + froms);
            }
            annotationsField.setAccessible(true);
            annotationsField.set(to, annotations);
            annotationsField.setAccessible(false);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    public static List<JAnnotationUse> getAnnotations(JAnnotatable from) {
        List<JAnnotationUse> annotations = new ArrayList<JAnnotationUse>();

        try {
            Class clazz = from.getClass();
            Field annotationsField = getField(clazz, "annotations");
            if (annotationsField == null) {
                throw new IllegalStateException("Couldn't find annotations field in " + from);
            }

            annotationsField.setAccessible(true);
            List<JAnnotationUse> list = (List<JAnnotationUse>) annotationsField.get(from);
            annotationsField.setAccessible(false);
            if (list != null) {
                annotations.addAll(list);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new IllegalStateException(ex.getMessage(), ex);
        }

        return annotations;
    }

    public static Field getField(Class clazz, String name) {
        Field[] fields = clazz.getDeclaredFields();
        if (fields != null) {
            for (Field field : fields) {
                if (field.getName().equals(name)) {
                    return field;
                }
            }
        }

        if (clazz.getSuperclass() != null) {
            return getField(clazz.getSuperclass(), name);
        }

        return null;
    }
    
    private static BIDeclaration hasAnnotation(XSAnnotation annotation, QName qname) {
        if (annotation == null) {
            return null;
        }

        Object object = annotation.getAnnotation();
        if (!(object instanceof BindInfo)) {
            return null;
        }

        BindInfo info = (BindInfo) object;
        BIDeclaration[] declarations = info.getDecls();
        if (declarations == null) {
            return null;
        }

        for (BIDeclaration declaration : declarations) {
            if (qname.equals(declaration.getName())) {
                return declaration;
            }
        }

        return null;
    }
    
    public static BIDeclaration hasAnnotation(ClassOutline classOutline, JFieldVar field, QName qname) {
        CPropertyInfo propertyInfo = classOutline.target.getProperty(field.name());
        if (propertyInfo == null || !(propertyInfo.getSchemaComponent() instanceof XSParticle)) {
            return null;
        }
        XSParticle particle = (XSParticle)propertyInfo.getSchemaComponent();
        if (particle.getTerm() == null) {
            return null;
        }
        XSAnnotation annotation = particle.getTerm().getAnnotation(false);
        
        return hasAnnotation(annotation, qname);
    }

    public static boolean hasAnnotation(ClassOutline classOutline, QName qname) {
        XSComponent xsComponent = classOutline.target.getSchemaComponent();

        if (xsComponent == null) {
            return false;
        }

        return hasAnnotation(xsComponent.getAnnotation(false), qname) != null;
    }
}
