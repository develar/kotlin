package org.jetbrains.k2js.translate.intrinsic.functions.patterns;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.CallableMemberDescriptor;
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor;
import org.jetbrains.jet.lang.descriptors.FunctionDescriptor;
import org.jetbrains.jet.lang.descriptors.NamespaceDescriptor;
import org.jetbrains.jet.lang.resolve.DescriptorUtils;

import java.util.Arrays;
import java.util.Set;

public class DescriptorPattern implements DescriptorPredicate {
    private final String[] names;

    private boolean receiverParameterExists;
    private boolean checkOverridden;

    public DescriptorPattern(String... names) {
        this.names = names;
    }

    @Override
    public String toString() {
        return Arrays.toString(names);
    }

    public DescriptorPattern receiverExists() {
        receiverParameterExists = true;
        return this;
    }

    public DescriptorPattern checkOverridden() {
        this.checkOverridden = true;
        return this;
    }

    private static boolean isRootNamespace(DeclarationDescriptor declarationDescriptor) {
        return declarationDescriptor instanceof NamespaceDescriptor && DescriptorUtils
                .isRootNamespace((NamespaceDescriptor) declarationDescriptor);
    }

    private boolean check(FunctionDescriptor functionDescriptor) {
        DeclarationDescriptor descriptor = functionDescriptor.getContainingDeclaration();
        int nameIndex = names.length - 1;
        do {
            if (nameIndex == -1) {
                return isRootNamespace(descriptor);
            }
            else if (isRootNamespace(descriptor) || !descriptor.getName().asString().equals(names[nameIndex--])) {
                return false;
            }
        }
        while ((descriptor = descriptor.getContainingDeclaration()) != null);
        return false;
    }

    @Override
    public boolean apply(@NotNull FunctionDescriptor functionDescriptor) {
        if ((functionDescriptor.getReceiverParameter() == null) == receiverParameterExists) {
            return false;
        }

        DeclarationDescriptor descriptor;
        if (functionDescriptor.getKind() == CallableMemberDescriptor.Kind.FAKE_OVERRIDE) {
            assert functionDescriptor.getOverriddenDescriptors().size() > 0;
            descriptor = functionDescriptor.getOverriddenDescriptors().iterator().next();
        }
        else {
            descriptor = functionDescriptor;
        }

        int nameIndex = names.length - 1;
        while ((descriptor = descriptor.getContainingDeclaration()) != null) {
            if (nameIndex == -1) {
                return isRootNamespace(descriptor);
            }
            else if (isRootNamespace(descriptor)) {
                return false;
            }

            if (!descriptor.getName().asString().equals(names[nameIndex--])) {
                // we check overridden on any mismatch - we can have classes with equal name from different packages
                return checkOverridden && checkOverridden(functionDescriptor);
            }
        }
        return false;
    }

    private boolean checkOverridden(FunctionDescriptor functionDescriptor) {
        Set<? extends FunctionDescriptor> overriddenDescriptors = functionDescriptor.getOverriddenDescriptors();
        if (overriddenDescriptors.isEmpty()) {
            return false;
        }

        for (FunctionDescriptor overridden : overriddenDescriptors) {
            if (overridden.getKind() == CallableMemberDescriptor.Kind.FAKE_OVERRIDE) {
                for (FunctionDescriptor realOverridden : overridden.getOverriddenDescriptors()) {
                    if (check(realOverridden) || checkOverridden(realOverridden)) {
                        return true;
                    }
                }
            }
            else if (check(overridden) || checkOverridden(overridden)) {
                return true;
            }
        }
        return false;
    }
}
