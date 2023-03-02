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
import org.tensorflow.proto.framework.NodeDef;
import org.tensorflow.types.TString;

import java.util.stream.Collectors;

public class ModelLoad implements Runnable {

  @Override
  public void run() {
    SavedModelBundle savedModel = SavedModelBundle.load("model_save_test");
    System.out.println("nodes: " + savedModel.graph().toGraphDef().getNodeList()
        .stream().map(NodeDef::getName).collect(Collectors.joining(", ")));
    savedModel.session().runner()
        .feed("input", TString.vectorOf(new String[]{"string"}))
        .run().get(0);
  }

  public static void main(String[] args) {
    // export TF_CPP_MIN_LOG_LEVEL=3
    new ModelLoad().run();
  }

}














