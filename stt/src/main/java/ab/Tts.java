package ab;

import com.google.cloud.texttospeech.v1.AudioConfig;
import com.google.cloud.texttospeech.v1.AudioEncoding;
import com.google.cloud.texttospeech.v1.SynthesisInput;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechResponse;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class Tts implements Runnable { // FIXME: 2021-08-08 this class is messy even for a study project

  private static final Logger log = Logger.getLogger(Tts.class.getName());

  private static TextToSpeechClient textToSpeechClient;
  public static TextToSpeechClient getClient() {
    if (textToSpeechClient == null) {
      try {
        textToSpeechClient = TextToSpeechClient.create();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return textToSpeechClient;
  }

  @Override
  public void run() {
    //List<com.google.cloud.texttospeech.v1.Voice> voicesList =
    //    getClient().listVoices(com.google.cloud.texttospeech.v1.ListVoicesRequest.newBuilder().build()).getVoicesList();

    for (String fileName : Stt.listFilesExt(".srt")) {
      String languageCode = Stt.localePrefix(fileName).orElseThrow();
      final List<String> srtStrings;
      try {
        srtStrings = Files.readAllLines(Paths.get(fileName));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      List<String> transcripts = new ArrayList<>();
      int srtLineNum = 0;
      for (String s : srtStrings) {
        srtLineNum++;
        if (s.isEmpty()) srtLineNum = 0;
        if (srtLineNum < 3) continue;
        transcripts.add(s);
      }
      transcripts.size();
      int i = 0;
      for (String s : transcripts) {
        VoiceSelectionParams voiceSelectionParams = VoiceSelectionParams.newBuilder()
            .setLanguageCode(languageCode)
            .setName(languageCode + "-Standard-" + (char)('A' + i))//(i % 2 == 0 ? 'A' : 'B')) // en-US-Wavenet-A
            .setName(languageCode + "-Standard-" + (i % 2 == 0 ? 'E' : 'B')) // en-US-Wavenet-A
            .build();
        SynthesizeSpeechResponse response;
          response = getClient().synthesizeSpeech(
              SynthesisInput.newBuilder().setSsml("<speak>" + s + (i % 4 == 2 ? '?' : '.')
                  + "<break strength=\"strong\"/></speak>").build(), voiceSelectionParams,
              AudioConfig.newBuilder().setAudioEncoding(AudioEncoding.MP3).build());
        byte[] byteResponse = response.getAudioContent().toByteArray();
        try {
          Files.write(Paths.get(fileName + ".mp3"), byteResponse, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
        i++;
      }
      //try (FileOutputStream stream = new FileOutputStream(Paths.get(fileName + ".mp3"))) {
      //  stream.write(bytes);
      //}
    }
  }

  public static void main(String[] args) {
    new Tts().run();
  }

}
