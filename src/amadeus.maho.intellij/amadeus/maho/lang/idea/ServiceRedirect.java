package amadeus.maho.lang.idea;

import java.util.List;
import java.util.Map;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.impl.XmlExtensionAdapter;
import com.intellij.refactoring.suggested.SuggestedRefactoringProvider;
import com.intellij.refactoring.suggested.SuggestedRefactoringProviderImpl;
import com.intellij.serviceContainer.ComponentManagerImpl;

import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
interface ServiceRedirect {
    
    class FakeSuggestedRefactoringProvider implements SuggestedRefactoringProvider {
        
        @Override
        public void reset() { }
        
    }
    
    @Hook(exactMatch = false)
    private static Hook.Result execute(final SuggestedRefactoringProviderImpl.Startup $this) = Hook.Result.NULL;
    
    Map<Class<?>, Object> redirectIntellectualDisability = Map.of(SuggestedRefactoringProvider.class, new FakeSuggestedRefactoringProvider());
    
    @Hook
    private static <T> Hook.Result doGetService(final ComponentManagerImpl $this, final Class<T> serviceClass, final boolean createIfNeeded) = Hook.Result.nullToVoid(redirectIntellectualDisability[serviceClass]);
    
    List<String> disabledExtensionImplementation = List.of("com.intellij.refactoring.suggested.SuggestedRefactoringIntentionContributor");
    
    @Hook
    private static <T> Hook.Result createInstance(final XmlExtensionAdapter $this, final ComponentManager manager)
            = Hook.Result.falseToVoid($this.implementationClassOrName instanceof String name && disabledExtensionImplementation.contains(name), null);
    
}
