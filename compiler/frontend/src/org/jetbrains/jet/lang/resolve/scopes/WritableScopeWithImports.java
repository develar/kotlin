/*
 * Copyright 2010-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.jet.lang.resolve.scopes;

import com.google.common.collect.Lists;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.OrderedSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.*;
import org.jetbrains.jet.lang.resolve.name.Name;

import java.util.*;

public abstract class WritableScopeWithImports extends JetScopeAdapter implements WritableScope {

    @NotNull
    private final String debugName;

    private List<JetScope> imports = Collections.emptyList();
    private WritableScope currentIndividualImportScope;
    protected final RedeclarationHandler redeclarationHandler;
    private List<ReceiverParameterDescriptor> implicitReceiverHierarchy;

    public WritableScopeWithImports(@NotNull JetScope scope, @NotNull RedeclarationHandler redeclarationHandler, @NotNull String debugName) {
        super(scope);
        this.redeclarationHandler = redeclarationHandler;
        this.debugName = debugName;
    }



    private LockLevel lockLevel = LockLevel.WRITING;

    @Override
    public WritableScope changeLockLevel(LockLevel lockLevel) {
        if (lockLevel.ordinal() < this.lockLevel.ordinal()) {
            throw new IllegalStateException("cannot lower lock level from " + this.lockLevel + " to " + lockLevel + " at " + toString());
        }
        this.lockLevel = lockLevel;
        return this;
    }

    protected void checkMayRead() {
        if (lockLevel != LockLevel.READING && lockLevel != LockLevel.BOTH) {
            throw new IllegalStateException("cannot read with lock level " + lockLevel + " at " + toString());
        }
    }

    protected void checkMayWrite() {
        if (lockLevel != LockLevel.WRITING && lockLevel != LockLevel.BOTH) {
            throw new IllegalStateException("cannot write with lock level " + lockLevel + " at " + toString());
        }
    }
    
    protected void checkMayNotWrite() {
        if (lockLevel == LockLevel.WRITING || lockLevel == LockLevel.BOTH) {
            throw new IllegalStateException("cannot write with lock level " + lockLevel + " at " + toString());
        }
    }

    @NotNull
    protected final List<JetScope> getImports() {
        return imports;
    }

    @Override
    public void importScope(@NotNull JetScope imported) {
        if (imported == this) {
            throw new IllegalStateException("cannot import scope into self");
        }

        checkMayWrite();

        if (imports == Collections.<JetScope>emptyList()) {
            imports = new SmartList<JetScope>(imported);
        }
        else {
            imports.add(0, imported);
        }
        currentIndividualImportScope = null;
    }

    @NotNull
    @Override
    public List<ReceiverParameterDescriptor> getImplicitReceiversHierarchy() {
        checkMayRead();

        if (implicitReceiverHierarchy == null) {
            implicitReceiverHierarchy = computeImplicitReceiversHierarchy();
        }
        return implicitReceiverHierarchy;
    }

    protected List<ReceiverParameterDescriptor> computeImplicitReceiversHierarchy() {
        List<ReceiverParameterDescriptor> implicitReceiverHierarchy = Lists.newArrayList();
        // Imported scopes come with their receivers
        // Example: class member resolution scope imports a scope of it's class object
        //          members of the class object must be able to find it as an implicit receiver
        for (JetScope scope : imports) {
            implicitReceiverHierarchy.addAll(scope.getImplicitReceiversHierarchy());
        }
        implicitReceiverHierarchy.addAll(super.getImplicitReceiversHierarchy());
        return implicitReceiverHierarchy;
    }

    @NotNull
    @Override
    public Set<VariableDescriptor> getProperties(@NotNull Name name) {
        checkMayRead();

        if (imports.isEmpty()) {
            return Collections.emptySet();
        }
        Set<VariableDescriptor> properties = new OrderedSet<VariableDescriptor>();
        collectPropertiesFromImports(name, properties);
        return properties;
    }

    protected void collectPropertiesFromImports(Name name, Set<VariableDescriptor> properties) {
        for (JetScope imported : imports) {
            properties.addAll(imported.getProperties(name));
        }
    }

    @Override
    public VariableDescriptor getLocalVariable(@NotNull Name name) {
        checkMayRead();

        // Meaningful lookup goes here
        for (JetScope imported : imports) {
            VariableDescriptor importedDescriptor = imported.getLocalVariable(name);
            if (importedDescriptor != null) {
                return importedDescriptor;
            }
        }
        return null;
    }

    @NotNull
    @Override
    public Collection<FunctionDescriptor> getFunctions(@NotNull Name name) {
        checkMayRead();

        if (imports.isEmpty()) {
            return Collections.emptySet();
        }
        Set<FunctionDescriptor> result = new OrderedSet<FunctionDescriptor>();
        collectFunctionsFromImports(name, result);
        return result;
    }

    protected void collectFunctionsFromImports(Name name, Set<FunctionDescriptor> result) {
        for (JetScope imported : imports) {
            result.addAll(imported.getFunctions(name));
        }
    }

    @Override
    public ClassifierDescriptor getClassifier(@NotNull Name name) {
        checkMayRead();

        for (JetScope imported : imports) {
            ClassifierDescriptor importedClassifier = imported.getClassifier(name);
            if (importedClassifier != null) {
                return importedClassifier;
            }
        }
        return null;
    }

    @Override
    public ClassDescriptor getObjectDescriptor(@NotNull Name name) {
        checkMayRead();

        for (JetScope imported : imports) {
            ClassDescriptor objectDescriptor = imported.getObjectDescriptor(name);
            if (objectDescriptor != null) {
                return objectDescriptor;
            }
        }
        return null;
    }

    @Override
    public <P extends Processor<NamespaceDescriptor>> boolean processNamespaces(@NotNull Name name, @NotNull P processor) {
        checkMayRead();

        for (JetScope imported : imports) {
            if (!imported.processNamespaces(name, processor)) {
                return false;
            }
        }
        return true;
    }

    private WritableScope getCurrentIndividualImportScope() {
        if (currentIndividualImportScope == null) {
            WritableScopeImpl writableScope = new WritableScopeImpl(EMPTY, getContainingDeclaration(), RedeclarationHandler.DO_NOTHING, "Individual import scope");
            writableScope.changeLockLevel(LockLevel.BOTH);
            importScope(writableScope);
            currentIndividualImportScope = writableScope;
        }
        return currentIndividualImportScope;
    }

    @Override
    public void importClassifierAlias(@NotNull Name importedClassifierName, @NotNull ClassifierDescriptor classifierDescriptor) {
        checkMayWrite();

        getCurrentIndividualImportScope().addClassifierAlias(importedClassifierName, classifierDescriptor);
    }
    
    
    @Override
    public void importNamespaceAlias(@NotNull Name aliasName, @NotNull NamespaceDescriptor namespaceDescriptor) {
        checkMayWrite();

        getCurrentIndividualImportScope().addNamespaceAlias(aliasName, namespaceDescriptor);
    }

    @Override
    public void importFunctionAlias(@NotNull Name aliasName, @NotNull FunctionDescriptor functionDescriptor) {
        checkMayWrite();

        getCurrentIndividualImportScope().addFunctionAlias(aliasName, functionDescriptor);
    }

    @Override
    public void importVariableAlias(@NotNull Name aliasName, @NotNull VariableDescriptor variableDescriptor) {
        checkMayWrite();

        getCurrentIndividualImportScope().addVariableAlias(aliasName, variableDescriptor);
    }

    @Override
    public void clearImports() {
        currentIndividualImportScope = null;
        if (!imports.isEmpty()) {
            imports.clear();
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(this)) + " " + debugName + " for " + getContainingDeclaration();
    }

}
