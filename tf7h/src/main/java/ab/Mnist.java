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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class Mnist implements Runnable {
  // wget https://storage.googleapis.com/tensorflow/tf-keras-datasets/mnist.npz
  // unzip mnist.npz

  SavedModelBundle model;

  private final byte[] xTest;
  private final byte[] yTest;

  public Mnist() {
    this.model = SavedModelBundle.load("model_data");
    List<String> nodeList = model.graph().toGraphDef().getNodeList()
        .stream().map(NodeDef::getName).collect(Collectors.toList());
    Set<String> readSet = nodeList.stream().filter(u -> u.endsWith("/Read/ReadVariableOp"))
        .map(u -> u.substring(0, u.length() - 20)).collect(Collectors.toCollection(LinkedHashSet::new));
    Set<String> commonSet = nodeList.stream().filter(u -> !u.endsWith("/Read/ReadVariableOp"))
        .filter(u -> !readSet.contains(u)).collect(Collectors.toCollection(LinkedHashSet::new));
    System.out.println("reads: " + String.join(", ", readSet));
    System.out.println("nodes: " + String.join(", ", commonSet));
    try {
      this.xTest = Files.readAllBytes(Paths.get("x_test.npy"));
      this.yTest = Files.readAllBytes(Paths.get("y_test.npy"));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public TFloat32 tensorFromIndex(int index) {
    float[][] f = new float[28][28];
    for (int i = index * 28 * 28 + 80, y = 0; y < 28; y++) {
      for (int x = 0; x < 28; x++, i++) {
        f[y][x] = (xTest[i] & 0xFF) / 255.0F;
      }
    }
    return TFloat32.tensorOf(Shape.of(28, 28), data -> StdArrays.copyTo(f, data));
  }

  public int digitFromTensor(TFloat32 t) {
    int n = (int) t.shape().get(1);
    float max = Float.NEGATIVE_INFINITY;
    int result = 0;
    for (int i = 0; i < n; i++) {
      float v = t.getFloat(0, i);
      if (max < v) {
        max = v;
        result = i;
      }
    }
    return result;
  }

  public void printTensor(TFloat32 t) {
    for (int yh = 0; yh < 7; yh++) {
      if (yh != 0) System.out.println();
      StringBuilder sb = new StringBuilder();
      for (int xh = 0; xh < 14; xh++) {
        double sum = 0;
        for (int yl = 0; yl < 4; yl++) {
          for (int xl = 0; xl < 2; xl++) {
            sum += t.getFloat(yh * 4 + yl, xh * 2 + xl);
          }
        }
        sb.append(sum > 2.0 ? '#' : '-');
      }
      System.out.print(sb);
    }
  }

  @Override
  public void run() {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    for (int i = 0; i < 3; i++) {
      int index = random.nextInt(10_000);
      TFloat32 input = tensorFromIndex(index);
      Tensor output = model.session().runner()
          .feed("serving_default_flatten_input", input)
          .fetch("StatefulPartitionedCall")
          .run().get(0);
      int digit = digitFromTensor((TFloat32) output);
      printTensor(input);
      System.out.println(" " + yTest[80 + index] + " " + digit);
    }
  }

  public static void main(String[] args) {
    // export TF_CPP_MIN_LOG_LEVEL=3
    new Mnist().run();
  }

}
