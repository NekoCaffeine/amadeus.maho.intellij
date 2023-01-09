package amadeus.maho.lang.idea.handler.base;

import java.util.List;

import com.intellij.psi.PsiClassType;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PsiClassesException extends RuntimeException {
    
    List<? extends PsiClassType> classes;
    
    @Override
    public Throwable fillInStackTrace() = this;
    
}
