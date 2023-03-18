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

import org.tensorflow.SavedModelBundle;
import org.tensorflow.Tensor;
import org.tensorflow.ndarray.Shape;
import org.tensorflow.ndarray.StdArrays;
import org.tensorflow.proto.framework.NodeDef;
import org.tensorflow.types.TFloat32;
import org.tensorflow.types.TFloat64;
import org.tensorflow.types.TString;

import java.util.stream.Collectors;

public class Cube implements Runnable {
  // vocab = list("abcdefghijklmnopqrstuvwxyz ")
  public static final int NON_ALPHA = 'z' - 'a' + 1;

  public TFloat32 tensorFromString(String s) {
    int n = s.length();
    float[] f = new float[n];
    for (int i = 0; i < n; i++) {
      int c = s.charAt(i) | 0x20;
      c = c >= 'a' && c <= 'z' ? c - 'a' : NON_ALPHA;
      f[i] = c;
    }
    float[][] input = new float[][]{f};
    return TFloat32.tensorOf(Shape.of(1, n), data -> StdArrays.copyTo(input, data));
  }

  public String stringFromTensor(TFloat32 t) {
    int n = (int) t.shape().get(1);
    int nc = (int) t.shape().get(2);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < n; i++) {
      float max = Float.NEGATIVE_INFINITY;
      int mc = NON_ALPHA;
      for (int c = 0; c < nc; c++) {
        float v = t.getFloat(0, i, c);
        if (max < v) {
          max = v;
          mc = c;
        }
      }
      sb.append(mc >= NON_ALPHA ? ' ' : (char) ('a' + mc));
    }
    return sb.toString();
  }

  SavedModelBundle model;

  public Cube() {
    this.model = SavedModelBundle.load("cube");
    //System.out.println("nodes: " + model.graph().toGraphDef().getNodeList()
    //    .stream().map(NodeDef::getName).collect(Collectors.joining(", ")));
  }

  public char predictNext(String s) {
    TFloat32 input = tensorFromString(s);
    Tensor output = model.session().runner()
        .feed("serving_default_embedding_1_input", input)
        .fetch("StatefulPartitionedCall")
        .run().get(0);
    s = stringFromTensor((TFloat32) output);
    return s.charAt(s.length() - 1);
  }

  @Override
  public void run() {
    String s = "                                                                ";
    for (int i = 0; i < 4; i++) {
      for (int is = 0; is < s.length(); is++) {
        s += predictNext(s);
        s = s.substring(1);
      }
      System.out.println(s);
    }
  }

  public static void main(String[] args) {
    // export TF_CPP_MIN_LOG_LEVEL=3
    new Cube().run();
  }

}
