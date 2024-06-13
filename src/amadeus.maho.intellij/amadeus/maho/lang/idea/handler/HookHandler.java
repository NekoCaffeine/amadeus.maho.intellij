package amadeus.maho.lang.idea.handler;

import java.util.List;
import java.util.stream.Stream;

import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteral;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.util.TypeConversionUtil;

import amadeus.maho.lang.idea.handler.base.BaseHandler;
import amadeus.maho.lang.idea.handler.base.Handler;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.InvisibleType;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.runtime.ArrayHelper;

import static amadeus.maho.lang.idea.IDEAContext.lookupClassType;

@Handler(Hook.class)
public class HookHandler extends BaseHandler<Hook> {
    
    @Override
    public void check(final PsiElement tree, final Hook annotation, final PsiAnnotation annotationTree, final ProblemsHolder holder, final QuickFixFactory quickFix) {
        if (tree instanceof PsiMethod method) {
            final @Nullable PsiTypeElement returnTypeElement = method.getReturnTypeElement();
            final PsiParameter parameters[] = method.getParameterList().getParameters();
            if (returnTypeElement != null) {
                if (!method.hasModifierProperty(PsiModifier.STATIC))
                    holder.registerProblem(returnTypeElement, STR."The target method of the @\{Hook.class.getSimpleName()} must be static.", ProblemHighlightType.GENERIC_ERROR,
                            quickFix.createModifierListFix(method, PsiModifier.STATIC, true, false));
                if (switch (method.getReturnType()) {
                    case PsiClassType classType -> !(classType.resolve()?.getQualifiedName()?.equals(Hook.Result.class.getCanonicalName()) ?? false);
                    case null,
                         default                -> true;
                } && Stream.of(parameters).anyMatch(parameter -> parameter.hasAnnotation(Hook.Reference.class.getCanonicalName())))
                    holder.registerProblem(returnTypeElement, STR."@\{Hook.Reference.class.getSimpleName()} needs to pass the result by returning value type \{Hook.Result.class.getSimpleName()}.", ProblemHighlightType.GENERIC_ERROR,
                            quickFix.createMethodReturnFix(method, JavaPsiFacade.getElementFactory(method.getProject()).createTypeByFQClassName(Hook.Result.class.getCanonicalName(), method.getResolveScope()), false));
            }
            final String name = annotation.selector().isEmpty() ? switch (At.Lookup.dropInvalidPart(method.getName())) {
                case "_init_"      -> ASMHelper._INIT_;
                case "_clinit_"    -> ASMHelper._CLINIT_;
                case String string -> string;
            } : annotation.selector();
            if (!At.Lookup.WILDCARD.equals(name) && !ASMHelper._CLINIT_.equals(name)) {
                final List<PsiType> types = Stream.of(parameters)
                        .map(HookHandler::mapInvisibleType)
                        .toList();
                if (!types[null]) {
                    final boolean isStatic = annotation.isStatic();
                    final int targetIndex = isStatic ? -1 : annotation.capture() ? 1 : 0;
                    final @Nullable PsiClass target = (annotationTree.hasAttribute("value") ? annotation.accessPsiClass(Hook::value) :
                            annotationTree.hasAttribute("target") ? lookupClassType(tree, annotation.target()) : !isStatic ? types[targetIndex] : null) instanceof PsiClassType classType ? classType.resolve() : null;
                    if (target != null) {
                        final @Nullable PsiMethod candidate;
                        if (annotation.exactMatch()) {
                            final @Nullable PsiParameter firstLocalVarParameter = ~Stream.of(parameters).filter(parameter -> parameter.hasAnnotation(Hook.LocalVar.class.getCanonicalName()));
                            final int lastArgIndex = firstLocalVarParameter == null ? parameters.length : ArrayHelper.indexOf(parameters, firstLocalVarParameter);
                            final int firstArgIndex = isStatic ? annotation.capture() ? 1 : 0 : targetIndex + 1;
                            final List<PsiType> argTypes = Stream.of(ArrayHelper.sub(parameters, firstArgIndex, lastArgIndex)).map(PsiParameter::getType).map(TypeConversionUtil::erasure).toList();
                            candidate = ~Stream.of(ASMHelper._INIT_.equals(name) ? target.getConstructors() : target.findMethodsByName(name, false))
                                    .filter(it -> {
                                        final PsiParameterList parameterList = it.getParameterList();
                                        return parameterList.getParametersCount() == argTypes.size() && Stream.of(parameterList.getParameters()).map(PsiParameter::getType).map(TypeConversionUtil::erasure).toList().equals(argTypes);
                                    });
                        } else
                            candidate = ~Stream.of(ASMHelper._INIT_.equals(name) ? target.getConstructors() : target.findMethodsByName(name, false));
                        if (candidate == null)
                            holder.registerProblem(annotationTree, "Missing Hook target method", ProblemHighlightType.GENERIC_ERROR);
                    } else
                        holder.registerProblem(annotationTree, "Missing Hook target class", ProblemHighlightType.GENERIC_ERROR);
                }
            }
        }
    }
    
    private static @Nullable PsiType mapInvisibleType(final PsiParameter parameter) {
        final @Nullable PsiAnnotation annotation = parameter.getAnnotation(InvisibleType.class.getName());
        return annotation != null ? lookupClassType(parameter, annotation.findAttributeValue("value") instanceof PsiLiteral literal && literal.getValue() instanceof String value ? value : null) : parameter.getType();
    }
    
}
