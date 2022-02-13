/*
 * Copyright (c) 2021. Agency for Digital Government (DIGG)
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

package se.swedenconnect.ca.tools.repomigration.repo.db;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Description
 *
 * @author Martin Lindström (martin@idsec.se)
 * @author Stefan Santesson (stefan@idsec.se)
 */
public interface DBJPARepository extends JpaRepository<DBCertificateRecord, String> {

  // Get a particular cert from a particular instance
  List<DBCertificateRecord> findByInstanceAndSerialNumber(String instance, String certSerial);
  // Get a page of certs from an instance
  Page<DBCertificateRecord> findByInstance(String string, Pageable pageable);
  // Get a page of certs either revoked or non-revoked
  Page<DBCertificateRecord> findByInstanceAndRevoked(String instance, boolean revoked, Pageable pageable);
  // Get all expired certificates by page
  Page<DBCertificateRecord> findByInstanceAndExpiryDateLessThan(String instance, long maxExpiryDate, Pageable pageable);

  //Variations of getting pages of data with or without exclusion of revoked certs and with different sort by and sort directions
  Page<DBCertificateRecord> findByInstanceOrderByIssueDateAsc(String instance, Pageable pageable);
  Page<DBCertificateRecord> findByInstanceOrderByIssueDateDesc(String instance, Pageable pageable);
  Page<DBCertificateRecord> findByInstanceAndRevokedOrderByIssueDateAsc(String instance, boolean revoked, Pageable pageable);
  Page<DBCertificateRecord> findByInstanceAndRevokedOrderByIssueDateDesc(String instance, boolean revoked, Pageable pageable);
  Page<DBCertificateRecord> findByInstanceOrderBySerialNumberAsc(String instance, Pageable pageable);
  Page<DBCertificateRecord> findByInstanceOrderBySerialNumberDesc(String instance, Pageable pageable);
  Page<DBCertificateRecord> findByInstanceAndRevokedOrderBySerialNumberAsc(String instance, boolean revoked, Pageable pageable);
  Page<DBCertificateRecord> findByInstanceAndRevokedOrderBySerialNumberDesc(String instance, boolean revoked, Pageable pageable);

  // Certificate counters - all certs
  int countByInstance(String instance);
  // Count revoked or non-revoked certs
  int countByInstanceAndRevoked(String instance, boolean revoked);

  // delete expired certificates
  @Transactional
  int deleteByInstanceAndSerialNumber(String instance, String certSerial);

}
