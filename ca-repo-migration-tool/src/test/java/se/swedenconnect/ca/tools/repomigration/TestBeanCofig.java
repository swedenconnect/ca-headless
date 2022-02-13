package se.swedenconnect.ca.tools.repomigration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import se.swedenconnect.ca.tools.repomigration.repo.RepositoryGroup;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Description
 *
 * @author Martin Lindström (martin@idsec.se)
 * @author Stefan Santesson (stefan@idsec.se)
 */
@Configuration
public class TestBeanCofig {

  @Bean
  @Profile("test")
  Map<String, RepositoryGroup> caRepositoryMap () throws IOException {
    return new HashMap<>();
  }

}
