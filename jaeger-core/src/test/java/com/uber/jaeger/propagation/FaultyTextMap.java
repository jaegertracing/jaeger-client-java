package com.uber.jaeger.propagation;

import io.opentracing.propagation.TextMap;

import java.util.Iterator;
import java.util.Map;

public class FaultyTextMap implements TextMap {

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    throw new RuntimeException("some TextMaps can be faulty, this one is.");
  }

  @Override
  public void put(String key, String value) {
    throw new RuntimeException("some TextMaps can be faulty, this one is.");
  }
}
