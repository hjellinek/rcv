/*
 * RCTab
 * Copyright (c) 2017-2023 Bright Spots Developers.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package network.brightspots.rcv;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ranked-choice voting Web API.  Call the endpoints in this sequence:
 * <ol>
 *   <li><tt>newContest</tt>: start a new contest. Requires a config parameter, returns a unique contest ID.
 *   <li><tt>castVotes</tt>: accept a chunk of Cast Vote Records - can run this 1 or more times. Requires a contest ID
 *   and sequence number.  Returns the sequence number expected for the next call.
 *   <li><tt>tabulate</tt>: tabulate a contest. Requires operator name and contest ID.  Returns the contest results.
 *   <li><tt>clear</tt>: erase all records of a contest from memory and disk.
 * </ol>
 * You can call <tt>appVersion</tt> anytime.
 */
@RestController
@RequestMapping("/api/v1.0")
@SuppressWarnings("SpringJavaAutowiredFieldsWarningInspection")
public class WebController {

  private static final String CONFIG_FILE_EXT = ".json";

  private static final int BUFFER_SIZE = 65536;

  @Autowired
  private Environment env;

  /**
   * Where all the contest data files will be written and read from.
   */
  private File rootContestDir;

  /**
   * Use this to serialize/deserialize JSON or other external formats.
   */
  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Keep track of active contests by mapping from the contest ID to {@link ContestState}.
   */
  private final Map<UUID, ContestState> contestMap = new ConcurrentHashMap<>();

  public WebController() {
    Logger.setup();
  }

  @PostConstruct
  private void init() {
    rootContestDir = new File(env.getRequiredProperty("contest.dir"));
  }

  /**
   * Return the application name and version as a String.
   *
   * @return the app name and version
   */
  @RequestMapping(value = "appVersion", method = RequestMethod.GET)
  public String getAppVersion() {
    return Main.APP_NAME + " " + Main.APP_VERSION;
  }

  /**
   * Begin a new contest by uploading a configuration.  We copy the config file to a
   * temp directory.  We create a {@link ContestState}, store the directory name in it,
   * and add it to {@link #contestMap} with a new random UUID.  We return the UUID.
   *
   * @param contestConfig the configuration
   * @return the UUID and expected next upload chunk number
   */
  @RequestMapping(value = "newContest", method = RequestMethod.POST)
  public UploadResult newContest(@RequestBody RawContestConfig contestConfig) throws IOException {
    final UUID contestId = UUID.randomUUID();
    final File contestDir = new File(rootContestDir, contestId.toString());
    if (!contestDir.mkdirs()) {
      throw new IOException("Could not create '"+contestDir+"'");
    }
    updateWithContestId(contestConfig, contestId);
    final ContestState contestState = new ContestState(contestDir, contestId);
    contestMap.put(contestId, contestState);
    objectMapper.writeValue(configFile(contestDir, contestId), contestConfig);
    return new UploadResult(contestId, contestState.getNextUpload());
  }

  /**
   * The config we're passed will refer to the CVR data file with a filesystem-relative name.
   * We rename files to use the contest ID.  Update the config with that name.
   *
   * @param contestConfig the config to update
   * @param contestId     the contest ID
   */
  private void updateWithContestId(RawContestConfig contestConfig, UUID contestId) {
    contestConfig.cvrFileSources.get(0).filePathProperty().set(contestId.toString());
  }

  /**
   * Upload a chunk of Cast Vote Records and append them to the previously-uploaded ones.
   *
   * @param castVotesChunk a chunk of a CVR file
   * @param contestId      the ID of the contest
   * @param chunk          the chunk number
   * @return the UUID and next chunk number
   */
  @RequestMapping(value = "castVotes", method = RequestMethod.POST)
  public UploadResult castVotes(InputStream castVotesChunk,
                                UUID contestId, int chunk) throws IOException {
    final ContestState contest = checkContestExists(contestId);
    checkExpectedChunk(chunk, contest);
    appendCastVotes(contest, castVotesChunk);
    contest.incrementNextUpload();
    return new UploadResult(contestId, contest.getNextUpload());
  }

  /**
   * Add this chunk of cast vote data to the existing data.
   *
   * @param contest   the {@link ContestState}
   * @param castVotes an opaque block of data, an entire file or portion
   * @throws IOException if there's a problem
   */
  private void appendCastVotes(ContestState contest, InputStream castVotes) throws IOException {
    final File dataFileName = dataFile(rootContestDir, contest.getContestId());
    try (final FileOutputStream fos = new FileOutputStream(dataFileName, true)) {
      final byte[] copyBuffer = new byte[BUFFER_SIZE];
      while (true) {
        final int bytesRead = castVotes.read(copyBuffer);
        if (bytesRead == -1) {
          break;
        }
        fos.write(copyBuffer, 0, bytesRead);
      }
    }
  }

