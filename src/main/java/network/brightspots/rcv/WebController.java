/*
 * RCTab
 * Copyright (c) 2017-2023 Bright Spots Developers.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package network.brightspots.rcv;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1.0")
public class WebController {

  public WebController() {
    Logger.setup();
  }

  @RequestMapping(value = "appVersion", method = RequestMethod.GET)
  public String getAppVersion() {
    return Main.APP_NAME + " " + Main.APP_VERSION;
  }

  @RequestMapping(value = "convertToCdf", method = RequestMethod.GET)
  public void convertToCdf(@RequestParam("path") String configPath) {
    final TabulatorSession session = new TabulatorSession(configPath);
    session.convertToCdf();
  }

  @RequestMapping(value = "tabulate", method = RequestMethod.GET)
  public List<String> tabulate(@RequestParam("name") String operatorName,
                               @RequestParam("path") String configPath) {
    final TabulatorSession session = new TabulatorSession(configPath);
    return session.tabulate(operatorName.trim());
  }

}
