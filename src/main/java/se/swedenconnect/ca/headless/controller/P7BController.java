/*
 * Copyright (c) 2021-2023.  Agency for Digital Government (DIGG)
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

package se.swedenconnect.ca.headless.controller;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.swedenconnect.ca.headless.ca.P7BCertStore;

import java.io.InputStream;

/**
 * Controller for getting the list of valid issued certificates in the form of a PKCS7 certs only file (.p7b)
 *
 * @author Martin Lindström (martin@idsec.se)
 * @author Stefan Santesson (stefan@idsec.se)
 */
@Slf4j
@RestController
public class P7BController {

  private final P7BCertStore p7bCertStore;

  @Autowired
  public P7BController(P7BCertStore p7bCertStore) {
    this.p7bCertStore = p7bCertStore;
  }

  @RequestMapping(value = "/certs/{p7bFileName}")
  public ResponseEntity<InputStreamResource> getP7bCertStoreFile(@PathVariable("p7bFileName") String p7bFileName) {
    if (StringUtils.isBlank(p7bFileName) || !p7bFileName.endsWith(".p7b") || p7bFileName.length() < 5) {
      log.debug("False request for P7B - specifying the caRepository file name {}", p7bFileName);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
    String instance = p7bFileName.substring(0, p7bFileName.length() - 4);
    InputStream p7bResource;
    try {
      p7bResource = p7bCertStore.getCertStoreP7bBytes(instance);
    } catch (Exception ex) {
      log.debug("No caRepository resource found for instance {}", instance);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
    log.trace("Request for P7B caRepository file received for instance {}", instance);

    return ResponseEntity
      .ok()
      .headers(getHeaders(p7bFileName))
      .contentType(MediaType.parseMediaType("application/octet-stream"))
      .body(new InputStreamResource(p7bResource));
  }

  private HttpHeaders getHeaders(String fileName) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
    headers.add("content-disposition", "attachment; filename=" + fileName);
    headers.add("Pragma", "no-cache");
    headers.add("Expires", "0");
    return headers;
  }

}
