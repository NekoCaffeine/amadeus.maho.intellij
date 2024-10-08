package amadeus.maho.lang.idea;

import com.intellij.codeInsight.daemon.impl.HighlightingNecromancer;
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.PsiElement;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;

import org.jdom.Element;

@TransformProvider
public interface DisablePersistentStateComponent {

    @Hook(forceReturn = true, exactMatch = false)
    private static FileHighlightingSetting getHighlightingSettingForRoot(final HighlightingSettingsPerFile $this) = FileHighlightingSetting.FORCE_HIGHLIGHTING;

    @Hook(forceReturn = true, exactMatch = false)
    private static void setHighlightingSettingForRoot(final HighlightingSettingsPerFile $this) { }

    @Hook(forceReturn = true)
    private static void loadState(final HighlightingSettingsPerFile $this, final Element element) { }

    @Hook(forceReturn = true)
    private static @Nullable Element getState(final HighlightingSettingsPerFile $this) = null;

    @Hook(forceReturn = true)
    private static boolean shouldHighlight(final HighlightingSettingsPerFile $this, final PsiElement root) = true;

    @Hook(forceReturn = true)
    private static boolean shouldInspect(final HighlightingSettingsPerFile $this, final PsiElement root) = true;

    @Hook(forceReturn = true, exactMatch = false)
    private static boolean runEssentialHighlightingOnly(final HighlightingSettingsPerFile $this) = false;

    @Hook(value = HighlightingNecromancer.Companion.class, forceReturn = true)
    private static boolean isEnabled(final HighlightingNecromancer.Companion $this) = false;

    @Hook(value = HighlightingNecromancer.Companion.class, forceReturn = true)
    private static boolean isZombieMarkup(final HighlightingNecromancer.Companion $this, final RangeMarker highlighter) = false;

    @Hook(value = HighlightingNecromancer.Companion.class, forceReturn = true)
    private static void unmarkZombieMarkup(final HighlightingNecromancer.Companion $this, final RangeMarker highlighter) { }

    @Hook(forceReturn = true)
    private static boolean shouldBuryHighlighter(final HighlightingNecromancer $this, final RangeHighlighterEx highlighter) = false;

    @Hook(forceReturn = true)
    private static boolean putDownActiveZombiesInFile(final HighlightingNecromancer $this, final VirtualFileWithId file, final Document document) = false;

    @Hook(forceReturn = true)
    private static void subscribeDaemonFinished(final HighlightingNecromancer $this) { }

    @Hook(at = @At(field = @At.FieldInsn(name = "project")), before = false, exactMatch = false)
    private static Hook.Result _init_(final HighlightingNecromancer $this) = Hook.Result.NULL;

}
