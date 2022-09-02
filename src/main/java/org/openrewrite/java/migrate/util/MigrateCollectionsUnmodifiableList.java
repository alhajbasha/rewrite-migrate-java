/*
 * Copyright 2022 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.migrate.util;

import org.openrewrite.Applicability;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesJavaVersion;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.time.Duration;
import java.util.List;
import java.util.StringJoiner;

public class MigrateCollectionsUnmodifiableList extends Recipe {
    private static final MethodMatcher UNMODIFIABLE_LIST = new MethodMatcher("java.util.Collections unmodifiableList(java.util.List)", true);
    private static final MethodMatcher ARRAYS_AS_LIST = new MethodMatcher("java.util.Arrays asList(..)", true);

    @Override
    public String getDisplayName() {
        return "Use `List.of(..)` in Java 9 or higher";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    public String getDescription() {
        return "Replaces `unmodifiableList(java.util.Arrays asList(<args>))` with `List.Of(<args>)`.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return Applicability.and(new UsesJavaVersion<>(9),
                new UsesMethod<>(UNMODIFIABLE_LIST));
    }

    @Override
    protected JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, executionContext);
                if (UNMODIFIABLE_LIST.matches(method)) {
                    if (m.getArguments().get(0) instanceof J.MethodInvocation) {
                        if (ARRAYS_AS_LIST.matches((J.MethodInvocation) m.getArguments().get(0))) {
                            J.MethodInvocation arraysInvocation = (J.MethodInvocation) m.getArguments().get(0);
                            maybeRemoveImport("java.util.Collections");
                            maybeRemoveImport("java.util.Arrays");
                            maybeAddImport("java.util.List");
                            StringJoiner setOf = new StringJoiner(", ", "List.of(", ")");
                            List<Expression> args = arraysInvocation.getArguments();
                            args.forEach(o -> setOf.add("#{any()}"));

                            return autoFormat(m.withTemplate(
                                    JavaTemplate
                                            .builder(this::getCursor, setOf.toString())
                                            .imports("java.util.List")
                                            .build(),
                                    m.getCoordinates().replace(),
                                    args.toArray()
                            ), executionContext);
                        }
                    }
                }
                return m;
            }
        };
    }
}
