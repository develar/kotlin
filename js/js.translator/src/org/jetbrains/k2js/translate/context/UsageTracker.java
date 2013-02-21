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
package org.jetbrains.k2js.translate.context;

import com.intellij.util.SmartList;
import com.intellij.util.containers.OrderedSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.*;

import java.util.List;

import static org.jetbrains.jet.lang.resolve.DescriptorUtils.isAncestor;

public final class UsageTracker {
    @Nullable
    private final ClassDescriptor trackedClassDescriptor;
    @NotNull
    private final MemberDescriptor memberDescriptor;

    @Nullable
    private final UsageTracker parent;
    @Nullable
    private List<UsageTracker> children;

    private boolean used;
    @Nullable
    private OrderedSet<CallableDescriptor> capturedVariables;
    private ClassDescriptor outerClassDescriptor;

    public UsageTracker(@NotNull MemberDescriptor memberDescriptor, @Nullable UsageTracker parent, @Nullable ClassDescriptor trackedClassDescriptor) {
        this.memberDescriptor = memberDescriptor;
        this.trackedClassDescriptor = trackedClassDescriptor;
        this.parent = parent;
        if (parent != null) {
            parent.addChild(this);
        }
    }

    public boolean isUsed() {
        return used;
    }

    @Nullable
    public ClassDescriptor getOuterClassDescriptor() {
        if (outerClassDescriptor != null || children == null) {
            return outerClassDescriptor;
        }

        for (UsageTracker child : children) {
            ClassDescriptor childOuterClassDescriptor = child.getOuterClassDescriptor();
            if (childOuterClassDescriptor != null) {
                return childOuterClassDescriptor;
            }
        }
        return null;
    }

    private void addChild(UsageTracker child) {
        if (children == null) {
            children = new SmartList<UsageTracker>();
        }
        children.add(child);
    }

    private void addCapturedMember(CallableDescriptor descriptor) {
        if (capturedVariables == null) {
            capturedVariables = new OrderedSet<CallableDescriptor>();
        }
        if (capturedVariables.add(descriptor)) {
            UsageTracker p = parent;
            while (p != null) {
                // track
                boolean isLocalNamedFun = p.memberDescriptor instanceof SimpleFunctionDescriptor && p.memberDescriptor.getName().isSpecial();
                if (isAncestor(p.memberDescriptor, descriptor, !isLocalNamedFun, true)) {
                    break;
                }
                p.addCapturedMember(descriptor);
                p = p.parent;
            }
        }
    }

    public void triggerUsed(DeclarationDescriptor descriptor) {
        if ((descriptor instanceof PropertyDescriptor || descriptor instanceof PropertyAccessorDescriptor)) {
            checkOuterClass(descriptor);
        }
        else if (descriptor instanceof VariableDescriptor) {
            VariableDescriptor variableDescriptor = (VariableDescriptor) descriptor;
            if ((capturedVariables == null || !capturedVariables.contains(variableDescriptor)) &&
                !isAncestor(memberDescriptor, variableDescriptor, true, true)) {
                addCapturedMember(variableDescriptor);
            }
        }
        else if (descriptor instanceof SimpleFunctionDescriptor) {
            CallableDescriptor callableDescriptor = (CallableDescriptor) descriptor;
            if (callableDescriptor.getReceiverParameter() != null) {
                return;
            }

            DeclarationDescriptor containingDeclaration = descriptor.getContainingDeclaration();
            if (containingDeclaration instanceof ClassDescriptor) {
                // skip methods like "plus" — defined in Int class
                if (outerClassDescriptor == null &&
                    (callableDescriptor.getExpectedThisObject() == null || isAncestor(containingDeclaration, memberDescriptor, true, true))) {
                    outerClassDescriptor = (ClassDescriptor) containingDeclaration;
                }
                return;
            }

            // local named function
            if (!(containingDeclaration instanceof ClassOrNamespaceDescriptor) &&
                !isAncestor(memberDescriptor, descriptor, true, true)) {
                addCapturedMember(callableDescriptor);
            }
        }
        else if (descriptor instanceof ClassDescriptor && trackedClassDescriptor == descriptor) {
            used = true;
        }
    }

    private void checkOuterClass(DeclarationDescriptor descriptor) {
        if (outerClassDescriptor == null) {
            DeclarationDescriptor containingDeclaration = descriptor.getContainingDeclaration();
            if (containingDeclaration instanceof ClassDescriptor) {
                outerClassDescriptor = (ClassDescriptor) containingDeclaration;
            }
        }
    }

    @Nullable
    public OrderedSet<CallableDescriptor> getCapturedVariables() {
        return capturedVariables;
    }
}
