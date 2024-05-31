package amadeus.maho.lang.idea.light;

import com.intellij.psi.PsiMember;

public sealed interface LightBridgeElement extends PsiMember permits LightBridgeField, LightBridgeMethod { }
