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

package se.swedenconnect.ca.headless.ca.db;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectRetrievalFailureException;

import lombok.extern.slf4j.Slf4j;
import se.swedenconnect.ca.engine.revocation.crl.CRLMetadata;

/**
 * Repository for storing and retrieving current CRL metadata from DB
 */
@Slf4j
public class DBCRLMetadataRepository {

  private final DBCRLJPARepository jpaRepository;

  @Autowired
  public DBCRLMetadataRepository(DBCRLJPARepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  public CRLMetadata getCRLMetadata(String instance) {

    DBCRLMetadataRecord crlMdRec;
    try {
      crlMdRec = jpaRepository.getReferenceById(instance);
    } catch (Exception ex) {
      log.warn("Instance {} has no CRL metadata in DB: {}", instance, ex.getMessage());
      return null;
    }

    CRLMetadata.CRLMetadataBuilder builder = CRLMetadata.builder();
    try {
      builder
        .crlNumber(new BigInteger(
          Optional.ofNullable(crlMdRec.getCrlNumber()).orElseThrow(() -> new DBCrlException("CRL number is not present")),
          16
        ))
        .issueTime(Instant.ofEpochMilli(crlMdRec.getIssueTime()))
        .nextUpdate(Instant.ofEpochMilli(crlMdRec.getNextUpdate()))
        .revokedCertCount(crlMdRec.getRevCount());
    }
    catch (Exception e) {
      log.warn("Unable to access existing CRL Metadata från DB: {}", e.getMessage());
      return null;
    }
    return builder.build();
  }

  public void storeCrlMetadata(final CRLMetadata crlMetadata, String instance) {

    Objects.requireNonNull(crlMetadata, "CRL Metadata must not be null");
    Objects.requireNonNull(crlMetadata.getCrlNumber(), "CRL Number must not be null");
    Objects.requireNonNull(crlMetadata.getIssueTime(), "Issue time must not be null");
    Objects.requireNonNull(crlMetadata.getNextUpdate(), "Next update must not be null");

    DBCRLMetadataRecord dbCrlMdRec = new DBCRLMetadataRecord(
      instance,
      crlMetadata.getCrlNumber().toString(16),
      crlMetadata.getIssueTime().toEpochMilli(),
      crlMetadata.getNextUpdate().toEpochMilli(),
      crlMetadata.getRevokedCertCount()
    );
    try {
      jpaRepository.save(dbCrlMdRec);
    } catch (ObjectRetrievalFailureException ex) {
      log.debug("Failed first save attempt casued by missing Instance ID in table - retry");
      jpaRepository.save(dbCrlMdRec);
    }
  }
}
