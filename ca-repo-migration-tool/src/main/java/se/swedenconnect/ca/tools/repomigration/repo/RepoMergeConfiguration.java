package se.swedenconnect.ca.tools.repomigration.repo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;
import java.util.List;

/**
 * Description
 *
 * @author Martin Lindström (martin@idsec.se)
 * @author Stefan Santesson (stefan@idsec.se)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RepoMergeConfiguration {

  private File dataLocation;
  List<String> instanceNameList;


}
