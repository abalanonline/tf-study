package ab;

import com.google.api.gax.longrunning.OperationTimedPollAlgorithm;
import com.google.api.gax.retrying.RetrySettings;
import com.google.cloud.speech.v1.LongRunningRecognizeResponse;
import com.google.cloud.speech.v1.RecognitionAudio;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.SpeakerDiarizationConfig;
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
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Stt implements Runnable {

  private static final Logger log = Logger.getLogger(Stt.class.getName());

  public static String gsSave(String fileName) {
    // GOOGLE_APPLICATION_CREDENTIALS
    Storage storage = StorageOptions.getDefaultInstance().getService();
    Bucket bucket = StreamSupport.stream(storage.list().iterateAll().spliterator(), false).findAny().orElseThrow();
    try (InputStream inputStream = Files.newInputStream(Paths.get(fileName))) {
      Blob blob = bucket.create(fileName, inputStream);
      return blob.getBlobId().toGsUtilUri();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static List<SpeechRecognitionResult> listStt(String gsUri, String languageCode) {
    // gs://cloud-samples-data/speech/brooklyn_bridge.raw
    SpeechSettings.Builder settings = SpeechSettings.newBuilder();
    settings.longRunningRecognizeOperationSettings().setPollingAlgorithm(pollOfHours(2));
    RecognitionAudio audio = RecognitionAudio.newBuilder().setUri(gsUri).build();
    RecognitionConfig config = RecognitionConfig.newBuilder().setLanguageCode(languageCode).setEnableWordTimeOffsets(true) // timecode
        .setDiarizationConfig(SpeakerDiarizationConfig.newBuilder()
            .setEnableSpeakerDiarization(true).setMinSpeakerCount(1).setMaxSpeakerCount(8).build())
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

  public static Set<String> listFlacs() {
    try (Stream<Path> stream = Files.list(Paths.get("."))) {
      return stream.map(Path::getFileName).map(Path::toString)
          .filter(name -> name.endsWith(".flac")).collect(Collectors.toSet());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // fr-CA_live_news_test_July.flac
  public static final Pattern FILE_NAME_PATTERN = Pattern.compile("([a-z]{2}-[A-Z]{2,3})_.*");

  public static Optional<String> localePrefix(String name) {
    Matcher m = FILE_NAME_PATTERN.matcher(name);
    if (!m.matches()) return Optional.empty();
    String code = m.group(1);
    try { // validate language code
      Locale locale = Locale.forLanguageTag(code);
      locale.getISO3Language();
      locale.getISO3Country();
    } catch (MissingResourceException e) {
      return Optional.empty();
    }
    return Optional.of(code);
  }

  public static void writeSttFile(List<SpeechRecognitionResult> results, String fileName) {
    try (Writer writer = new BufferedWriter(
        new OutputStreamWriter(new FileOutputStream(fileName), StandardCharsets.UTF_8))) {
      for (SpeechRecognitionResult result : results) {
        SpeechRecognitionAlternative alternative = result.getAlternatives(0);
        log.info(alternative.getTranscript());
        for (WordInfo word : alternative.getWordsList()) {
          writer.write(word.getSpeakerTag() + " "
              + toString(word.getStartTime()) + " " + toString(word.getEndTime()) + " " + word.getWord() + '\n');
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void run() {
    for (String fileName : listFlacs()) {
      String languageCode = localePrefix(fileName).orElseThrow();
      String gsUri = gsSave(fileName);
      log.info(fileName + " -> " + gsUri);
      List<SpeechRecognitionResult> results = listStt(gsUri, languageCode);
      writeSttFile(results, fileName + ".txt");
    }
  }

  public static void main(String[] args) {
    new Stt().run();
  }

}
