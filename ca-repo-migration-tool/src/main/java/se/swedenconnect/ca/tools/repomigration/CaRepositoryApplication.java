package se.swedenconnect.ca.tools.repomigration;

import org.apache.commons.cli.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import se.swedenconnect.ca.tools.repomigration.merge.DatabaseMerger;
import se.swedenconnect.ca.tools.repomigration.options.AppOptions;

import java.io.File;

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
