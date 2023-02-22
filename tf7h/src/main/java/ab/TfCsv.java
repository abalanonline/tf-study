/*
 * Copyright 2023 Aleksei Balan
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

package ab;

import org.tensorflow.Graph;
import org.tensorflow.Operand;
import org.tensorflow.Result;
import org.tensorflow.Session;
import org.tensorflow.ndarray.NdArray;
import org.tensorflow.op.Ops;
import org.tensorflow.op.core.Placeholder;
import org.tensorflow.op.io.DecodeCsv;
import org.tensorflow.types.TString;
import org.tensorflow.types.family.TType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TfCsv {

  private int columns;
  private Graph graph;

  /**
   * @param defaults allowed values: float, double, int32, int64, string
   */
  public TfCsv(List<TType> defaults) {
    graph = new Graph();
    Ops tf = Ops.create(graph);

    Placeholder<TString> input = tf.withName("input").placeholder(TString.class);
    List<Operand<?>> recordDefaults = defaults.stream().map(tf::constantOf).collect(Collectors.toList());
    DecodeCsv decodeCsv = DecodeCsv.create(tf.scope(), input, recordDefaults);
    for (Operand<TType> column : decodeCsv) tf.withName("output_" + columns++).identity(column);
  }

  public List<NdArray<?>> run(InputStream csv) {
    List<String> input = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(csv))) { // boilerplating since 1997
      for (String line = reader.readLine(); line != null; line = reader.readLine()) input.add(line);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    String columnNames = input.remove(0);
    try (Session session = new Session(graph)) {
      Session.Runner runner = session.runner();
      IntStream.range(0, columns).forEach(column -> runner.fetch("output_" + column));
      Result result = runner.feed("input", TString.vectorOf(input.toArray(String[]::new))).run();
      return IntStream.range(0, columns).mapToObj(column -> (NdArray<?>) result.get(column)).collect(Collectors.toList());
    }
  }
}
