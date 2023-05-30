import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
open module amadeus.maho.intellij {
    
    requires transitive amadeus.maho;
    
    requires transitive java.desktop;
    
}
