package amadeus.maho.lang.idea.debugger.command;

import com.intellij.xdebugger.impl.XDebugSessionImpl;

import amadeus.maho.vm.JDWP;

public interface Handler<C extends JDWP.IDECommand> {
    
    void handle(C command, final XDebugSessionImpl session);
    
}
