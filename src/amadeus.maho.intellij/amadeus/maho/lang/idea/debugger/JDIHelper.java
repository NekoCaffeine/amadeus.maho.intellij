package amadeus.maho.lang.idea.debugger;

import java.util.List;

import com.sun.jdi.ArrayType;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.Type;
import com.sun.jdi.VirtualMachine;

import amadeus.maho.lang.inspection.Nullable;

public interface JDIHelper {
    
    static boolean isLambda(final @Nullable Type type) = type?.name()?.contains("$$Lambda$") ?? false;
    
    static ReferenceType componentType(final ReferenceType ref) {
        if (!(ref instanceof ArrayType))
            return ref;
        final String elementTypeName = ref.name().replace("[]", "");
        final VirtualMachine vm = ref.virtualMachine();
        final List<ReferenceType> referenceTypes = vm.classesByName(elementTypeName);
        if (referenceTypes.size() == 1)
            return referenceTypes.get(0);
        return ref;
    }
    
}
