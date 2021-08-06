/*
 *  Copyright 2020 The TensorFlow Authors. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  =======================================================================
 */
package ab.mnist;

import org.tensorflow.Graph;
import org.tensorflow.Operand;
import org.tensorflow.Session;
import org.tensorflow.framework.optimizers.GradientDescent;
import org.tensorflow.framework.optimizers.Optimizer;
import org.tensorflow.ndarray.ByteNdArray;
import org.tensorflow.ndarray.Shape;
import org.tensorflow.ndarray.index.Indices;
import org.tensorflow.op.Op;
import org.tensorflow.op.Ops;
import org.tensorflow.op.core.Placeholder;
import org.tensorflow.op.core.Variable;
import org.tensorflow.op.math.Mean;
import org.tensorflow.op.nn.Softmax;
import org.tensorflow.types.TFloat32;
import org.tensorflow.types.TInt64;
import org.tensorflow.types.TUint8;

import java.io.Closeable;
import java.util.logging.Logger;

public class SimpleMnist implements NnModel, Closeable {

  private static final Logger logger = Logger.getLogger(SimpleMnist.class.getName());

  Session session;
  Ops tf;
  Softmax<TFloat32> softmax;
  private Graph graph;
  Placeholder<TFloat32> images;
  Placeholder<TFloat32> labels;

  private SimpleMnist(MnistDataset dataset) {
    this.graph = new Graph();
    tf = Ops.create(graph);
    
    // Create placeholders and variables, which should fit batches of an unknown number of images
    images = tf.placeholder(TFloat32.class);
    labels = tf.placeholder(TFloat32.class);

    // Create weights with an initial value of 0
    Shape weightShape = Shape.of(dataset.imageSize(), MnistDataset.NUM_CLASSES);
    Variable<TFloat32> weights = tf.variable(weightShape, TFloat32.class);
    tf.initAdd(tf.assign(weights, tf.zerosLike(weights)));

    // Create biases with an initial value of 0
    Shape biasShape = Shape.of(MnistDataset.NUM_CLASSES);
    Variable<TFloat32> biases = tf.variable(biasShape, TFloat32.class);
    tf.initAdd(tf.assign(biases, tf.zerosLike(biases)));

    // Register all variable initializers for single execution
    tf.init();

    // Predict the class of each image in the batch and compute the loss
    softmax =
        tf.nn.softmax(
            tf.math.add(
                tf.linalg.matMul(images, weights),
                biases
            )
        );

    // Run the graph
    session = new Session(graph);

    // Initialize variables
    session.run(tf.init());
  }

  private static final float LEARNING_RATE = 0.2f;

  private static TFloat32 preprocessImages(ByteNdArray rawImages) {
    Ops tf = Ops.create();

    // Flatten images in a single dimension and normalize their pixels as floats.
    long imageSize = rawImages.get(0).shape().size();
    return tf.math.div(
        tf.reshape(
            tf.dtypes.cast(tf.constant(rawImages), TFloat32.class),
            tf.array(-1L, imageSize)
        ),
        tf.constant(255.0f)
    ).asTensor();
  }

  private static TFloat32 preprocessLabels(ByteNdArray rawLabels) {
    Ops tf = Ops.create();

    // Map labels to one hot vectors where only the expected predictions as a value of 1.0
    return tf.oneHot(
        tf.constant(rawLabels),
        tf.constant(MnistDataset.NUM_CLASSES),
        tf.constant(1.0f),
        tf.constant(0.0f)
    ).asTensor();
  }


