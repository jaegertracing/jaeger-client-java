/*
 * Copyright (c) 2018, The Jaeger Authors
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

package io.jaegertracing.tracerresolver.internal;

import io.jaegertracing.Configuration;
import io.jaegertracing.internal.JaegerTracer;
import io.opentracing.contrib.tracerresolver.TracerFactory;

import java.util.logging.*;

public class JaegerTracerFactory implements TracerFactory {
	
	private final static Logger logr = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
  @Override
  public JaegerTracer getTracer() {
	  
	  LogManager.getLogManager().reset();
	  logr.setLevel(Level.ALL);
	  
	  ConsoleHandler ch = new ConsoleHandler();
	  ch.setLevel(Level.SEVERE);
	  logr.addHandler(ch);
	  
	  try {
		  FileHandler fh = new FileHandler("getTracer", true);
		  fh.setLevel(Level.FINE);
		  logr.addHandler(fh);
		  
	  }
	  catch (java.io.IOException e) {
		  
		  logr.log(Level.SEVERE, "File Logger not working", e);
		  
	  }
	  
	  
	  logr.info("Logged Exception");
    return Configuration.fromEnv().getTracer();
  }
}
