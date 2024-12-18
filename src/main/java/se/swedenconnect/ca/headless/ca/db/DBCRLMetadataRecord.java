/*
 * Copyright (c) 2022-2023.  Agency for Digital Government (DIGG)
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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Description
 *
 * @author Martin Lindström (martin@idsec.se)
 * @author Stefan Santesson (stefan@idsec.se)
 */
@Entity
@Table(name = "crl_metadata")
@NoArgsConstructor
@AllArgsConstructor
public class DBCRLMetadataRecord {

  @Id
  @Column(name = "instance")
  @Getter private String instance;

  @Column(name = "crl_number")
  @Getter private String crlNumber;

  @Column(name = "issue_time")
  @Getter private long issueTime;

  @Column(name = "next_update")
  @Getter private long nextUpdate;

  @Column(name = "rev_count")
  @Getter private int revCount;

}
