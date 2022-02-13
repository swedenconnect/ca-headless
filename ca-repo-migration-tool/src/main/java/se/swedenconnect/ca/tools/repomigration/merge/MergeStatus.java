package se.swedenconnect.ca.tools.repomigration.merge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.List;

/**
 * Description
 *
 * @author Martin Lindström (martin@idsec.se)
 * @author Stefan Santesson (stefan@idsec.se)
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MergeStatus {
  private List<BigInteger> dbCertsMissingInJson;
  private List<BigInteger> jsonCertsMissingInDb;
  private List<BigInteger> duplicateRecords;
}
