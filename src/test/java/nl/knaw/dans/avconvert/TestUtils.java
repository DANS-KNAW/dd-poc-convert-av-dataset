package nl.knaw.dans.avconvert;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

// copied from dans-layer-store-lib which is a matured version of dd-manage-deposit
public class TestUtils {

    public static ByteArrayOutputStream captureStdout() {
        var outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
        return outContent;
    }

    public static ListAppender<ILoggingEvent> captureLog(Level debug, String loggerName) {
        var logger = (Logger) LoggerFactory.getLogger(loggerName);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.setLevel(debug);
        logger.addAppender(listAppender);
        return listAppender;
    }
}
