package amadeus.maho.lang.idea.debugger;

import java.util.List;

import com.sun.jdi.ArrayType;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.CharValue;
import com.sun.jdi.DoubleValue;
import com.sun.jdi.FloatValue;
import com.sun.jdi.IntegerValue;
import com.sun.jdi.LongValue;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.ShortValue;
import com.sun.jdi.StringReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Type;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;

import amadeus.maho.lang.SneakyThrows;
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
    
    static Object value(final Value value) = switch (value) {
        case BooleanValue booleanValue       -> booleanValue.booleanValue();
        case CharValue charValue             -> charValue.charValue();
        case DoubleValue doubleValue         -> doubleValue.doubleValue();
        case FloatValue floatValue           -> floatValue.floatValue();
        case IntegerValue integerValue       -> integerValue.intValue();
        case LongValue longValue             -> longValue.longValue();
        case ShortValue shortValue           -> shortValue.shortValue();
        case StringReference stringReference -> stringReference.value();
        default                              -> value;
    };
    
    @SneakyThrows
    static int getEnumOrdinal(final ThreadReference thread, final ObjectReference enumObj) = ((IntegerValue) enumObj.invokeMethod(thread, enumObj.referenceType().methodsByName("ordinal").get(0), List.of(), 0)).intValue();
    
}
