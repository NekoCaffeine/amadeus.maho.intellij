package amadeus.maho.lang.idea.support.ddlc;

import java.security.ProtectionDomain;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.ClassTransformer;
import amadeus.maho.transform.mark.base.Transformer;
import amadeus.maho.util.bytecode.context.TransformContext;
import amadeus.maho.util.bytecode.remap.ClassNameRemapper;

@Transformer
public class DDLCTransformer implements ClassTransformer {
    
    private static final Set<String> targets = Set.of("io.unthrottled.doki.icons.jetbrains.themes.", "io.unthrottled.doki.icon.ColorPatcher", "io.unthrottled.doki.themes.impl.ThemeManagerImpl");
    
    private static final String srcName = "com/intellij/ide/ui/laf/UIThemeBasedLookAndFeelInfo", newName = "com/intellij/ide/ui/laf/UIThemeLookAndFeelInfoImpl";
    
    @Nullable
    @Override
    public ClassNode transform(final TransformContext context, @Nullable final ClassNode node, @Nullable final ClassLoader loader, @Nullable final Class<?> clazz, @Nullable final ProtectionDomain domain) {
        if (node != null) {
            context.markModified();
            return ClassNameRemapper.changeName(node, srcName, newName);
        }
        return null;
    }
    
    @Override
    public boolean isTarget(@Nullable final ClassLoader loader, final String name) = targets.stream().anyMatch(name::startsWith);
    
}
