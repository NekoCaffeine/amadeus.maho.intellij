package amadeus.maho.lang.idea;

import java.util.Set;

import com.intellij.ide.highlighter.DTDFileType;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XHtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.json.JsonFileType;
import com.intellij.json.json5.Json5FileType;
import com.intellij.json.jsonLines.JsonLinesFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.psi.impl.cache.impl.id.IdIndex;

import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public interface DisableFileTypeIndex {
    
    Set<FileType> disableIndexFileTypes = Set.of(
            PlainTextFileType.INSTANCE,
            DTDFileType.INSTANCE,
            HtmlFileType.INSTANCE,
            XHtmlFileType.INSTANCE,
            XmlFileType.INSTANCE,
            JsonFileType.INSTANCE,
            Json5FileType.INSTANCE,
            JsonLinesFileType.INSTANCE
    );
    
    @Hook(value = IdIndex.class, isStatic = true)
    private static Hook.Result isIndexable(final FileType fileType) = Hook.Result.trueToVoid(!disableIndexFileTypes[fileType]);
    
}
