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

import org.junit.jupiter.api.Test;
import org.tensorflow.ndarray.NdArray;
import org.tensorflow.types.TFloat64;
import org.tensorflow.types.TInt32;
import org.tensorflow.types.TString;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

class TfCsvTest {

  @Test
  void test() throws IOException {
    InputStream csv;
    //csv = new URL("https://storage.googleapis.com/tf-datasets/titanic/eval.csv").openStream();
    csv = getClass().getResourceAsStream("/titanic/eval.csv");
    TfCsv tfCsv = new TfCsv(Arrays.asList(
        TInt32.scalarOf(0), // survived
        TString.scalarOf(""), // sex
        TFloat64.scalarOf(0), // age
        TInt32.scalarOf(0), // n_siblings_spouses
        TInt32.scalarOf(0), // parch
        TFloat64.scalarOf(0), // fare
        TString.scalarOf(""), // class
        TString.scalarOf(""), // deck
        TString.scalarOf(""), // embark_town
        TString.scalarOf("") // alone
    ));
    List<NdArray<?>> result = tfCsv.run(csv);
    System.out.println(result.get(5).streamOfObjects().map(Object::toString).collect(Collectors.joining(", ")));
  }
}
