# https://www.tensorflow.org/tutorials/quickstart/beginner
import tensorflow as tf
from tensorflow.python.client import device_lib
print("TensorFlow version:", tf.__version__,
  device_lib.list_local_devices()[-1].physical_device_desc)
mnist = tf.keras.datasets.mnist
(x_train, y_train), (x_test, y_test) = mnist.load_data()
x_train, x_test = x_train / 255.0, x_test / 255.0
model = tf.keras.models.Sequential([
  tf.keras.layers.Flatten(input_shape=(28, 28)),
  tf.keras.layers.Dense(128, activation='relu'),
  tf.keras.layers.Dropout(0.2),
  tf.keras.layers.Dense(10)
])
loss_fn = tf.keras.losses.SparseCategoricalCrossentropy(from_logits=True)
model.compile(optimizer='adam',
              loss=loss_fn,
              metrics=['accuracy'])
model.fit(x_train, y_train, epochs=5)
model.evaluate(x_test,  y_test, verbose=2)

model.save("model_data")
!zip -r model_save_test.zip model_data/
!rm -rf model_data/
!cp model_save_test.zip model_load_test.zip
!unzip -o model_load_test.zip
model_copy = tf.keras.models.load_model("model_data")
model_copy.evaluate(x_test,  y_test)
