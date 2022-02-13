package se.swedenconnect.ca.tools.repomigration.repo;

import se.swedenconnect.ca.engine.ca.repository.CARepository;
import se.swedenconnect.ca.engine.ca.repository.CertificateRecord;

import java.io.IOException;

/**
 * Description
 *
 * @author Martin Lindström (martin@idsec.se)
 * @author Stefan Santesson (stefan@idsec.se)
 */
public interface MergeCARepository extends CARepository {

  void addCertificateRecord(CertificateRecord certificateRecord) throws IOException;

  }
