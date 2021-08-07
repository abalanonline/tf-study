package ab;

import com.google.api.gax.longrunning.OperationTimedPollAlgorithm;
import com.google.api.gax.retrying.RetrySettings;
import com.google.cloud.speech.v1.LongRunningRecognizeResponse;
import com.google.cloud.speech.v1.RecognitionAudio;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechRecognitionAlternative;
import com.google.cloud.speech.v1.SpeechRecognitionResult;
import com.google.cloud.speech.v1.SpeechSettings;
import com.google.cloud.speech.v1.WordInfo;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import java.util.stream.StreamSupport;

public class Stt {

  private static final Logger log = Logger.getLogger(Stt.class.getName());

  public static String gsSave(String resourceName) {
    // GOOGLE_APPLICATION_CREDENTIALS
    Storage storage = StorageOptions.getDefaultInstance().getService();
    Bucket bucket = StreamSupport.stream(storage.list().iterateAll().spliterator(), false).findAny().orElseThrow();
    Blob blob = bucket.create(resourceName, Stt.class.getResourceAsStream("/" + resourceName));
    return blob.getBlobId().toGsUtilUri();
  }

  public static List<SpeechRecognitionResult> listStt(String gsUri) {
    SpeechSettings.Builder settings = SpeechSettings.newBuilder();
    settings.longRunningRecognizeOperationSettings().setPollingAlgorithm(pollOfHours(2));
    RecognitionAudio audio = RecognitionAudio.newBuilder().setUri(gsUri).build();
    RecognitionConfig config = RecognitionConfig.newBuilder().setLanguageCode("en-US").setEnableWordTimeOffsets(true) // timecode
        //.setEncoding(RecognitionConfig.AudioEncoding.LINEAR16).setSampleRateHertz(16000)
        .build();
    try (SpeechClient speechClient = SpeechClient.create(settings.build())) {
      LongRunningRecognizeResponse response = speechClient.longRunningRecognizeAsync(config, audio).get();
      // RecognizeResponse response = speechClient.recognize(config, audio);
      return response.getResultsList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException | ExecutionException e) {
      throw new IllegalStateException(e);
    }
  }

  public static OperationTimedPollAlgorithm pollOfHours(int hours) {
    // https://github.com/googleapis/java-speech/issues/207
    return OperationTimedPollAlgorithm.create(RetrySettings.newBuilder()
        .setInitialRetryDelay(org.threeten.bp.Duration.ofSeconds(5))
        .setRetryDelayMultiplier(1.5)
        .setMaxRetryDelay(org.threeten.bp.Duration.ofSeconds(45))
        .setTotalTimeout(org.threeten.bp.Duration.ofHours(hours))
        .build());
  }

  public static String toString(com.google.protobuf.Duration duration) {
    return String.format("%d.%03d", duration.getSeconds(), duration.getNanos() / 1_000_000);
  }

  public Stt() {
    String gsUri = gsSave("talk.wav"); // gs://cloud-samples-data/speech/brooklyn_bridge.raw
    log.info(gsUri);
    List<SpeechRecognitionResult> results = listStt(gsUri);
    try (Writer writer = new BufferedWriter(
        new OutputStreamWriter(new FileOutputStream("stt.txt"), StandardCharsets.UTF_8))) {
      for (SpeechRecognitionResult result : results) {
        SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
        log.info(alternative.getTranscript());
        for (WordInfo word : alternative.getWordsList()) {
          writer.write(toString(word.getStartTime()) + " " + toString(word.getEndTime()) + " " + word.getWord() + '\n');
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

  }

  public static void main(String[] args) {
    new Stt();
  }

}
