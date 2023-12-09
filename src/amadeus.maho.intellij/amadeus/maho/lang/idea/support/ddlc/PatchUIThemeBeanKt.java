package amadeus.maho.lang.idea.support.ddlc;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;

import com.intellij.ide.ui.UIThemeBean;
import com.intellij.ide.ui.UIThemeBeanKt;

import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import static com.intellij.ide.ui.ColorMapKt.readColorMapFromJson;

@TransformProvider
public class PatchUIThemeBeanKt {
    
    @SneakyThrows
    @Hook(value = UIThemeBeanKt.class, isStatic = true, forceReturn = true)
    private static UIThemeBean readTheme(final JsonParser parser) {
        if (parser.nextToken() != JsonToken.START_OBJECT)
            throw new IllegalStateException();
        final UIThemeBean bean = { };
        final Class<? extends UIThemeBean> beanClass = bean.getClass();
        int objectCount = 0;
        while (true)
            switch (parser.nextToken()) {
                case START_OBJECT -> {
                    objectCount++;
                    final String name = parser.currentName();
                    switch (name) {
                        case "icons"                 -> bean.icons = (Privilege) UIThemeBeanKt.readMapFromJson(parser);
                        case "background"            -> bean.background = (Privilege) UIThemeBeanKt.readMapFromJson(parser);
                        case "emptyFrameBackground"  -> bean.emptyFrameBackground = (Privilege) UIThemeBeanKt.readMapFromJson(parser);
                        case "colors"                -> bean.colorMap.rawMap = readColorMapFromJson(parser, new HashMap<>());
                        case "iconColorsOnSelection" -> bean.iconColorOnSelectionMap.rawMap = readColorMapFromJson(parser, new HashMap<>());
                        case "ui"                    -> {
                            final LinkedHashMap<String, Object> map = { 700 };
                            (Privilege) UIThemeBeanKt.readFlatMapFromJson(parser, map);
                            (Privilege) UIThemeBeanKt.putDefaultsIfAbsent(map);
                            bean.ui = map;
                        }
                        case "UIDesigner"            -> parser.skipChildren();
                    }
                }
                case END_OBJECT   -> objectCount--;
                case VALUE_STRING -> {
                    if (objectCount == 0) {
                        final String name = parser.currentName();
                        if (!name.equals("id"))
                            try {
                                final Field field = beanClass.getDeclaredField(name);
                                if (field.getType() == String.class)
                                    field.set(bean, parser.getValueAsString());
                            } catch (final NoSuchFieldException ignored) { }
                    }
                }
                case null         -> {
                    (Privilege) UIThemeBeanKt.putDefaultsIfAbsent(bean);
                    return bean;
                }
                default           -> { }
            }
    }
}
