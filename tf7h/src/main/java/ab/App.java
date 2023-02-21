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
import org.tensorflow.TensorFlow;
import org.tensorflow.ndarray.NdArray;
import org.tensorflow.ndarray.Shape;
import org.tensorflow.ndarray.StdArrays;
import org.tensorflow.op.OpScope;
import org.tensorflow.op.Ops;
import org.tensorflow.op.Scope;
import org.tensorflow.op.core.Constant;
import org.tensorflow.types.TFloat64;
import org.tensorflow.types.TInt32;
import org.tensorflow.types.TString;

// freecodecamp.org TensorFlow in 7 hours https://abstrusegoose.com/249
public class App {

  public static void m2() {
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

  public static void main(String[] args) {
    m2();
  }
}
