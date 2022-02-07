package se.swedenconnect.ca.headless;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(locations = "classpath:application-test.properties")
@SpringBootTest
@ActiveProfiles("mock")
class HeadlessCaApplicationTests {

  @Test
  void contextLoads() {
  }

}