  @Override
  public void train(MnistDataset dataset, int batchSize, int maxSize) {
    // Create placeholders and variables, which should fit batches of an unknown number of images
    //Placeholder<TFloat32> images = tf.placeholder(TFloat32.class);
    //Placeholder<TFloat32> labels = tf.placeholder(TFloat32.class);
    // Predict the class of each image in the batch and compute the loss
    Mean<TFloat32> crossEntropy =
        tf.math.mean(
            tf.math.neg(
                tf.reduceSum(
                    tf.math.mul(labels, tf.math.log(softmax)),
                    tf.array(1)
                )
            ),
            tf.array(0)
        );
    // Back-propagate gradients to variables for training
    Optimizer optimizer = new GradientDescent(graph, LEARNING_RATE);
    Op minimize = optimizer.minimize(crossEntropy);
    // Train the model
    int trainLimit = Common.TRAIN_BATCH_SIZE;
    for (ImageBatch trainingBatch : dataset.trainingBatches(Common.TRAIN_BATCH_SIZE)) {
      if (trainLimit-- <= 0) break;
      try (TFloat32 batchImages = preprocessImages(trainingBatch.images());
           TFloat32 batchLabels = preprocessLabels(trainingBatch.labels())) {
        session.runner()
            .addTarget(minimize)
            .feed(images.asOutput(), batchImages)
            .feed(labels.asOutput(), batchLabels)
            .run();
      }
    }

  }

  @Override
  public void test(MnistDataset dataset, int batchSize) {
    // Create placeholders and variables, which should fit batches of an unknown number of images
    //Placeholder<TFloat32> images = tf.placeholder(TFloat32.class);
    //Placeholder<TFloat32> labels = tf.placeholder(TFloat32.class);
    // Compute the accuracy of the model
    Operand<TInt64> predicted = tf.math.argMax(softmax, tf.constant(1));
    Operand<TInt64> expected = tf.math.argMax(labels, tf.constant(1));
    Operand<TFloat32> accuracy = tf.math.mean(tf.dtypes.cast(tf.math.equal(predicted, expected), TFloat32.class), tf.array(0));
    // Test the model
    ImageBatch testBatch = dataset.testBatch();
    try (TFloat32 testImages = preprocessImages(testBatch.images());
         TFloat32 testLabels = preprocessLabels(testBatch.labels());
         TFloat32 accuracyValue = (TFloat32)session.runner()
             .fetch(accuracy)
             .feed(images.asOutput(), testImages)
             .feed(labels.asOutput(), testLabels)
             .run()
             .get(0)) {
      System.out.println("Accuracy: " + accuracyValue.getFloat());
    }
  }

  @Override
  public void close() {
    session.close();
    graph.close();
  }

  public void vggtest(MnistDataset dataset, int minibatchSize) {
    int correctCount = 0;
    int[][] confusionMatrix = new int[10][10];

    for (ImageBatch trainingBatch : dataset.testBatches(minibatchSize)) {
      try (TUint8 transformedInput = TUint8.tensorOf(trainingBatch.images());
           TFloat32 outputTensor = (TFloat32)session.runner()
               .feed("input", transformedInput)
               .fetch("output").run().get(0)) {

        ByteNdArray labelBatch = trainingBatch.labels();
        for (int k = 0; k < labelBatch.shape().size(0); k++) {
          byte trueLabel = labelBatch.getByte(k);
          int predLabel;

          predLabel = 1;//argmax(outputTensor.slice(Indices.at(k), Indices.all()));
          if (predLabel == trueLabel) {
            correctCount++;
          }

          confusionMatrix[trueLabel][predLabel]++;
        }
      }
    }

    logger.info("Final accuracy = " + ((float) correctCount) / dataset.numTestingExamples());

    StringBuilder sb = new StringBuilder();
    sb.append("Label");
    for (int i = 0; i < confusionMatrix.length; i++) {
      sb.append(String.format("%1$5s", "" + i));
    }
    sb.append("\n");

    for (int i = 0; i < confusionMatrix.length; i++) {
      sb.append(String.format("%1$5s", "" + i));
      for (int j = 0; j < confusionMatrix[i].length; j++) {
        sb.append(String.format("%1$5s", "" + confusionMatrix[i][j]));
      }
      sb.append("\n");
    }

    System.out.println(sb.toString());
  }

  public static void main(String[] args) {
    MnistDataset dataset = Common.createMnistDataset();

    try (SimpleMnist mnist = new SimpleMnist(dataset)) {
      mnist.train(dataset, Common.TRAIN_BATCH_SIZE, Common.TRAIN_BATCH_SIZE);
      mnist.test(dataset, Common.TEST_BATCH_SIZE);
      //mnist.vggtest(dataset, Common.TEST_BATCH_SIZE);
    }
  }

}
