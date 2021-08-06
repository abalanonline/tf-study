package ab.mnist;

public class Common {
  public static final int EPOCHS = 1;
  public static final int TRAIN_BATCH_SIZE = 500;
  public static final int TRAIN_LIMIT = 50;
  public static final int TEST_BATCH_SIZE = 500;
  static final String TRAINING_IMAGES_ARCHIVE = "mnist/train-images-idx3-ubyte.gz";
  static final String TRAINING_LABELS_ARCHIVE = "mnist/train-labels-idx1-ubyte.gz";
  static final String TEST_IMAGES_ARCHIVE = "mnist/t10k-images-idx3-ubyte.gz";
  static final String TEST_LABELS_ARCHIVE = "mnist/t10k-labels-idx1-ubyte.gz";
  static final int VALIDATION_SIZE = 0;

  public static MnistDataset createMnistDataset() {
    return MnistDataset.create(0, TRAINING_IMAGES_ARCHIVE, TRAINING_LABELS_ARCHIVE, TEST_IMAGES_ARCHIVE, TEST_LABELS_ARCHIVE);
  }
}
