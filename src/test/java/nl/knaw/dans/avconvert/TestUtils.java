/*
 * Copyright (C) 2024 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
