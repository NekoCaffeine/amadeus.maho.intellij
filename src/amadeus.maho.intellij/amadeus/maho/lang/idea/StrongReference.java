package amadeus.maho.lang.idea;

import java.lang.ref.WeakReference;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StrongReference<T> extends WeakReference<T> {
    
    T referent;
    
    public StrongReference(final T referent) {
        super(referent);
        this.referent = referent;
    }
    
}
