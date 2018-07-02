/*
 * Copyright (c) 2016, Uber Technologies, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.jaegertracing.internal.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

public class Http {

  private static final int TIMEOUT_MS = 5000;

  public static String makeGetRequest(String urlToRead) throws IOException {
    URL url = new URL(urlToRead);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setConnectTimeout(TIMEOUT_MS);
    StringBuilder result = new StringBuilder();
    try {
      conn.setRequestMethod("GET");
      BufferedReader rd =
          new BufferedReader(
              new InputStreamReader(conn.getInputStream(), Charset.forName("UTF-8")));
      try {
        String line;
        while ((line = rd.readLine()) != null) {
          result.append(line);
        }
      } finally {
        rd.close();
      }
    } finally {
      conn.disconnect();
    }
    return result.toString();
  }
}
