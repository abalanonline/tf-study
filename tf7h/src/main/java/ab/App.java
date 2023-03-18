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
import org.tensorflow.Session;
import org.tensorflow.TensorFlow;
import org.tensorflow.ndarray.NdArray;
import org.tensorflow.ndarray.Shape;
import org.tensorflow.ndarray.StdArrays;
import org.tensorflow.op.OpScope;
import org.tensorflow.op.Operands;
import org.tensorflow.op.Ops;
import org.tensorflow.op.Scope;
import org.tensorflow.op.core.Constant;
import org.tensorflow.op.core.Placeholder;
import org.tensorflow.op.data.CSVDataset;
import org.tensorflow.proto.util.testlog.AvailableDeviceInfo;
import org.tensorflow.types.TBool;
import org.tensorflow.types.TFloat64;
import org.tensorflow.types.TInt32;
import org.tensorflow.types.TInt64;
import org.tensorflow.types.TString;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

// freecodecamp.org TensorFlow in 7 hours https://abstrusegoose.com/249
public class App {

  public static void m2() {
    System.out.println(AvailableDeviceInfo.getDefaultInstance().getPhysicalDescription());
    System.out.println("TensorFlow version " + TensorFlow.version());
    Graph graph = new Graph();
    Scope scope = new OpScope(graph);
    Constant<TString> constant = Constant.scalarOf(scope, "this is a string");
    TString string = TString.scalarOf("this is a string");
    TInt32 number = TInt32.scalarOf(324);
    TFloat64 floating = TFloat64.scalarOf(3.567);

    NdArray<String> tensorRank1 = StdArrays.ndCopyOf(new String[]{"Test"});
    NdArray<String> tensorRank2 = StdArrays.ndCopyOf(new String[][]{{"test", "ok"}, {"test", "yes"}});
    System.out.println("tensorRank1 " + tensorRank1.toString() + " rank " + tensorRank1.rank());
    System.out.println("tensorRank2 " + tensorRank2.toString() + " rank " + tensorRank2.rank());

    Ops tf = Ops.create();
    TString tensor1 = tf.reshape(tf.constant(tensorRank2), tf.constant(Shape.of(-1))).asTensor();
    System.out.println(tensor1);
  }

  public static void m3() {
    Graph graph = new Graph();
    Ops tf = Ops.create(graph);
    Scope scope = new OpScope(graph);

    Placeholder<TString> filename = new Placeholder<>(scope.opBuilder(Placeholder.OP_NAME, "filename").setAttr("dtype", Operands.toDataType(TString.class)).build());
    CSVDataset csvDataset = CSVDataset.create(scope,
        filename,
        tf.placeholder(TString.class), // compressionType
        tf.placeholder(TInt64.class),
        tf.placeholder(TBool.class), tf.placeholder(TString.class), tf.placeholder(TBool.class),
        tf.placeholder(TString.class), tf.placeholder(TInt64.class), Collections.singletonList(tf.placeholder(TString.class)),
        tf.placeholder(TInt64.class), Collections.singletonList(Shape.of(-1,-1)));

    // run
    new Session(graph).runner()
        .feed("filename", TString.scalarOf("dataset.csv"))
        .feed("Placeholder", TString.scalarOf(""))
        .feed("Placeholder_1", TInt64.scalarOf(4194304))
        .feed("Placeholder_2", TBool.scalarOf(false))
        .feed("Placeholder_3", TString.scalarOf(","))
        .feed("Placeholder_4", TBool.scalarOf(true))
        .feed("Placeholder_5", TString.scalarOf(""))
        .feed("Placeholder_6", TInt64.vectorOf()) // select_cols
        .feed("Placeholder_7", TString.scalarOf(""))
        .feed("Placeholder_8", TInt64.vectorOf())
        .addTarget(csvDataset).run();
    System.out.println(csvDataset.handle() + " " + csvDataset.handle().shape());
    // https://storage.googleapis.com/tf-datasets/titanic/train.csv
    // https://storage.googleapis.com/tf-datasets/titanic/eval.csv
  }

  public void m4() {
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

    Graph graph = new Graph();
    Ops tf = Ops.create(graph);
  }

  public static void main(String[] args) {
    new App().m4();
  }

}
