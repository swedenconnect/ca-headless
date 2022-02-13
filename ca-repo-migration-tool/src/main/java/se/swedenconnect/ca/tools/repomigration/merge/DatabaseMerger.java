package se.swedenconnect.ca.tools.repomigration.merge;

import org.apache.commons.cli.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import se.swedenconnect.ca.engine.ca.repository.CertificateRecord;
import se.swedenconnect.ca.service.base.configuration.keys.BasicX509Utils;
import se.swedenconnect.ca.tools.repomigration.options.AppOptions;
import se.swedenconnect.ca.tools.repomigration.repo.MergeCARepository;
import se.swedenconnect.ca.tools.repomigration.repo.RepositoryGroup;

import java.io.IOException;
import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Description
 *
 * @author Martin Lindström (martin@idsec.se)
 * @author Stefan Santesson (stefan@idsec.se)
 */
@Component
public class DatabaseMerger {

  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

  private final Map<String, RepositoryGroup> caRepositoryMap;

  @Autowired
  public DatabaseMerger(Map<String, RepositoryGroup> caRepositoryMap) {
    this.caRepositoryMap = caRepositoryMap;
  }

  public void run(String... args) throws Exception {
    CommandLineParser parser = new DefaultParser();
    Options opt = AppOptions.getOptions();
    try {
      CommandLine cmd = parser.parse(opt, args);
      validateInput(cmd);
    } catch (ParseException ex) {
      showHelp("Unable to parse arguments!");
    }

  }

  private void validateInput(CommandLine cmd) {
    if (cmd.hasOption(AppOptions.OPTION_HELP)) {
      showHelp();
      return;
    }
    if (cmd.hasOption(AppOptions.OPTION_LIST)){
      showMergeStatus(cmd);
      return;
    }
    if (!cmd.hasOption(AppOptions.OPTION_FILE_MERGE) && !cmd.hasOption(AppOptions.OPTION_DB_MERGE)) {
      showHelp("At least one of the options '-dbmerge' or '-filemerge' must be set");
      return;
    }
    try {
      mergeRepositories(cmd);
    }
    catch (IOException e) {
      System.out.println("Error merging CA repositories");
      e.printStackTrace();
    }

  }

  private void mergeRepositories(CommandLine cmd) throws IOException {
    System.out.println("Merging CA repositories");
    Map<String, MergeStatus> mergeStatusMap = getMergeStatus();
    final Set<String> instanceSet = mergeStatusMap.keySet();
    for (String instance : instanceSet){
      System.out.println("Merging repository data for instance: " + instance);
      System.out.println("---------------------------------------------------------");
      final MergeStatus mergeStatus = mergeStatusMap.get(instance);
      final RepositoryGroup repositoryGroup = caRepositoryMap.get(instance);
      if (cmd.hasOption(AppOptions.OPTION_DB_MERGE)){
        System.out.println("Merging file repository certs to DB:");
        if (mergeStatus.getJsonCertsMissingInDb().isEmpty()){
          System.out.println("-- Nothing to merge --");
        } else {
          mergeCerts(mergeStatus.getJsonCertsMissingInDb(), repositoryGroup.getFileRepository(), repositoryGroup.getDbRepository(), cmd);
          System.out.println("Merged " + mergeStatus.getJsonCertsMissingInDb().size() +  " certificates to DB repository");
        }
      }
      if (cmd.hasOption(AppOptions.OPTION_FILE_MERGE)){
        System.out.println("Merging DB repository certs to File storage:");
        if (mergeStatus.getDbCertsMissingInJson().isEmpty()){
          System.out.println("-- Nothing to merge --");
        } else {
          mergeCerts(mergeStatus.getDbCertsMissingInJson(), repositoryGroup.getDbRepository(), repositoryGroup.getFileRepository(), cmd);
          System.out.println("Merged " + mergeStatus.getDbCertsMissingInJson().size() +  " certificates to File repository");
        }
      }
      System.out.println("");
    }
  }

  private void mergeCerts(List<BigInteger> certSerialList, MergeCARepository fromRepo, MergeCARepository toRepo, CommandLine cmd) throws IOException {
    for (BigInteger certSerial : certSerialList){
      final CertificateRecord certificateRecord = fromRepo.getCertificate(certSerial);
      toRepo.addCertificateRecord(certificateRecord);
      if (cmd.hasOption(AppOptions.OPTION_VERBOSE)){
        printCertRecord(certificateRecord);
      }
    }
  }

