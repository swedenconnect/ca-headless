# Headless CA versions

**Latest current version 1.1.5**

| Version | Comment                                                                              | Date       |
|---------|--------------------------------------------------------------------------------------|------------|
| 1.0.0   | Initial version                                                                      | 2022-02-07 |
| 1.1.0   | Support for DB Certificate repository as the default storage                         | 2022-02-07 |
| 1.1.1   | Fixing daemon bug caused by missing annotation. Added supplementary tools            | 2022-02-14 |
| 1.1.2   | Allowing empty context path for CA                                                   | 2022-02-23 |
| 1.1.3   | Publishing a new CRL directly upon CMC revocation instead of waiting for next update | 2022-02-23 |
| 1.1.4   | Including mitigation of spring core RCE vulnerability                                | 2022-03-31 |
| 1.1.5   | Moving to Spring boot 2.6.6 as a stable mitigation of RCE                            | 2022-03-31 |


## Important release notes (most recent on top)

Release notes are provided only for updates that require configuration changes or other deployment considerations.

### 1.1.0

A new section 2.2.2.8 CA repository configuration is added, describing the configuration settings for setting up
a CA repository using database storage.