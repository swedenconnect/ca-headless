/*
 * Copyright (c) 2022.  Agency for Digital Government (DIGG)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package se.swedenconnect.ca.tools.repomigration;

import org.apache.commons.cli.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import se.swedenconnect.ca.tools.repomigration.merge.DatabaseMerger;
import se.swedenconnect.ca.tools.repomigration.options.AppOptions;

import java.io.File;

/**
 * Main application for this CLI Spring Boot application that is executed using java -jar repomigrate.jar [options]
 *
 * Help menu is available through java -jar repomigrate.jar -help
 *
 * @author Martin Lindström (martin@idsec.se)
 * @author Stefan Santesson (stefan@idsec.se)
 */
@SpringBootApplication()
public class CaRepositoryApplication implements CommandLineRunner {

  private final DatabaseMerger databaseMerger;

  @Autowired
  public CaRepositoryApplication(DatabaseMerger databaseMerger) {
    this.databaseMerger = databaseMerger;
  }

  public static void main(String[] args) throws ParseException {

    CommandLineParser parser = new DefaultParser();
    Options opt = AppOptions.getOptions();
    CommandLine cmd = parser.parse(opt, args);

    if (cmd.hasOption(AppOptions.OPTION_HELP)){
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp("java -jar repomigrate.jar [options]", AppOptions.getOptions());
      return;
    }

    if (cmd.hasOption(AppOptions.OPTION_LOG)){
      /*
        #logging.level.root=WARN
        #logging.level.se.swedenconnect.ca.tools.repomigration.CaRepositoryApplication = WARN
       */
      System.setProperty("logging.level.root", "INFO");
      System.setProperty("logging.level.se.swedenconnect.ca.tools.repomigration.CaRepositoryApplication", "INFO");
      System.setProperty("logging.level.se.swedenconnect.ca.tools.repomigration", "INFO");
    }

    File configDir;
    if (cmd.hasOption(AppOptions.OPTION_DIR)){
      final String optionValue = cmd.getOptionValue(AppOptions.OPTION_DIR);
      configDir = new File(optionValue);
      if (!configDir.exists()){
        System.out.println("provided config dir does not exist: " + optionValue);
        return;
      }
    } else {
      configDir = new File(System.getProperty("user.dir"));
      System.out.println("No config dir specified. Using working dir: " + configDir.getAbsolutePath());
    }

    System.setProperty("spring.config.additional-location", configDir.getAbsolutePath() + "/");
    SpringApplication.run(CaRepositoryApplication.class, args);
  }

  @Override public void run(String... args) throws Exception {
    databaseMerger.run(args);
  }

}