  private void showMergeStatus(CommandLine cmd) {
    System.out.println("Merge status information : Verbose = " + cmd.hasOption(AppOptions.OPTION_VERBOSE));
    System.out.println("");
    Map<String, MergeStatus> mergeStatusMap = getMergeStatus();
    final Set<String> instanceSet = mergeStatusMap.keySet();
    for (String instance : instanceSet){
      System.out.println("Merge status for instance: " + instance);
      System.out.println("---------------------------------------------------------");
      final MergeStatus mergeStatus = mergeStatusMap.get(instance);
      final RepositoryGroup repositoryGroup = caRepositoryMap.get(instance);
      System.out.println("File repo certs missing in DB repo (" + mergeStatus.getJsonCertsMissingInDb().size() + ")");
      if (cmd.hasOption(AppOptions.OPTION_VERBOSE)) printFullCertList(mergeStatus.getJsonCertsMissingInDb(), repositoryGroup.getFileRepository());
      System.out.println("DB repo certs missing in File repo (" + mergeStatus.getDbCertsMissingInJson().size() + ")");
      if (cmd.hasOption(AppOptions.OPTION_VERBOSE)) printFullCertList(mergeStatus.getDbCertsMissingInJson(), repositoryGroup.getDbRepository());
      System.out.println("Duplicate cert records (" + mergeStatus.getDuplicateRecords().size() + "): ");
      if (cmd.hasOption(AppOptions.OPTION_VERBOSE)) System.out.println("Duplicate cert serials: " + String.join(",", strList(mergeStatus.getDuplicateRecords())));
      System.out.println("");
    }

  }

  private void printFullCertList(List<BigInteger> serialList, MergeCARepository caRepository) {
    for (BigInteger certSerial : serialList){
      final CertificateRecord certificateRecord = caRepository.getCertificate(certSerial);
      printCertRecord(certificateRecord);
    }

  }

  private void printCertRecord(CertificateRecord certificateRecord) {
    try {
      StringBuilder b = new StringBuilder();
      final X509Certificate certificate = BasicX509Utils.getCertificate(certificateRecord.getCertificate());
      b.append(certificate.getSubjectX500Principal())
        .append(" NotBefore:").append(DATE_FORMAT.format(certificate.getNotBefore()))
        .append(" Expires:").append(DATE_FORMAT.format(certificate.getNotAfter()));
      if (certificateRecord.isRevoked()){
        b.append(" Revoked:").append(DATE_FORMAT.format(certificateRecord.getRevocationTime()));
      }
      System.out.println(b);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  private List<String> strList(List<BigInteger> bigIntegerList) {
    if (bigIntegerList == null) {
      return new ArrayList<>();
    }
    return bigIntegerList.stream()
      .map(bigInteger -> bigInteger.toString(16))
      .collect(Collectors.toList());
  }

  private Map<String, MergeStatus> getMergeStatus() {
    Map<String, MergeStatus> mergeStatusMap = new HashMap<>();

    final Set<String> instanceSet = caRepositoryMap.keySet();
    for (String instance:instanceSet){
      List<BigInteger> dbCertsMissingInJson = new ArrayList<>();
      List<BigInteger> jsonCertsMissingInDb = new ArrayList<>();
      List<BigInteger> duplicateRecords = new ArrayList<>();

      final RepositoryGroup repositoryGroup = caRepositoryMap.get(instance);
      final List<BigInteger> allJsonCertificates = repositoryGroup.getFileRepository().getAllCertificates();
      final List<BigInteger> allDbCertificates = repositoryGroup.getDbRepository().getAllCertificates();

      for (BigInteger jsonCertSerial : allJsonCertificates) {
        if (allDbCertificates.contains(jsonCertSerial)){
          duplicateRecords.add(jsonCertSerial);
        } else {
          jsonCertsMissingInDb.add(jsonCertSerial);
        }
      }
      for (BigInteger dbCertSerial : allDbCertificates) {
        if (!allJsonCertificates.contains(dbCertSerial)){
          dbCertsMissingInJson.add(dbCertSerial);
        }
      }
      mergeStatusMap.put(instance, MergeStatus.builder()
          .dbCertsMissingInJson(dbCertsMissingInJson)
          .jsonCertsMissingInDb(jsonCertsMissingInDb)
          .duplicateRecords(duplicateRecords)
        .build());
    }
    return mergeStatusMap;
  }

  private void showHelp() {
    showHelp(null);
  }

  private void showHelp(String error) {
    if (error != null) {
      System.out.println("Error: " + error);
    }
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp("java -jar repomigrate.jar [options]", AppOptions.getOptions());
  }



}
