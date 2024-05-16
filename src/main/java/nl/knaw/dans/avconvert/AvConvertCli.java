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

import lombok.extern.slf4j.Slf4j;
import nl.knaw.dans.avconvert.command.ExampleCommand;
import nl.knaw.dans.avconvert.config.AvConvertConfig;
import nl.knaw.dans.avconvert.core.Converter;
import nl.knaw.dans.lib.util.AbstractCommandLineApp;
import nl.knaw.dans.lib.util.CliVersionProvider;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.nio.file.Path;

@Command(name = "convert-av-dataset",
         mixinStandardHelpOptions = true,
         versionProvider = CliVersionProvider.class,
         description = "Convert an AV dataset.")
@Slf4j
public class AvConvertCli extends AbstractCommandLineApp<AvConvertConfig> {
    public static void main(String[] args) throws Exception {
        new AvConvertCli().run(args);
    }

    @CommandLine.Parameters(index = "0",
                            paramLabel = "INPUT_DIR",
                            description = "The directory containing the AV dataset.")
    private Path inputDir;

    @CommandLine.Parameters(index = "1",
                            paramLabel = "MAPPING_CSV",
                            description = "File with columns 'easy-file-id', 'path-in-AV-dir', 'path-in-springfield-dir'")
    private Path mapping;

    @CommandLine.Parameters(index = "2",
                            paramLabel = "OUTPUT_DIR",
                            description = "The directory where the converted dataset will be stored.")
    private Path outputDir;

    public String getName() {
        return "DD Convert AV Dataset";
    }

    @Override
    public void configureCommandLine(CommandLine commandLine, AvConvertConfig config) {
        log.debug("Configuring command line");
        commandLine.addSubcommand(new ExampleCommand());
    }

    @Override
    public Integer call() {
        new Converter().convert(inputDir, mapping, outputDir);
        return 0;
    }
}