  /**
   * Tabulate the results of the contest described by the config at the given path and
   * return (transmit) the summary as a JSON object.  (We merely copy the <tt>_summary.json</tt> file
   * to the client, trusting that it is actually well-formed JSON.)
   *
   * @param operatorName the operator's name
   * @param contestId    the contest ID
   */
  @RequestMapping(value = "tabulate", method = RequestMethod.GET,
    produces = MediaType.APPLICATION_JSON_VALUE)
  public void tabulate(@RequestParam("name") String operatorName,
                       UUID contestId,
                       HttpServletResponse response) throws IOException {
    final ContestState contest = checkContestExists(contestId);
    final File configPath = configFile(new File(rootContestDir, contestId.toString()), contest.getContestId());
    final TabulatorSession session = new TabulatorSession(configPath.getPath());
    final List<String> ignore = session.tabulate(operatorName.trim());
    final String outputPath = session.getOutputPath();
    // if only we could call ResultsWriter.getOutputFilePathFromInstance instead of this:
    final Path summaryFile = Path.of(outputPath, session.getTimestampString() + "_summary.json");
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    Files.copy(summaryFile, response.getOutputStream());
  }

  /**
   * Delete the contest directory.
   *
   * @param contestId ID of the contest
   * @throws IOException if the contest doesn't exist or we cannot delete the directory
   */
  @RequestMapping(value = "clear", method = RequestMethod.GET)
  public void clearState(UUID contestId, HttpServletResponse response) throws IOException {
    final ContestState contestState = checkContestExists(contestId);
    contestMap.remove(contestId);
    deleteContestDirectory(contestState.getDirectory());
    response.setStatus(HttpStatus.ACCEPTED.value());
  }

  /**
   * Delete the given contest directory and its contents.
   *
   * @param contestDir the contest directory
   * @throws IOException if there's a problem
   */
  private void deleteContestDirectory(File contestDir) throws IOException {
    Files.walkFileTree(contestDir.toPath(), new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        Files.delete(dir);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  /**
   * Return the canonical name for a contest config file in the given dir.
   *
   * @param dir       the directory, which will be included in the result
   * @param contestId the contest ID
   * @return the full, canonical name for the config file for the contest
   */
  private File configFile(File dir, UUID contestId) {
    return new File(dir, contestId + CONFIG_FILE_EXT);
  }

  /**
   * Return the canonical name for a contest data file in the given dir.
   *
   * @param contestDir the contest's directory, which will be included in the result
   * @param contestId  the contest ID
   * @return the full, canonical name for the data file for the contest
   */
  private File dataFile(File contestDir, UUID contestId) {
    return new File(new File(contestDir, contestId.toString()), contestId.toString());
  }

  //
  /// Error checking
  //

  /**
   * If the contest with this ID exsists, return the corresponding {@link ContestState} object.  Otherwise throw
   * {@link IOException}.
   *
   * @param contestId the contest ID
   * @return the corresponding {@link ContestState} object
   * @throws IOException if it's not found
   */
  private ContestState checkContestExists(UUID contestId) throws IOException {
    final ContestState contestState = contestMap.get(contestId);
    if (contestState == null) {
      throw new IOException("No such contest: " + contestId);
    }
    return contestState;
  }

  /**
   * Check that we're expecting the chunk number we were given.
   *
   * @param chunk        the chunk number
   * @param contestState the {@link ContestState}
   * @throws IOException if it's not the right chunk
   */
  private void checkExpectedChunk(int chunk, ContestState contestState) throws IOException {
    if (chunk != contestState.getNextUpload()) {
      throw new IOException("Sent chunk " + chunk + ", expecting " + contestState.getNextUpload());
    }
  }

  /**
   * The state of an existing contest.
   */
  private static class ContestState {

    /**
     * The contest's unique ID, a random {@link UUID}.
     */
    private final UUID contestId;

    /**
     * If we receive another chunk of data for this contest, we expect it to have this sequence number.
     */
    private final AtomicInteger nextUpload;

    /**
     * The directory holding this contest's data.
     */
    private final File directory;

    /**
     * Given a directory and contest ID, create a new {@link ContestState}.
     *
     * @param directory the directory that will hold the contest data
     * @param contestId the contest ID
     */
    public ContestState(File directory, UUID contestId) {
      nextUpload = new AtomicInteger(0);
      this.directory = directory;
      this.contestId = contestId;
    }

    /**
     * Return the contest's directory.
     *
     * @return the contest's directory
     */
    public File getDirectory() {
      return directory;
    }

    /**
     * What sequence number should we expect for the next chunk?
     *
     * @return the sequence number we expect for the next chunk
     */
    public int getNextUpload() {
      return nextUpload.get();
    }

    /**
     * Bump the expected sequence number by 1.
     */
    public void incrementNextUpload() {
      nextUpload.addAndGet(1);
    }

    /**
     * Return the contest ID.
     *
     * @return the contest ID, a {@link UUID}.
     */
    public UUID getContestId() {
      return contestId;
    }
  }

  /**
   * The result of an upload, which is returned to the caller.
   *
   * @param contestId  the contest ID
   * @param nextUpload the index of the next expected upload
   */
  public record UploadResult(UUID contestId, int nextUpload) {
  }

}
