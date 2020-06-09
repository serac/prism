package com.evolveum.axiom.api.stream;

import com.evolveum.axiom.api.AxiomName;
import com.evolveum.axiom.concepts.SourceLocation;
import com.evolveum.axiom.lang.spi.AxiomNameResolver;

public interface AxiomItemStream {

    interface Target {
        void startItem(AxiomName item, SourceLocation loc);
        void endItem(SourceLocation loc);

        void startValue(Object value, SourceLocation loc);
        void endValue(SourceLocation loc);

        default void startInfra(AxiomName item, SourceLocation loc) {};
        default void endInfra(SourceLocation loc) {};

    }

    interface TargetWithResolver extends Target {

        AxiomNameResolver itemResolver();
        AxiomNameResolver valueResolver();

    }

}
