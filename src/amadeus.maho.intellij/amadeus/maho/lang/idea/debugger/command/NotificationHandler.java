package amadeus.maho.lang.idea.debugger.command;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.xdebugger.impl.XDebugSessionImpl;

import amadeus.maho.vm.JDWP;

@CommandHandler(JDWP.IDECommand.Notification.class)
public class NotificationHandler implements Handler<JDWP.IDECommand.Notification> {
    
    @Override
    public void handle(final JDWP.IDECommand.Notification command, final XDebugSessionImpl session) {
        final Notification notification = { "amadeus.maho", command.title(), command.content(), Enum.valueOf(NotificationType.class, command.type().name()) };
        // noinspection deprecation
        notification.setListener(NotificationListener.URL_OPENING_LISTENER);
        notification.notify(session.getProject());
    }
    
}
