package ab.mnist;

public interface NnModel {
  public void train(MnistDataset dataset, int batchSize, int maxSize);
  public void test(MnistDataset dataset, int batchSize);
}
