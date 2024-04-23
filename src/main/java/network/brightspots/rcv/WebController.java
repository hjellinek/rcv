/*
 * RCTab
 * Copyright (c) 2017-2023 Bright Spots Developers.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package network.brightspots.rcv;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/v1.0")
public class WebController {

  public WebController() {
    Logger.setup();
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
   * Convert to CDF using the config file at the given path.
   *
   * @param configPath the path to the config file
   */
  @RequestMapping(value = "convertToCdf", method = RequestMethod.GET)
  public void convertToCdf(@RequestParam("path") String configPath) {
    final TabulatorSession session = new TabulatorSession(configPath);
    session.convertToCdf();
  }

  /**
   * Tabulate the results of the contest described by the config at the given path and
   * return (transmit) the summary as a JSON object.  (We merely copy the <tt>_summary.json</tt> file
   * to the client, trusting that it is actually well-formed JSON.)
   *
   * @param operatorName the operator's name
   * @param configPath   the path to the contest's config file
   */
  @RequestMapping(value = "tabulate", method = RequestMethod.GET,
    produces = MediaType.APPLICATION_JSON_VALUE)
  public void tabulate(@RequestParam("name") String operatorName,
                       @RequestParam("path") String configPath,
                       HttpServletResponse response) throws IOException {
    final TabulatorSession session = new TabulatorSession(configPath);
    final List<String> ignore = session.tabulate(operatorName.trim());
    final String outputPath = session.getOutputPath();
    // if only we could call ResultsWriter.getOutputFilePathFromInstance instead of this:
    final Path summaryFile = Path.of(outputPath, session.getTimestampString() + "_summary.json");
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    Files.copy(summaryFile, response.getOutputStream());
  }

}
