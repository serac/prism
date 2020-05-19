/*
 * Copyright (c) 2010-2018 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.prism.impl;

import javax.xml.namespace.QName;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;

import com.evolveum.midpoint.prism.Hacks;
import com.evolveum.midpoint.prism.ParsingContext;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.impl.lex.dom.DomLexicalProcessor;
import com.evolveum.midpoint.prism.impl.marshaller.XNodeProcessorUtil;
import com.evolveum.midpoint.prism.impl.xnode.ListXNodeImpl;
import com.evolveum.midpoint.prism.impl.xnode.MapXNodeImpl;
import com.evolveum.midpoint.prism.impl.xnode.PrimitiveXNodeImpl;
import com.evolveum.midpoint.prism.impl.xnode.XNodeImpl;
import com.evolveum.midpoint.prism.xnode.*;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.prism.xml.ns._public.types_3.ProtectedDataType;

/**
 * TEMPORARY
 */
public class HacksImpl implements Hacks, XNodeMutator {

    @NotNull private final PrismContextImpl prismContext;

    HacksImpl(@NotNull PrismContextImpl prismContext) {
        this.prismContext = prismContext;
    }

    @Override
    public <T> void setPrimitiveXNodeValue(PrimitiveXNode<T> node, T value, QName typeName) {
        ((PrimitiveXNodeImpl<T>) node).setValue(value, typeName);
    }

    @Override
    public void putToMapXNode(MapXNode map, QName key, XNode value) {
        ((MapXNodeImpl) map).put(key, (XNodeImpl) value);
    }

    @Override
    public void addToListXNode(ListXNode list, XNode... nodes) {
        for (XNode node : nodes) {
            ((ListXNodeImpl) list).add((XNodeImpl) node);
        }
    }

    @Override
    public <T> void parseProtectedType(ProtectedDataType<T> protectedType, MapXNode xmap, PrismContext prismContext, ParsingContext pc) throws SchemaException {
        XNodeProcessorUtil.parseProtectedType(protectedType, (MapXNodeImpl) xmap, prismContext, pc);
    }

    @Override
    public Element serializeSingleElementMapToElement(MapXNode filterClauseXNode) throws SchemaException {
        DomLexicalProcessor domParser = getDomParser(prismContext);
        return domParser.serializeSingleElementMapToElement(filterClauseXNode);
    }

    private static DomLexicalProcessor getDomParser(@NotNull PrismContext prismContext) {
        return ((PrismContextImpl) prismContext).getParserDom();
    }

    @Override
    public void setXNodeType(XNode node, QName explicitTypeName, boolean explicitTypeDeclaration) {
        ((XNodeImpl) node).setTypeQName(explicitTypeName);
        ((XNodeImpl) node).setExplicitTypeDeclaration(explicitTypeDeclaration);
    }
}
